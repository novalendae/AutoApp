package com.kaizen.auto.core.capture

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Captura via MediaProjection. Funciona da API 24 em diante, mas mostra o
 * ícone de gravação de tela. Mantemos UM VirtualDisplay vivo durante toda a
 * sessão e só lemos o frame mais recente do ImageReader — recriar o display a
 * cada screenshot custa ~300ms e derruba a taxa de captura.
 */
class MediaProjectionCapture(
    private val context: Context,
    private val projection: MediaProjection,
) : ScreenCapture {

    override val name: String = "MediaProjection"

    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null

    private var widthPx = 0
    private var heightPx = 0
    private var densityDpi = 0

    @Volatile private var released = false

    /** Guarda o último frame entregue pelo reader, para leitura imediata. */
    private val latestFrame = AtomicReference<Bitmap?>(null)
    private val firstFrameLatch = CountDownLatch(1)

    init {
        start()
    }

    @SuppressLint("WrongConstant")
    private fun start() {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)

        widthPx = metrics.widthPixels
        heightPx = metrics.heightPixels
        densityDpi = metrics.densityDpi

        handlerThread = HandlerThread("KaizenCapture").also { it.start() }
        handler = Handler(handlerThread!!.looper)

        // maxImages=2 dá folga para o produtor sem acumular latência.
        val reader = ImageReader.newInstance(widthPx, heightPx, PixelFormat.RGBA_8888, 2)
        reader.setOnImageAvailableListener({ r ->
            var image: Image? = null
            try {
                image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
                val bmp = image.toBitmap(widthPx, heightPx)
                val old = latestFrame.getAndSet(bmp)
                if (old != null && !old.isRecycled && old !== bmp) old.recycle()
                firstFrameLatch.countDown()
            } catch (t: Throwable) {
                Log.w(TAG, "Falha ao ler frame: ${t.message}")
            } finally {
                image?.close()
            }
        }, handler)
        imageReader = reader

        // Em Android 14+ é obrigatório registrar um callback antes de criar o display.
        runCatching {
            projection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.i(TAG, "MediaProjection encerrada pelo sistema/usuário.")
                    release()
                }
            }, handler)
        }

        virtualDisplay = projection.createVirtualDisplay(
            "KaizenAuto",
            widthPx,
            heightPx,
            densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            handler,
        )
    }

    override fun isReady(): Boolean = !released && virtualDisplay != null

    override fun capture(timeoutMs: Long): Bitmap? {
        if (released) return null
        // Na primeira chamada esperamos o pipeline aquecer.
        if (latestFrame.get() == null) {
            runCatching { firstFrameLatch.await(timeoutMs, TimeUnit.MILLISECONDS) }
        }
        val frame = latestFrame.get() ?: return null
        // Devolvemos uma cópia: o frame vivo pode ser reciclado a qualquer momento.
        return runCatching { frame.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull()
    }

    override fun release() {
        if (released) return
        released = true
        runCatching { virtualDisplay?.release() }
        runCatching { imageReader?.close() }
        runCatching { projection.stop() }
        runCatching { handlerThread?.quitSafely() }
        latestFrame.getAndSet(null)?.let { if (!it.isRecycled) it.recycle() }
        virtualDisplay = null
        imageReader = null
        handlerThread = null
        handler = null
    }

    /**
     * O buffer do ImageReader vem com padding de linha (rowStride > width*4).
     * Ignorar isso produz a clássica imagem "enviesada" na diagonal.
     */
    private fun Image.toBitmap(targetW: Int, targetH: Int): Bitmap {
        val plane = planes[0]
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * targetW
        val paddedWidth = targetW + rowPadding / pixelStride

        val bitmap = Bitmap.createBitmap(paddedWidth, targetH, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(plane.buffer)

        return if (rowPadding == 0) {
            bitmap
        } else {
            val cropped = Bitmap.createBitmap(bitmap, 0, 0, targetW, targetH)
            bitmap.recycle()
            cropped
        }
    }

    private companion object {
        const val TAG = "MediaProjectionCapture"
    }
}
