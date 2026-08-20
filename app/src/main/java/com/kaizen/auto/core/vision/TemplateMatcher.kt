package com.kaizen.auto.core.vision

import android.graphics.Bitmap
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfKeyPoint
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.features2d.DescriptorMatcher
import org.opencv.features2d.ORB
import org.opencv.imgproc.Imgproc
import kotlin.math.roundToInt

/**
 * Motor de casamento de imagens. Duas estratégias complementares:
 *
 *  - [findTemplate]: matchTemplate + varredura multi-escala. Rápido e preciso
 *    quando a imagem é pixel-a-pixel parecida. É o caminho padrão.
 *
 *  - [findByFeatures]: ORB + BFMatcher. Bem mais lento, mas encontra o elemento
 *    mesmo quando ele mudou de tamanho, sofreu re-tema ou foi redesenhado. É a
 *    carta na manga que o self-healing usa quando o template falha.
 */
object TemplateMatcher {

    private const val TAG = "TemplateMatcher"

    /** Escalas testadas na varredura multi-escala, em ordem de probabilidade. */
    private val DEFAULT_SCALES = doubleArrayOf(1.0, 0.95, 1.05, 0.9, 1.1, 0.85, 1.15, 0.8, 1.25)

    // ------------------------------------------------------------------
    // Template matching
    // ------------------------------------------------------------------

    /**
     * Procura [template] dentro de [screen].
     *
     * @param region limita a área de busca (grande ganho de performance).
     * @param scales escalas a testar; passe doubleArrayOf(1.0) para desligar.
     * @return o melhor match acima do limiar, ou null.
     */
    fun findTemplate(
        screen: Bitmap,
        template: Bitmap,
        threshold: Double,
        region: ScreenRegion? = null,
        grayscale: Boolean = true,
        scales: DoubleArray = DEFAULT_SCALES,
    ): MatchResult? {
        var screenMat: Mat? = null
        var templateMat: Mat? = null
        try {
            screenMat = screen.toMat(grayscale)
            templateMat = template.toMat(grayscale)

            val roi = region?.clampTo(screen.width, screen.height)
            val searchMat = if (roi != null && roi.w > 0 && roi.h > 0) {
                Mat(screenMat, Rect(roi.x, roi.y, roi.w, roi.h))
            } else {
                screenMat
            }
            val offsetX = roi?.x ?: 0
            val offsetY = roi?.y ?: 0

            var best: MatchResult? = null

            for (scale in scales) {
                val tw = (templateMat.cols() * scale).roundToInt()
                val th = (templateMat.rows() * scale).roundToInt()
                // Template maior que a área de busca não tem como casar.
                if (tw < 8 || th < 8) continue
                if (tw > searchMat.cols() || th > searchMat.rows()) continue

                val scaled = if (scale == 1.0) {
                    templateMat
                } else {
                    Mat().also { Imgproc.resize(templateMat, it, Size(tw.toDouble(), th.toDouble())) }
                }

                val result = Mat()
                try {
                    Imgproc.matchTemplate(searchMat, scaled, result, Imgproc.TM_CCOEFF_NORMED)
                    val mm = Core.minMaxLoc(result)
                    val score = mm.maxVal

                    if (score >= threshold && (best == null || score > best.score)) {
                        val loc: Point = mm.maxLoc
                        best = MatchResult(
                            region = ScreenRegion(
                                x = loc.x.roundToInt() + offsetX,
                                y = loc.y.roundToInt() + offsetY,
                                w = tw,
                                h = th,
                            ),
                            score = score,
                            scale = scale,
                            strategy = MatchStrategy.TEMPLATE,
                        )
                        // Match quase perfeito na escala original: não vale continuar.
                        if (score >= 0.985) break
                    }
                } finally {
                    result.release()
                    if (scaled !== templateMat) scaled.release()
                }
            }

            if (searchMat !== screenMat) searchMat.release()
            return best?.withCenterTarget()
        } catch (t: Throwable) {
            Log.e(TAG, "findTemplate falhou: ${t.message}", t)
            return null
        } finally {
            screenMat?.release()
            templateMat?.release()
        }
    }

