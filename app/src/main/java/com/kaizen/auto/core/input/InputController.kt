package com.kaizen.auto.core.input

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.util.Log
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random

/**
 * As "mãos" do robô: transforma coordenadas em gestos reais via
 * AccessibilityService.dispatchGesture (API 24+, sem root).
 *
 * Todos os métodos são BLOQUEANTES: só retornam quando o sistema confirma que o
 * gesto terminou. Isso é essencial para o script Lua, que é sequencial —
 * `click(); click()` sem espera resulta no segundo gesto sendo cancelado.
 */
class InputController(
    private val serviceProvider: () -> AccessibilityService?,
) {

    /** Humanização: jitter de posição em px e variação de duração em ms. */
    var humanize: Boolean = true
    var positionJitterPx: Int = 3
    var durationJitterMs: Int = 25

    fun isReady(): Boolean = serviceProvider() != null

    // ------------------------------------------------------------------
    // Gestos básicos
    // ------------------------------------------------------------------

    fun tap(x: Float, y: Float, durationMs: Long = 60L): Boolean {
        val (px, py) = jitter(x, y)
        val path = Path().apply { moveTo(px, py) }
        return dispatch(path, 0L, jitterDuration(durationMs))
    }

    fun longPress(x: Float, y: Float, durationMs: Long = 700L): Boolean {
        val (px, py) = jitter(x, y)
        val path = Path().apply { moveTo(px, py) }
        return dispatch(path, 0L, durationMs)
    }

    fun doubleTap(x: Float, y: Float, gapMs: Long = 90L): Boolean {
        val first = tap(x, y, 50L)
        Thread.sleep(gapMs)
        val second = tap(x, y, 50L)
        return first && second
    }

    /**
     * Swipe em linha reta. Duração muito curta vira "fling", duração longa vira
     * arraste controlado — o script escolhe pela semântica que quer.
     */
    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long = 300L): Boolean {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        return dispatch(path, 0L, durationMs)
    }

    /**
     * Arrastar e soltar de verdade: pressiona, segura, move devagar e solta.
     * Muitos jogos ignoram um swipe simples porque não houve "hold" inicial.
     */
    fun dragDrop(
        x1: Float, y1: Float,
        x2: Float, y2: Float,
        holdMs: Long = 400L,
        moveMs: Long = 700L,
        releaseMs: Long = 200L,
    ): Boolean {
        val service = serviceProvider() ?: return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            // continueStroke só existe na API 26+; degradamos para swipe longo.
            return swipe(x1, y1, x2, y2, holdMs + moveMs)
        }

        val down = Path().apply { moveTo(x1, y1) }
        val move = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val up = Path().apply { moveTo(x2, y2) }

        val s1 = GestureDescription.StrokeDescription(down, 0L, holdMs, true)
        val s2 = s1.continueStroke(move, 0L, moveMs, true)
        val s3 = s2.continueStroke(up, 0L, releaseMs, false)

        return dispatchStrokes(service, listOf(s1)) &&
            dispatchStrokes(service, listOf(s2)) &&
            dispatchStrokes(service, listOf(s3))
    }

    /** Swipe com curva de Bézier — parece muito mais humano que a linha reta. */
    fun humanSwipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long = 400L): Boolean {
        val dist = hypot((x2 - x1).toDouble(), (y2 - y1).toDouble()).toFloat()
        val bow = (dist * 0.12f).coerceAtMost(80f)
        val midX = (x1 + x2) / 2f + Random.nextFloat() * bow - bow / 2f
        val midY = (y1 + y2) / 2f + Random.nextFloat() * bow - bow / 2f

        val path = Path().apply {
            moveTo(x1, y1)
            quadTo(midX, midY, x2, y2)
        }
        return dispatch(path, 0L, jitterDuration(durationMs))
    }

    /** Pinça para zoom. positive = abrir (zoom in), negative = fechar. */
    fun pinch(centerX: Float, centerY: Float, fromRadius: Float, toRadius: Float, durationMs: Long = 500L): Boolean {
        val service = serviceProvider() ?: return false
        val p1 = Path().apply {
            moveTo(centerX - fromRadius, centerY)
            lineTo(centerX - toRadius, centerY)
        }
        val p2 = Path().apply {
            moveTo(centerX + fromRadius, centerY)
            lineTo(centerX + toRadius, centerY)
        }
        val strokes = listOf(
            GestureDescription.StrokeDescription(p1, 0L, durationMs),
            GestureDescription.StrokeDescription(p2, 0L, durationMs),
        )
        return dispatchStrokes(service, strokes)
    }

    // ------------------------------------------------------------------
    // Ações globais do sistema
    // ------------------------------------------------------------------

    fun back(): Boolean = global(AccessibilityService.GLOBAL_ACTION_BACK)
    fun home(): Boolean = global(AccessibilityService.GLOBAL_ACTION_HOME)
    fun recents(): Boolean = global(AccessibilityService.GLOBAL_ACTION_RECENTS)
    fun notifications(): Boolean = global(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)

    private fun global(action: Int): Boolean =
        serviceProvider()?.performGlobalAction(action) ?: false

    // ------------------------------------------------------------------
    // Infra de despacho
    // ------------------------------------------------------------------

    private fun dispatch(path: Path, startTime: Long, durationMs: Long): Boolean {
        val service = serviceProvider() ?: run {
            Log.w(TAG, "Serviço de acessibilidade não está ativo.")
            return false
        }
        val stroke = GestureDescription.StrokeDescription(
            path,
            startTime,
            durationMs.coerceIn(1L, MAX_GESTURE_MS),
        )
        return dispatchStrokes(service, listOf(stroke))
    }

    private fun dispatchStrokes(
        service: AccessibilityService,
        strokes: List<GestureDescription.StrokeDescription>,
    ): Boolean {
        val builder = GestureDescription.Builder()
        strokes.forEach { builder.addStroke(it) }

        val latch = CountDownLatch(1)
        var success = false

        val accepted = service.dispatchGesture(
            builder.build(),
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    success = true
                    latch.countDown()
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    success = false
                    latch.countDown()
                }
            },
            null,
        )

        if (!accepted) {
            Log.w(TAG, "dispatchGesture recusou o gesto (serviço sem canPerformGestures?).")
            return false
        }

        // Timeout de segurança: se o callback nunca vier, não travamos o script.
        val finished = latch.await(GESTURE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        return finished && success
    }

    private fun jitter(x: Float, y: Float): Pair<Float, Float> {
        if (!humanize || positionJitterPx <= 0) return x to y
        val j = positionJitterPx
        return (x + Random.nextInt(-j, j + 1)) to (y + Random.nextInt(-j, j + 1))
    }

    private fun jitterDuration(base: Long): Long {
        if (!humanize || durationJitterMs <= 0) return base
        val delta = Random.nextInt(-durationJitterMs, durationJitterMs + 1)
        return (base + delta).coerceAtLeast(20L)
    }

    private companion object {
        const val TAG = "InputController"
        const val GESTURE_TIMEOUT_MS = 15_000L
        const val MAX_GESTURE_MS = 59_000L // limite do framework é 60s
    }
}
