package com.kaizen.auto.healing

import android.os.SystemClock
import android.util.Log
import com.kaizen.auto.data.db.KaizenDatabase
import com.kaizen.auto.data.db.ScreenObservation
import com.kaizen.auto.service.KaizenAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * O APRENDIZADO PASSIVO.
 *
 * Enquanto o usuário usa o celular normalmente (e o serviço de acessibilidade
 * está ligado), este objeto vai catalogando as telas que aparecem: quais
 * botões existem, com que rótulo e em que posição.
 *
 * Isso não custa praticamente nada — reaproveitamos eventos que o Android já
 * está emitindo — e cria uma base de conhecimento valiosíssima: quando um
 * script quebra procurando "botao_jogar.png", o [HealingEngine] pode perguntar
 * "existe algum elemento clicável chamado 'Jogar' nesta tela?" e resolver o
 * problema sem intervenção humana.
 *
 * Privacidade: nada sai do aparelho. Guardamos apenas rótulos e coordenadas,
 * e campos de senha são descartados.
 */
object PassiveObserver {

    private const val TAG = "PassiveObserver"

    /** Intervalo mínimo entre duas coletas, para não pesar na bateria. */
    private const val THROTTLE_MS = 1_200L

    /** Limite de telas memorizadas antes de começar a reciclar. */
    private const val MAX_OBSERVATIONS = 400

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile var enabled: Boolean = true
    @Volatile private var lastCollectAt: Long = 0L
    @Volatile private var lastSignature: String = ""

    private var database: KaizenDatabase? = null

    /** Cache em memória da tela atual — consultado pelo healing em tempo real. */
    @Volatile
    var currentScreen: List<KaizenAccessibilityService.UiElement> = emptyList()
        private set

    @Volatile
    var currentPackage: String = ""
        private set

    fun attach(db: KaizenDatabase) {
        database = db
    }

    /**
     * Chamado pelo AccessibilityService a cada mudança de tela.
     * O [collector] é lazy: só executamos a varredura da árvore se passarmos
     * pelo throttle, porque percorrer a árvore é a parte cara.
     */
    fun onScreenChanged(
        packageName: String,
        collector: () -> List<KaizenAccessibilityService.UiElement>,
    ) {
        if (!enabled) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastCollectAt < THROTTLE_MS) return
        lastCollectAt = now

        val elements = try {
            collector()
        } catch (t: Throwable) {
            Log.w(TAG, "Falha ao coletar árvore: ${t.message}")
            return
        }
        if (elements.isEmpty()) return

        val filtered = elements.filterNot { it.isSensitive() }
        currentScreen = filtered
        currentPackage = packageName

        val signature = filtered.signature()
        if (signature == lastSignature) return // mesma tela, nada novo a aprender
        lastSignature = signature

        scope.launch { persist(signature, packageName, filtered) }
    }

    private fun persist(
        signature: String,
        packageName: String,
        elements: List<KaizenAccessibilityService.UiElement>,
    ) {
        val db = database ?: return
        try {
            val dao = db.screenObservationDao()
            val existing = dao.findSync(signature)
            if (existing != null) {
                dao.update(
                    existing.copy(
                        seenCount = existing.seenCount + 1,
                        lastSeenAt = System.currentTimeMillis(),
                        elementsJson = elements.toJson(),
                    ),
                )
            } else {
                if (dao.count() >= MAX_OBSERVATIONS) return
                dao.upsert(
                    ScreenObservation(
                        signature = signature,
                        packageName = packageName,
                        elementsJson = elements.toJson(),
                    ),
                )
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Falha ao gravar observação: ${t.message}")
        }
    }

    /**
     * Procura na tela ATUAL um elemento cujo rótulo casa com [query].
     * Usado pelo self-healing como estratégia "A11Y_TREE".
     */
    fun findElementByLabel(query: String): KaizenAccessibilityService.UiElement? {
        val normalized = query.normalized()
        if (normalized.isEmpty()) return null

        val snapshot = currentScreen
        // Preferimos correspondência exata, depois "contém", priorizando clicáveis.
        return snapshot.firstOrNull { it.label.normalized() == normalized && it.clickable }
            ?: snapshot.firstOrNull { it.label.normalized() == normalized }
            ?: snapshot.filter { it.clickable }
                .firstOrNull { it.label.normalized().contains(normalized) }
            ?: snapshot.firstOrNull { it.label.normalized().contains(normalized) }
    }

    /**
     * Dado o nome de um arquivo de imagem ("botao_jogar.png"), tenta adivinhar
     * o rótulo textual correspondente e achá-lo na árvore.
     * "botao_jogar.png" → "botao jogar" → casa com o botão "Jogar".
     */
    fun findElementByPatternName(patternKey: String): KaizenAccessibilityService.UiElement? {
        val words = patternKey
            .substringAfterLast('/')
            .substringBeforeLast('.')
            .split('_', '-', ' ')
            .filter { it.length > 2 }
            .filterNot { it.lowercase() in STOP_WORDS }

        if (words.isEmpty()) return null

        // Tenta a frase inteira, depois cada palavra isolada.
        findElementByLabel(words.joinToString(" "))?.let { return it }
        words.sortedByDescending { it.length }.forEach { word ->
            findElementByLabel(word)?.let { return it }
        }
        return null
    }

    fun clearMemory() {
        scope.launch { runCatching { database?.screenObservationDao()?.clear() } }
        currentScreen = emptyList()
        lastSignature = ""
    }

    // ------------------------------------------------------------------

    private val STOP_WORDS = setOf("btn", "button", "botao", "icon", "icone", "img", "image", "png", "jpg")

    private fun KaizenAccessibilityService.UiElement.isSensitive(): Boolean {
        val lower = "$viewId $className".lowercase()
        return lower.contains("password") || lower.contains("senha") || lower.contains("pin")
    }

    private fun String.normalized(): String =
        java.text.Normalizer.normalize(this, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9 ]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    /** Assinatura estável da tela: conjunto ordenado de rótulos. */
    private fun List<KaizenAccessibilityService.UiElement>.signature(): String {
        val labels = this.map { it.label.normalized() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
            .take(25)
            .joinToString("|")
        val digest = MessageDigest.getInstance("MD5").digest(labels.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun List<KaizenAccessibilityService.UiElement>.toJson(): String {
        val array = JSONArray()
        take(60).forEach { e ->
            array.put(
                JSONObject().apply {
                    put("label", e.label)
                    put("clickable", e.clickable)
                    put("x", e.centerX)
                    put("y", e.centerY)
                    put("l", e.left); put("t", e.top); put("r", e.right); put("b", e.bottom)
                },
            )
        }
        return array.toString()
    }
}