    /** Todas as ocorrências acima do limiar, com supressão de sobreposição. */
    fun findAllTemplates(
        screen: Bitmap,
        template: Bitmap,
        threshold: Double,
        region: ScreenRegion? = null,
        grayscale: Boolean = true,
        maxResults: Int = 50,
    ): List<MatchResult> {
        var screenMat: Mat? = null
        var templateMat: Mat? = null
        val out = ArrayList<MatchResult>()
        try {
            screenMat = screen.toMat(grayscale)
            templateMat = template.toMat(grayscale)

            val roi = region?.clampTo(screen.width, screen.height)
            val searchMat = if (roi != null && roi.w > 0 && roi.h > 0) {
                Mat(screenMat, Rect(roi.x, roi.y, roi.w, roi.h))
            } else {
                screenMat
            }
            val offsetX = roi?.x ?: 0
            val offsetY = roi?.y ?: 0

            if (templateMat.cols() > searchMat.cols() || templateMat.rows() > searchMat.rows()) {
                return emptyList()
            }

            val result = Mat()
            Imgproc.matchTemplate(searchMat, templateMat, result, Imgproc.TM_CCOEFF_NORMED)

            val tw = templateMat.cols()
            val th = templateMat.rows()

            // Estratégia clássica: pega o máximo, "apaga" a vizinhança, repete.
            while (out.size < maxResults) {
                val mm = Core.minMaxLoc(result)
                if (mm.maxVal < threshold) break
                val loc = mm.maxLoc
                out += MatchResult(
                    region = ScreenRegion(
                        x = loc.x.roundToInt() + offsetX,
                        y = loc.y.roundToInt() + offsetY,
                        w = tw,
                        h = th,
                    ),
                    score = mm.maxVal,
                    strategy = MatchStrategy.TEMPLATE,
                ).withCenterTarget()

                val sx = (loc.x - tw / 2).coerceAtLeast(0.0).toInt()
                val sy = (loc.y - th / 2).coerceAtLeast(0.0).toInt()
                val ex = (loc.x + tw / 2).coerceAtMost((result.cols() - 1).toDouble()).toInt()
                val ey = (loc.y + th / 2).coerceAtMost((result.rows() - 1).toDouble()).toInt()
                if (ex > sx && ey > sy) {
                    Mat(result, Rect(sx, sy, ex - sx, ey - sy)).setTo(org.opencv.core.Scalar(0.0))
                } else {
                    break
                }
            }

            result.release()
            if (searchMat !== screenMat) searchMat.release()
            return out.sortedByDescending { it.score }
        } catch (t: Throwable) {
            Log.e(TAG, "findAllTemplates falhou: ${t.message}", t)
            return out
        } finally {
            screenMat?.release()
            templateMat?.release()
        }
    }

    // ------------------------------------------------------------------
    // ORB / features — o plano B do self-healing
    // ------------------------------------------------------------------

