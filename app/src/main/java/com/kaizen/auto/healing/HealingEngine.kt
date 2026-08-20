package com.kaizen.auto.healing

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.kaizen.auto.core.vision.MatchResult
import com.kaizen.auto.core.vision.MatchStrategy
import com.kaizen.auto.core.vision.OcrEngine
import com.kaizen.auto.core.vision.Pattern
import com.kaizen.auto.core.vision.ScreenRegion
import com.kaizen.auto.core.vision.TemplateMatcher
import com.kaizen.auto.data.db.HealingEvent
import com.kaizen.auto.data.db.KaizenDatabase
import com.kaizen.auto.data.db.PatternMemory
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

sealed class HealingOutcome {
    data class Recovered(val match: MatchResult, val tactic: String) : HealingOutcome()
    data class Failed(val triedTactics: List<String>) : HealingOutcome()
}

/**
 * O SELF-HEALING.
 *
 * Quando uma busca falha, em vez de simplesmente quebrar o script, tentamos uma
 * cascata de táticas — da mais barata para a mais cara:
 *
 *   1. RELAX_THRESHOLD  — o limiar está apertado demais? Tenta com folga.
 *   2. LEARNED_VARIANT  — usa o recorte real da tela salvo num acerto anterior.
 *   3. WIDE_MULTISCALE  — varredura de escala agressiva (0.5x a 2.0x).
 *   4. ORB_FEATURES     — casamento por features: sobrevive a redesign da UI.
 *   5. OCR_TEXT         — procura o TEXTO derivado do nome do arquivo.
 *   6. A11Y_TREE        — pergunta à árvore de acessibilidade onde está o botão.
 *
 * Cada tática que dá certo é registrada: da próxima vez, ela é promovida na
 * ordem e o limiar do padrão é permanentemente ajustado. É assim que o bot
 * "aprende com os erros".
 */
