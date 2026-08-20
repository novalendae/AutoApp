package com.kaizen.auto.core.vision

import kotlin.math.roundToInt

/** Retângulo em coordenadas de tela (pixels reais do dispositivo). */
data class ScreenRegion(
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int,
) {
    val centerX: Int get() = x + w / 2
    val centerY: Int get() = y + h / 2
    val right: Int get() = x + w
    val bottom: Int get() = y + h

    fun contains(px: Int, py: Int) = px in x until right && py in y until bottom

    /** Cresce a região em todas as direções, respeitando os limites da tela. */
    fun inflate(px: Int, maxW: Int, maxH: Int): ScreenRegion {
        val nx = (x - px).coerceAtLeast(0)
        val ny = (y - px).coerceAtLeast(0)
        val nr = (right + px).coerceAtMost(maxW)
        val nb = (bottom + px).coerceAtMost(maxH)
        return ScreenRegion(nx, ny, nr - nx, nb - ny)
    }

    fun scaled(factor: Double): ScreenRegion = ScreenRegion(
        (x * factor).roundToInt(),
        (y * factor).roundToInt(),
        (w * factor).roundToInt(),
        (h * factor).roundToInt(),
    )

    companion object {
        fun fullScreen(w: Int, h: Int) = ScreenRegion(0, 0, w, h)
    }
}

/** Resultado de uma busca visual bem-sucedida. */
data class MatchResult(
    val region: ScreenRegion,
    /** Similaridade 0.0–1.0. */
    val score: Double,
    /** Escala em que o template casou (1.0 = tamanho original). */
    val scale: Double = 1.0,
    /** Qual estratégia encontrou: TEMPLATE, ORB, OCR, A11Y_TREE. */
    val strategy: MatchStrategy = MatchStrategy.TEMPLATE,
    /** Ponto exato onde clicar (aplica targetOffset do Pattern). */
    val targetX: Int = region.centerX,
    val targetY: Int = region.centerY,
)

enum class MatchStrategy {
    /** matchTemplate clássico do OpenCV. */
    TEMPLATE,

    /** Casamento por features ORB — sobrevive a mudanças de escala/rotação. */
    ORB,

    /** Reconhecimento de texto via ML Kit. */
    OCR,

    /** Achado na árvore de acessibilidade pelo texto/id. */
    A11Y_TREE,

    /** Cor sólida dominante numa área. */
    COLOR,
}

/**
 * Um alvo de busca — equivalente ao `Pattern` do AnkuLua/Sikuli.
 *
 * @param source caminho da imagem OU texto a procurar (quando [isText]).
 * @param similarity limiar mínimo de confiança.
 * @param targetOffset deslocamento do ponto de clique em relação ao centro.
 * @param grayscale busca em tons de cinza (mais rápido e tolerante a temas).
 */
data class Pattern(
    val source: String,
    val similarity: Double = 0.80,
    val targetOffsetX: Int = 0,
    val targetOffsetY: Int = 0,
    val grayscale: Boolean = true,
    val isText: Boolean = false,
    /** Restringe a busca a esta região; null = tela inteira. */
    val searchRegion: ScreenRegion? = null,
) {
    fun similar(value: Double) = copy(similarity = value.coerceIn(0.0, 1.0))
    fun targetOffset(dx: Int, dy: Int) = copy(targetOffsetX = dx, targetOffsetY = dy)
    fun color() = copy(grayscale = false)
    fun gray() = copy(grayscale = true)
    fun inRegion(r: ScreenRegion?) = copy(searchRegion = r)
}
