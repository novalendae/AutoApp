package com.kaizen.auto.core.vision

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import com.kaizen.auto.core.capture.CaptureManager
import com.kaizen.auto.healing.HealingEngine
import com.kaizen.auto.healing.HealingOutcome
import java.io.File

/**
 * Fachada de visão usada pelo runtime Lua.
 *
 * Fluxo de uma busca:
 *   1. captura o frame atual (com cache curto)
 *   2. tenta template matching multi-escala
 *   3. se falhar E o self-healing estiver ligado → delega ao [HealingEngine]
 *   4. registra o resultado para alimentar o aprendizado
 *
 * Todo o resto do app fala com a visão através daqui.
 */
class VisionEngine(
    private val capture: CaptureManager,
    private val healing: HealingEngine,
) {

    /** Cache de bitmaps de template — decodificar PNG a cada loop é caríssimo. */
    private val templateCache = object : LruCache<String, Bitmap>(24) {
        override fun sizeOf(key: String, value: Bitmap) = 1
    }

    /**
     * Dimensão de referência dos scripts (como o setScriptDimension do AnkuLua).
     * Se o script foi feito num aparelho 1080p e roda num 1440p, escalamos os
     * templates automaticamente em vez de falhar.
     */
    var scriptDimension: Int = 0
    var compareDimension: Int = 0

    /** Diretório base onde as imagens do script vivem. */
    var imageBaseDir: File? = null

    fun screenSize(): Pair<Int, Int> {
        val bmp = capture.grab() ?: return 0 to 0
        return bmp.width to bmp.height
    }

    fun currentFrame(fresh: Boolean = false): Bitmap? = capture.grab(fresh)

    fun invalidateFrame() = capture.invalidate()

    // ------------------------------------------------------------------
    // Busca principal
    // ------------------------------------------------------------------

    /**
     * Procura o [pattern] na tela UMA vez.
     * @param allowHealing quando false, pula o self-healing (usado internamente
     *        pelo próprio healing para evitar recursão).
     */
    fun find(pattern: Pattern, allowHealing: Boolean = true): MatchResult? {
        val screen = capture.grab() ?: run {
            Log.w(TAG, "Sem frame disponível — a captura de tela está ativa?")
            return null
        }

        // Busca textual não usa template.
        if (pattern.isText) {
            val hit = OcrEngine.findText(screen, pattern.source, region = pattern.searchRegion)
            return hit?.applyOffset(pattern)
        }

        val template = loadTemplate(pattern.source) ?: run {
            Log.w(TAG, "Imagem não encontrada: ${pattern.source}")
            return null
        }

        val effectiveTemplate = rescaleForScreen(template, screen)
        val threshold = healing.effectiveThreshold(pattern.source, pattern.similarity)

        val direct = TemplateMatcher.findTemplate(
            screen = screen,
            template = effectiveTemplate,
            threshold = threshold,
            region = pattern.searchRegion ?: healing.suggestedRegion(pattern.source),
            grayscale = pattern.grayscale,
        )

        if (direct != null) {
            healing.recordSuccess(pattern.source, direct, screen)
            return direct.applyOffset(pattern)
        }

        // Se restringimos a região e não achamos, tenta a tela toda antes de curar.
        if (pattern.searchRegion == null && healing.suggestedRegion(pattern.source) != null) {
            val fullScan = TemplateMatcher.findTemplate(
                screen = screen,
                template = effectiveTemplate,
                threshold = threshold,
                region = null,
                grayscale = pattern.grayscale,
            )
            if (fullScan != null) {
                healing.recordSuccess(pattern.source, fullScan, screen)
                return fullScan.applyOffset(pattern)
            }
        }

        if (!allowHealing) return null

        // ---- Self-healing entra em cena ----
        val outcome = healing.attemptHeal(
            patternKey = pattern.source,
            pattern = pattern,
            template = effectiveTemplate,
            screen = screen,
        )
        return when (outcome) {
            is HealingOutcome.Recovered -> {
                healing.recordSuccess(pattern.source, outcome.match, screen)
                outcome.match.applyOffset(pattern)
            }
            is HealingOutcome.Failed -> {
                healing.recordFailure(pattern.source, screen)
                null
            }
        }
    }

    /** Todas as ocorrências do padrão. */
    fun findAll(pattern: Pattern): List<MatchResult> {
        val screen = capture.grab() ?: return emptyList()
        if (pattern.isText) {
            return OcrEngine.findAllText(screen, pattern.source, pattern.searchRegion)
                .map { it.applyOffset(pattern) }
        }
        val template = loadTemplate(pattern.source) ?: return emptyList()
        val effective = rescaleForScreen(template, screen)
        return TemplateMatcher.findAllTemplates(
            screen = screen,
            template = effective,
            threshold = healing.effectiveThreshold(pattern.source, pattern.similarity),
            region = pattern.searchRegion,
            grayscale = pattern.grayscale,
        ).map { it.applyOffset(pattern) }
    }

    /**
     * Espera o padrão aparecer.
     * @return o match, ou null se estourou o tempo.
     */
    fun waitFor(pattern: Pattern, timeoutSeconds: Double, shouldStop: () -> Boolean): MatchResult? {
        val deadline = System.currentTimeMillis() + (timeoutSeconds * 1000).toLong()
        var attempt = 0
        do {
            if (shouldStop()) return null
            // Primeira tentativa sem healing (rápida); healing só quando insistir.
            val match = find(pattern, allowHealing = attempt > 0)
            if (match != null) return match
            attempt++
            if (System.currentTimeMillis() >= deadline) break
            Thread.sleep(POLL_INTERVAL_MS)
            capture.invalidate()
        } while (System.currentTimeMillis() < deadline)
        return null
    }

    /** Espera o padrão SUMIR da tela. */
    fun waitVanish(pattern: Pattern, timeoutSeconds: Double, shouldStop: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + (timeoutSeconds * 1000).toLong()
        while (System.currentTimeMillis() < deadline) {
            if (shouldStop()) return false
            capture.invalidate()
            if (find(pattern, allowHealing = false) == null) return true
            Thread.sleep(POLL_INTERVAL_MS)
        }
        return false
    }

    // ------------------------------------------------------------------
    // Templates
    // ------------------------------------------------------------------

    private fun loadTemplate(name: String): Bitmap? {
        templateCache.get(name)?.let { if (!it.isRecycled) return it }

        val file = resolveImageFile(name) ?: return null
        val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val bmp = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return null
        templateCache.put(name, bmp)
        return bmp
    }

    private fun resolveImageFile(name: String): File? {
        val direct = File(name)
        if (direct.isAbsolute && direct.exists()) return direct

        val base = imageBaseDir ?: return null
        val candidates = listOf(
            File(base, name),
            File(base, "$name.png"),
            File(base, "images/$name"),
            File(base, "images/$name.png"),
        )
        return candidates.firstOrNull { it.exists() }
    }

    /**
     * Ajusta o template quando o script foi criado numa resolução diferente da
     * tela atual — é a causa nº 1 de scripts do AnkuLua "pararem de funcionar".
     */
    private fun rescaleForScreen(template: Bitmap, screen: Bitmap): Bitmap {
        val reference = scriptDimension
        if (reference <= 0) return template

        val screenLong = maxOf(screen.width, screen.height)
        if (screenLong == reference) return template

        val factor = screenLong.toDouble() / reference.toDouble()
        if (kotlin.math.abs(factor - 1.0) < 0.02) return template

        val nw = (template.width * factor).toInt().coerceAtLeast(4)
        val nh = (template.height * factor).toInt().coerceAtLeast(4)
        return try {
            Bitmap.createScaledBitmap(template, nw, nh, true)
        } catch (t: Throwable) {
            template
        }
    }

    fun clearTemplateCache() = templateCache.evictAll()

    private fun MatchResult.applyOffset(p: Pattern) = copy(
        targetX = region.centerX + p.targetOffsetX,
        targetY = region.centerY + p.targetOffsetY,
    )

    private companion object {
        const val TAG = "VisionEngine"
        const val POLL_INTERVAL_MS = 250L
    }
}
