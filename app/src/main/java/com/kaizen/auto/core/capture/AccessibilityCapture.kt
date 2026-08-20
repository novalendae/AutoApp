package com.kaizen.auto.core.capture

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.view.Display
import androidx.annotation.RequiresApi
import java.util.concurrent.Executors
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.TimeUnit

/**
 * Captura via AccessibilityService.takeScreenshot (API 30+).
 *
 * Vantagens sobre o MediaProjection: não pede diálogo de consentimento, não
 * mostra o ícone de gravação e não segura um VirtualDisplay.
 *
 * Limitação real: o framework aplica rate limiting (~1 captura por segundo em
 * várias ROMs). Por isso o CaptureManager usa esta fonte como preferencial mas
 * cai para o MediaProjection quando o script pede frames em alta cadência.
 */
@RequiresApi(Build.VERSION_CODES.R)
class AccessibilityCapture(
    private val serviceProvider: () -> AccessibilityService?,
) : ScreenCapture {

    override val name: String = "AccessibilityScreenshot"

    private val executor = Executors.newSingleThreadExecutor()

    override fun isReady(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && serviceProvider() != null

    override fun capture(timeoutMs: Long): Bitmap? {
        val service = serviceProvider() ?: return null
        // SynchronousQueue faz a ponte entre o callback assíncrono e o chamador.
        val queue = SynchronousQueue<Any>()

        try {
            service.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                executor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                        val bmp = try {
                            val hw = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                            // Hardware bitmaps não permitem getPixels(); copiamos p/ software.
                            hw?.copy(Bitmap.Config.ARGB_8888, false)
                        } catch (t: Throwable) {
                            Log.w(TAG, "wrapHardwareBuffer falhou: ${t.message}")
                            null
                        } finally {
                            runCatching { result.hardwareBuffer.close() }
                        }
                        queue.offer(bmp ?: FAILURE, timeoutMs, TimeUnit.MILLISECONDS)
                    }

                    override fun onFailure(errorCode: Int) {
                        Log.w(TAG, "takeScreenshot falhou, código=$errorCode")
                        queue.offer(FAILURE, timeoutMs, TimeUnit.MILLISECONDS)
                    }
                },
            )
        } catch (t: Throwable) {
            Log.w(TAG, "takeScreenshot lançou exceção: ${t.message}")
            return null
        }

        val result = queue.poll(timeoutMs, TimeUnit.MILLISECONDS)
        return result as? Bitmap
    }

    override fun release() {
        executor.shutdownNow()
    }

    private companion object {
        const val TAG = "AccessibilityCapture"
        val FAILURE = Any()
    }
}
