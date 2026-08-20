package com.kaizen.auto.core.capture

import android.content.Context
import android.graphics.Bitmap
import android.media.projection.MediaProjection
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.kaizen.auto.service.KaizenAccessibilityService

/**
 * Decide de onde vem cada frame e implementa cache de curta duração.
 *
 * Regra de escolha:
 *  1. Se existir sessão de MediaProjection ativa → usa ela (rápida, sem rate limit).
 *  2. Senão, se API >= 30 e o AccessibilityService estiver ligado → takeScreenshot.
 *  3. Senão → sem visão; o runtime avisa o usuário.
 *
 * O cache de [cacheWindowMs] evita capturar 5 vezes seguidas quando o script faz
 * `exists(a) or exists(b) or exists(c)` no mesmo instante lógico.
 */
class CaptureManager(private val context: Context) {

    private var projectionSource: MediaProjectionCapture? = null
    private var accessibilitySource: AccessibilityCapture? = null

    private var cachedFrame: Bitmap? = null
    private var cachedAt: Long = 0L

    /** Janela de reaproveitamento do frame, em ms. 0 desliga o cache. */
    @Volatile var cacheWindowMs: Long = 120L

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            accessibilitySource = AccessibilityCapture { KaizenAccessibilityService.instance }
        }
    }

    /** Chamado pelo AutomationService quando o usuário aceita o diálogo de captura. */
    fun attachProjection(projection: MediaProjection) {
        releaseProjection()
        projectionSource = try {
            MediaProjectionCapture(context, projection)
        } catch (t: Throwable) {
            Log.e(TAG, "Não foi possível iniciar MediaProjection: ${t.message}")
            null
        }
    }

    fun releaseProjection() {
        projectionSource?.release()
        projectionSource = null
    }

    fun activeSourceName(): String = when {
        projectionSource?.isReady() == true -> projectionSource!!.name
        accessibilitySource?.isReady() == true -> accessibilitySource!!.name
        else -> "nenhuma"
    }

    fun hasVision(): Boolean =
        projectionSource?.isReady() == true || accessibilitySource?.isReady() == true

    /**
     * Devolve o frame atual da tela.
     * @param fresh true força uma captura nova, ignorando o cache.
     */
    @Synchronized
    fun grab(fresh: Boolean = false): Bitmap? {
        val now = SystemClock.elapsedRealtime()
        if (!fresh && cacheWindowMs > 0) {
            val cached = cachedFrame
            if (cached != null && !cached.isRecycled && now - cachedAt <= cacheWindowMs) {
                return cached
            }
        }

        val bmp = projectionSource?.takeIf { it.isReady() }?.capture()
            ?: accessibilitySource?.takeIf { it.isReady() }?.capture()

        if (bmp != null) {
            val old = cachedFrame
            cachedFrame = bmp
            cachedAt = now
            if (old != null && !old.isRecycled && old !== bmp) old.recycle()
        }
        return bmp
    }

    /** Invalida o cache — usado logo após um toque, quando a tela certamente mudou. */
    @Synchronized
    fun invalidate() {
        cachedAt = 0L
    }

    fun release() {
        releaseProjection()
        accessibilitySource?.release()
        cachedFrame?.let { if (!it.isRecycled) it.recycle() }
        cachedFrame = null
    }

    private companion object {
        const val TAG = "CaptureManager"
    }
}