class HealingEngine(
    private val context: Context,
    private val database: KaizenDatabase,
) {

    /** Liga/desliga o self-healing sem mexer no script. */
    @Volatile var enabled: Boolean = true

    /** Se true, o bot salva o recorte da tela quando cura com sucesso. */
    @Volatile var learnVariants: Boolean = true

    /** Nome do script em execução — só para rotular os eventos no log. */
    @Volatile var currentScriptName: String = "desconhecido"

    /** Callback para o runtime exibir mensagens ao usuário. */
    var onLog: ((String) -> Unit)? = null

    private val memoryDao = database.patternMemoryDao()
    private val eventDao = database.healingEventDao()

    private val variantsDir: File by lazy {
        File(context.filesDir, "learned_variants").apply { mkdirs() }
    }
    private val failureShotsDir: File by lazy {
        File(context.filesDir, "failure_shots").apply { mkdirs() }
    }

    // ------------------------------------------------------------------
    // Consultas usadas ANTES da busca (o lado "aprendido")
    // ------------------------------------------------------------------

    /**
     * Limiar efetivo: o pedido pelo script mais o ajuste que o bot aprendeu.
     * Nunca descemos abaixo de [MIN_THRESHOLD] para não gerar falso positivo.
     */
    fun effectiveThreshold(patternKey: String, requested: Double): Double {
        if (!enabled) return requested
        val memory = safeMemory(patternKey) ?: return requested
        return (requested + memory.thresholdDelta).coerceIn(MIN_THRESHOLD, 0.99)
    }

    /**
     * Região sugerida para busca dirigida. Se o elemento apareceu 20 vezes no
     * mesmo lugar, buscar só naquela vizinhança é ~10x mais rápido.
     */
    fun suggestedRegion(patternKey: String): ScreenRegion? {
        if (!enabled) return null
        val memory = safeMemory(patternKey) ?: return null
        if (memory.successCount < MIN_HITS_FOR_REGION) return null
        if (memory.lastX < 0 || memory.lastW <= 0) return null
        // Margem generosa: o elemento pode deslizar um pouco entre telas.
        val margin = maxOf(memory.lastW, memory.lastH)
        return ScreenRegion(
            x = (memory.lastX - margin).coerceAtLeast(0),
            y = (memory.lastY - margin).coerceAtLeast(0),
            w = memory.lastW + margin * 2,
            h = memory.lastH + margin * 2,
        )
    }

    // ------------------------------------------------------------------
    // A cascata de cura
    // ------------------------------------------------------------------

    fun attemptHeal(
        patternKey: String,
        pattern: Pattern,
        template: Bitmap,
        screen: Bitmap,
    ): HealingOutcome {
        if (!enabled) return HealingOutcome.Failed(emptyList())

        val tried = ArrayList<String>()
        val baseThreshold = effectiveThreshold(patternKey, pattern.similarity)
        val memory = safeMemory(patternKey)

        // A tática que já funcionou antes vai para o começo da fila.
        val order = tacticOrder(memory?.preferredStrategy)

        for (tactic in order) {
            tried += tactic.name
            val result: MatchResult? = when (tactic) {
                Tactic.RELAX_THRESHOLD -> relaxThreshold(pattern, template, screen, baseThreshold)
                Tactic.LEARNED_VARIANT -> useLearnedVariant(memory, pattern, screen, baseThreshold)
                Tactic.WIDE_MULTISCALE -> wideMultiScale(pattern, template, screen, baseThreshold)
                Tactic.ORB_FEATURES -> orbFeatures(pattern, template, screen)
                Tactic.OCR_TEXT -> ocrFallback(patternKey, screen, pattern.searchRegion)
                Tactic.A11Y_TREE -> accessibilityFallback(patternKey)
            }

            if (result != null) {
                onHealSuccess(patternKey, tactic, result, baseThreshold)
                log("🔧 Curado via ${tactic.label} (score ${"%.2f".format(result.score)})")
                return HealingOutcome.Recovered(result, tactic.name)
            }
        }

        recordEvent(patternKey, "GIVE_UP", false, baseThreshold, 0.0,
            "Todas as táticas falharam: ${tried.joinToString()}", saveShot(screen, patternKey))
        log("❌ Não consegui encontrar '$patternKey' nem com self-healing.")
        return HealingOutcome.Failed(tried)
    }

    // ---- Táticas individuais -----------------------------------------

    /** 1. Afrouxa o limiar em passos, aceitando o primeiro match razoável. */
    private fun relaxThreshold(
        pattern: Pattern,
        template: Bitmap,
        screen: Bitmap,
        base: Double,
    ): MatchResult? {
        for (step in RELAX_STEPS) {
            val threshold = (base - step).coerceAtLeast(MIN_THRESHOLD)
            val hit = TemplateMatcher.findTemplate(
                screen = screen,
                template = template,
                threshold = threshold,
                region = pattern.searchRegion,
                grayscale = pattern.grayscale,
            )
            if (hit != null) return hit.copy(score = hit.score)
        }
        return null
    }

    /** 2. Usa o recorte real que o bot salvou de um acerto anterior. */
    private fun useLearnedVariant(
        memory: PatternMemory?,
        pattern: Pattern,
        screen: Bitmap,
        base: Double,
    ): MatchResult? {
        val path = memory?.learnedVariantPath ?: return null
        val file = File(path)
        if (!file.exists()) return null
        val variant = android.graphics.BitmapFactory.decodeFile(file.absolutePath) ?: return null
        return TemplateMatcher.findTemplate(
            screen = screen,
            template = variant,
            threshold = (base - 0.05).coerceAtLeast(MIN_THRESHOLD),
            region = pattern.searchRegion,
            grayscale = pattern.grayscale,
        )
    }

    /** 3. Varredura de escala agressiva — resolve mudança de densidade/resolução. */
    private fun wideMultiScale(
        pattern: Pattern,
        template: Bitmap,
        screen: Bitmap,
        base: Double,
    ): MatchResult? = TemplateMatcher.findTemplate(
        screen = screen,
        template = template,
        threshold = (base - 0.08).coerceAtLeast(MIN_THRESHOLD),
        region = null, // escala diferente pode deslocar o elemento; olha tudo
        grayscale = pattern.grayscale,
        scales = WIDE_SCALES,
    )

    /** 4. Features ORB — imune a re-tema e a pequenas mudanças de desenho. */
    private fun orbFeatures(pattern: Pattern, template: Bitmap, screen: Bitmap): MatchResult? =
        TemplateMatcher.findByFeatures(
            screen = screen,
            template = template,
            minMatches = 8,
            region = pattern.searchRegion,
        )

    /** 5. OCR: transforma "botao_jogar.png" em busca pelo texto "jogar". */
    private fun ocrFallback(patternKey: String, screen: Bitmap, region: ScreenRegion?): MatchResult? {
        val guess = patternKey.toTextGuess() ?: return null
        return OcrEngine.findText(screen, guess, exact = false, region = region)
    }

    /** 6. Árvore de acessibilidade: a fonte mais confiável quando disponível. */
    private fun accessibilityFallback(patternKey: String): MatchResult? {
        val element = PassiveObserver.findElementByPatternName(patternKey) ?: return null
        return MatchResult(
            region = ScreenRegion(
                element.left,
                element.top,
                element.right - element.left,
                element.bottom - element.top,
            ),
            score = 0.95,
            strategy = MatchStrategy.A11Y_TREE,
            targetX = element.centerX,
            targetY = element.centerY,
        )
    }

    // ------------------------------------------------------------------
    // Aprendizado (escrita na memória)
    // ------------------------------------------------------------------

    /** Registra um acerto — atualiza região, score médio e escala típica. */
    fun recordSuccess(patternKey: String, match: MatchResult, screen: Bitmap? = null) {
        try {
            val old = safeMemory(patternKey)
            val hits = (old?.successCount ?: 0) + 1
            val avg = if (old == null) match.score
            else (old.avgScore * old.successCount + match.score) / hits
            val avgScale = if (old == null) match.scale
            else (old.avgScale * old.successCount + match.scale) / hits

            memoryDao.upsert(
                (old ?: PatternMemory(patternKey = patternKey)).copy(
                    patternKey = patternKey,
                    successCount = hits,
                    lastX = match.region.x,
                    lastY = match.region.y,
                    lastW = match.region.w,
                    lastH = match.region.h,
                    avgScore = avg,
                    minSuccessScore = minOf(old?.minSuccessScore ?: 1.0, match.score),
                    avgScale = avgScale,
                    preferredStrategy = match.strategy.name,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        } catch (t: Throwable) {
            Log.w(TAG, "recordSuccess falhou: ${t.message}")
        }
    }

    fun recordFailure(patternKey: String, screen: Bitmap? = null) {
        try {
            val old = safeMemory(patternKey) ?: PatternMemory(patternKey = patternKey)
            memoryDao.upsert(
                old.copy(
                    patternKey = patternKey,
                    failureCount = old.failureCount + 1,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        } catch (t: Throwable) {
            Log.w(TAG, "recordFailure falhou: ${t.message}")
        }
    }

    /**
     * Consolida o que a cura ensinou:
     *  - ajusta permanentemente o limiar (com teto, para não virar falso positivo)
     *  - salva o recorte real da tela como variante aprendida
     *  - promove a tática vencedora
     */
    private fun onHealSuccess(
        patternKey: String,
        tactic: Tactic,
        match: MatchResult,
        thresholdBefore: Double,
    ) {
        val old = safeMemory(patternKey) ?: PatternMemory(patternKey = patternKey)

        // Se casou com score bem abaixo do limiar, o template está desatualizado:
        // baixamos o limiar só o suficiente, com margem de segurança.
        val newDelta = if (match.score < thresholdBefore) {
            val needed = match.score - thresholdBefore - SAFETY_MARGIN
            (old.thresholdDelta + needed).coerceIn(MAX_NEGATIVE_DELTA, 0.0)
        } else {
            old.thresholdDelta
        }

        memoryDao.upsert(
            old.copy(
                patternKey = patternKey,
                thresholdDelta = newDelta,
                preferredStrategy = tactic.name,
                lastX = match.region.x,
                lastY = match.region.y,
                lastW = match.region.w,
                lastH = match.region.h,
                updatedAt = System.currentTimeMillis(),
            ),
        )

        recordEvent(
            patternKey = patternKey,
            tactic = tactic.name,
            succeeded = true,
            scoreBefore = thresholdBefore,
            scoreAfter = match.score,
            details = "Limiar ajustado para ${"%.2f".format(thresholdBefore + newDelta)}; " +
                "estratégia ${tactic.label}",
        )
    }

    /** Salva o recorte da tela onde o elemento foi achado, como nova referência. */
    fun learnVariantFrom(patternKey: String, screen: Bitmap, region: ScreenRegion) {
        if (!learnVariants) return
        try {
            val x = region.x.coerceIn(0, screen.width - 1)
            val y = region.y.coerceIn(0, screen.height - 1)
            val w = region.w.coerceIn(1, screen.width - x)
            val h = region.h.coerceIn(1, screen.height - y)
            val crop = Bitmap.createBitmap(screen, x, y, w, h)

            val safeName = patternKey.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val out = File(variantsDir, "$safeName.png")
            FileOutputStream(out).use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
            crop.recycle()

            val old = safeMemory(patternKey) ?: PatternMemory(patternKey = patternKey)
            memoryDao.upsert(old.copy(patternKey = patternKey, learnedVariantPath = out.absolutePath))
        } catch (t: Throwable) {
            Log.w(TAG, "learnVariantFrom falhou: ${t.message}")
        }
    }

    // ------------------------------------------------------------------

    private fun recordEvent(
        patternKey: String,
        tactic: String,
        succeeded: Boolean,
        scoreBefore: Double,
        scoreAfter: Double,
        details: String,
        screenshotPath: String? = null,
    ) {
        runCatching {
            eventDao.insert(
                HealingEvent(
                    patternKey = patternKey,
                    scriptName = currentScriptName,
                    tactic = tactic,
                    succeeded = succeeded,
                    scoreBefore = scoreBefore,
                    scoreAfter = scoreAfter,
                    details = details,
                    screenshotPath = screenshotPath,
                ),
            )
        }
    }

    private fun saveShot(screen: Bitmap, patternKey: String): String? = try {
        val safeName = patternKey.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val out = File(failureShotsDir, "${safeName}_${System.currentTimeMillis()}.jpg")
        FileOutputStream(out).use { screen.compress(Bitmap.CompressFormat.JPEG, 70, it) }
        // Mantém no máximo 30 capturas de falha.
        failureShotsDir.listFiles()?.sortedByDescending { it.lastModified() }
            ?.drop(30)?.forEach { it.delete() }
        out.absolutePath
    } catch (t: Throwable) {
        null
    }

    private fun safeMemory(key: String): PatternMemory? =
        runCatching { memoryDao.findSync(key) }.getOrNull()

    private fun tacticOrder(preferred: String?): List<Tactic> {
        val all = Tactic.entries.toMutableList()
        val first = all.firstOrNull { it.name == preferred || it.matchesStrategy(preferred) }
        if (first != null) {
            all.remove(first)
            all.add(0, first)
        }
        return all
    }

    private fun log(message: String) {
        Log.i(TAG, message)
        onLog?.invoke(message)
    }

    private fun String.toTextGuess(): String? {
        val words = substringAfterLast('/')
            .substringBeforeLast('.')
            .split('_', '-')
            .filter { it.length > 2 }
            .filterNot { it.lowercase() in setOf("btn", "button", "botao", "icon", "img") }
        return if (words.isEmpty()) null else words.joinToString(" ")
    }

    private enum class Tactic(val label: String) {
        RELAX_THRESHOLD("limiar relaxado"),
        LEARNED_VARIANT("variante aprendida"),
        WIDE_MULTISCALE("multi-escala ampla"),
        ORB_FEATURES("features ORB"),
        OCR_TEXT("OCR de texto"),
        A11Y_TREE("árvore de acessibilidade"),
        ;

        fun matchesStrategy(strategy: String?): Boolean = when (strategy) {
            "ORB" -> this == ORB_FEATURES
            "OCR" -> this == OCR_TEXT
            "A11Y_TREE" -> this == A11Y_TREE
            else -> false
        }
    }

    private companion object {
        const val TAG = "HealingEngine"
        const val MIN_THRESHOLD = 0.55
        const val SAFETY_MARGIN = 0.03
        const val MAX_NEGATIVE_DELTA = -0.25
        const val MIN_HITS_FOR_REGION = 4
        val RELAX_STEPS = doubleArrayOf(0.05, 0.10, 0.15, 0.20)
        val WIDE_SCALES = doubleArrayOf(
            1.0, 0.9, 1.1, 0.8, 1.2, 0.7, 1.35, 0.6, 1.5, 0.5, 1.75, 2.0,
        )
    }
}
