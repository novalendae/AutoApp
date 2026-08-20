package com.kaizen.auto.core.capture

import android.graphics.Bitmap

/**
 * Abstração dos "olhos" do robô. Duas implementações concorrem:
 *  - AccessibilityCapture   (API 30+, silenciosa, sem barra de gravação)
 *  - MediaProjectionCapture (API 24+, exige o diálogo de consentimento)
 *
 * O [CaptureManager] escolhe a melhor disponível em tempo de execução.
 */
interface ScreenCapture {

    /** Nome legível da fonte, usado nos logs e na tela de diagnóstico. */
    val name: String

    /** true se a fonte está pronta para entregar frames agora. */
    fun isReady(): Boolean

    /**
     * Captura síncrona da tela inteira.
     * @return o bitmap ou null em caso de falha (o chamador decide o fallback).
     */
    fun capture(timeoutMs: Long = 3_000L): Bitmap?

    /** Libera recursos (ImageReader, VirtualDisplay, etc). */
    fun release()
}
