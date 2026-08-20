package com.kaizen.auto.core.vision

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * OCR local via ML Kit (modelo embarcado, funciona offline).
 *
 * Serve a dois propósitos:
 *  1. API pública `findText("Jogar")` para o usuário escrever scripts sem
 *     precisar recortar imagem de cada botão.
 *  2. Plano C do self-healing: se a imagem do botão "Jogar" não casa mais, o
 *     bot tenta achar a PALAVRA "Jogar" na tela.
 */
object OcrEngine {

    private const val TAG = "OcrEngine"

    private val recognizer: TextRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    data class TextBlock(
        val text: String,
        val region: ScreenRegion,
        val confidence: Double,
    )

    /** Extrai todos os blocos de texto visíveis. Chamada bloqueante. */
    fun readAll(screen: Bitmap, timeoutMs: Long = 5_000L): List<TextBlock> {
        val out = ArrayList<TextBlock>()
        val latch = CountDownLatch(1)

        try {
            val input = InputImage.fromBitmap(screen, 0)
            recognizer.process(input)
                .addOnSuccessListener { result ->
                    result.textBlocks.forEach { block ->
                        block.lines.forEach { line ->
                            val bb = line.boundingBox
                            if (bb != null) {
                                out += TextBlock(
                                    text = line.text,
                                    region = ScreenRegion(bb.left, bb.top, bb.width(), bb.height()),
                                    // ML Kit nem sempre expõe confidence; usamos 1.0 como neutro.
                                    confidence = line.confidence?.toDouble() ?: 1.0,
                                )
                            }
                        }
                    }
                    latch.countDown()
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "OCR falhou: ${e.message}")
                    latch.countDown()
                }
            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (t: Throwable) {
            Log.e(TAG, "Erro no OCR: ${t.message}")
        }
        return out
    }

    /**
     * Procura um texto na tela.
     * @param exact false faz busca por "contém", ignorando caixa e acentos.
     */
    fun findText(
        screen: Bitmap,
        query: String,
        exact: Boolean = false,
        region: ScreenRegion? = null,
    ): MatchResult? {
        val target = query.normalizeForSearch()
        val candidates = readAll(screen)
            .asSequence()
            .filter { region == null || region.overlaps(it.region) }
            .mapNotNull { block ->
                val normalized = block.text.normalizeForSearch()
                val score = when {
                    exact && normalized == target -> 1.0
                    !exact && normalized == target -> 1.0
                    !exact && normalized.contains(target) -> {
                        // Quanto menor o excedente, melhor o casamento.
                        0.9 * target.length / normalized.length.coerceAtLeast(1)
                    }
                    else -> 0.0
                }
                if (score > 0.0) block to score else null
            }
            .maxByOrNull { it.second }
            ?: return null

        val (block, score) = candidates
        return MatchResult(
            region = block.region,
            score = score.coerceIn(0.0, 1.0),
            strategy = MatchStrategy.OCR,
            targetX = block.region.centerX,
            targetY = block.region.centerY,
        )
    }

    /** Todas as ocorrências de um texto. */
    fun findAllText(screen: Bitmap, query: String, region: ScreenRegion? = null): List<MatchResult> {
        val target = query.normalizeForSearch()
        return readAll(screen)
            .filter { region == null || region.overlaps(it.region) }
            .filter { it.text.normalizeForSearch().contains(target) }
            .map {
                MatchResult(
                    region = it.region,
                    score = 0.9,
                    strategy = MatchStrategy.OCR,
                    targetX = it.region.centerX,
                    targetY = it.region.centerY,
                )
            }
    }

    /** Remove acentos, normaliza espaços e caixa — "Jogá-lo" vira "jogalo". */
    private fun String.normalizeForSearch(): String =
        java.text.Normalizer.normalize(this, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "")

    private fun ScreenRegion.overlaps(other: ScreenRegion): Boolean =
        x < other.right && right > other.x && y < other.bottom && bottom > other.y
}