    /**
     * Casamento por pontos de interesse. Encontra o template mesmo que ele tenha
     * mudado de escala ou de cor, desde que a "forma" continue reconhecível.
     *
     * Retorna um match cuja região é a caixa que envolve os pontos casados.
     */
    fun findByFeatures(
        screen: Bitmap,
        template: Bitmap,
        minMatches: Int = 8,
        region: ScreenRegion? = null,
    ): MatchResult? {
        var screenMat: Mat? = null
        var templateMat: Mat? = null
        try {
            screenMat = screen.toMat(grayscale = true)
            templateMat = template.toMat(grayscale = true)

            val roi = region?.clampTo(screen.width, screen.height)
            val searchMat = if (roi != null && roi.w > 0 && roi.h > 0) {
                Mat(screenMat, Rect(roi.x, roi.y, roi.w, roi.h))
            } else {
                screenMat
            }
            val offsetX = roi?.x ?: 0
            val offsetY = roi?.y ?: 0

            val orb = ORB.create(1000)
            val kpTemplate = MatOfKeyPoint()
            val kpScreen = MatOfKeyPoint()
            val descTemplate = Mat()
            val descScreen = Mat()

            orb.detectAndCompute(templateMat, Mat(), kpTemplate, descTemplate)
            orb.detectAndCompute(searchMat, Mat(), kpScreen, descScreen)

            if (descTemplate.empty() || descScreen.empty()) return null

            val matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING)
            val matches = org.opencv.core.MatOfDMatch()
            matcher.match(descTemplate, descScreen, matches)

            val list = matches.toList().sortedBy { it.distance }
            if (list.size < minMatches) return null

            // Ficamos com os 25% melhores, no mínimo minMatches.
            val keep = list.take(maxOf(minMatches, list.size / 4))
            val maxDistance = 64f
            val good = keep.filter { it.distance <= maxDistance }
            if (good.size < minMatches) return null

            val screenPoints = kpScreen.toArray()
            var minX = Int.MAX_VALUE
            var minY = Int.MAX_VALUE
            var maxX = Int.MIN_VALUE
            var maxY = Int.MIN_VALUE
            var distanceSum = 0f

            good.forEach { m ->
                val p = screenPoints.getOrNull(m.trainIdx)?.pt ?: return@forEach
                val px = p.x.roundToInt()
                val py = p.y.roundToInt()
                if (px < minX) minX = px
                if (py < minY) minY = py
                if (px > maxX) maxX = px
                if (py > maxY) maxY = py
                distanceSum += m.distance
            }
            if (minX == Int.MAX_VALUE) return null

            // Confiança aproximada: quanto menor a distância média de Hamming, melhor.
            val avgDistance = distanceSum / good.size
            val score = (1.0 - (avgDistance / maxDistance)).coerceIn(0.0, 1.0)

            val w = (maxX - minX).coerceAtLeast(template.width / 2)
            val h = (maxY - minY).coerceAtLeast(template.height / 2)

            kpTemplate.release(); kpScreen.release()
            descTemplate.release(); descScreen.release(); matches.release()
            if (searchMat !== screenMat) searchMat.release()

            return MatchResult(
                region = ScreenRegion(minX + offsetX, minY + offsetY, w, h),
                score = score,
                strategy = MatchStrategy.ORB,
            ).withCenterTarget()
        } catch (t: Throwable) {
            Log.e(TAG, "findByFeatures falhou: ${t.message}", t)
            return null
        } finally {
            screenMat?.release()
            templateMat?.release()
        }
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    /** Diferença perceptual entre dois bitmaps (0 = idênticos, 1 = opostos). */
    fun difference(a: Bitmap, b: Bitmap): Double {
        var ma: Mat? = null
        var mb: Mat? = null
        var diff: Mat? = null
        return try {
            ma = a.toMat(true)
            mb = b.toMat(true)
            if (ma.size() != mb.size()) {
                Imgproc.resize(mb, mb, ma.size())
            }
            diff = Mat()
            Core.absdiff(ma, mb, diff)
            Core.mean(diff).`val`[0] / 255.0
        } catch (t: Throwable) {
            1.0
        } finally {
            ma?.release(); mb?.release(); diff?.release()
        }
    }

    private fun Bitmap.toMat(grayscale: Boolean): Mat {
        val mat = Mat()
        // Utils exige ARGB_8888; hardware bitmaps já foram convertidos na captura.
        val safe = if (config == Bitmap.Config.ARGB_8888) this
        else copy(Bitmap.Config.ARGB_8888, false)
        Utils.bitmapToMat(safe, mat)
        if (safe !== this) safe.recycle()

        return if (grayscale) {
            val gray = Mat()
            Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY)
            mat.release()
            gray
        } else {
            val rgb = Mat()
            Imgproc.cvtColor(mat, rgb, Imgproc.COLOR_RGBA2RGB)
            mat.release()
            rgb
        }
    }

    private fun ScreenRegion.clampTo(maxW: Int, maxH: Int): ScreenRegion {
        val nx = x.coerceIn(0, maxOf(0, maxW - 1))
        val ny = y.coerceIn(0, maxOf(0, maxH - 1))
        val nw = w.coerceIn(1, maxW - nx)
        val nh = h.coerceIn(1, maxH - ny)
        return ScreenRegion(nx, ny, nw, nh)
    }

    private fun MatchResult.withCenterTarget() =
        copy(targetX = region.centerX, targetY = region.centerY)
}
