package com.kaizen.auto.runtime

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicBoolean

enum class RunState { IDLE, RUNNING, PAUSED, STOPPING, ERROR }

/**
 * O INTERRUPTOR.
 *
 * Este é o requisito que você destacou como mais importante: nada de script
 * rodando pra sempre em segundo plano. O controle acontece em 3 camadas:
 *
 *  1. [stopRequested] — flag atômica consultada pelo hook de debug do Lua a cada
 *     N instruções. Mesmo um `while true do end` sem chamadas de API é
 *     interrompido, porque o hook dispara direto no interpretador.
 *
 *  2. Todas as funções bloqueantes (wait, waitFor, sleep) checam a flag em
 *     fatias curtas, então a parada é percebida em <100ms.
 *
 *  3. [ScriptRunner] mata a thread e libera MediaProjection + notificação se o
 *     script ignorar as duas primeiras camadas.
 *
 * Além disso há limites automáticos: tempo máximo de execução e parada quando a
 * tela desliga, para o script não drenar a bateria esquecido.
 */
class ScriptController {

    private val _state = MutableStateFlow(RunState.IDLE)
    val state: StateFlow<RunState> = _state

    private val _currentScript = MutableStateFlow<String?>(null)
    val currentScript: StateFlow<String?> = _currentScript

    private val stopRequested = AtomicBoolean(false)
    private val pauseRequested = AtomicBoolean(false)

    /** Momento em que a execução começou (para o limite de tempo). */
    @Volatile var startedAt: Long = 0L
        private set

    /** Limite duro de execução em minutos. 0 = sem limite. */
    @Volatile var maxRuntimeMinutes: Int = 0

    /** Mensagem final definida pelo script via setStopMessage(). */
    @Volatile var stopMessage: String? = null

    fun markRunning(scriptName: String) {
        stopRequested.set(false)
        pauseRequested.set(false)
        stopMessage = null
        startedAt = System.currentTimeMillis()
        _currentScript.value = scriptName
        _state.value = RunState.RUNNING
    }

    fun requestStop() {
        if (_state.value == RunState.IDLE) return
        stopRequested.set(true)
        pauseRequested.set(false)
        _state.value = RunState.STOPPING
    }

    fun requestPause() {
        if (_state.value != RunState.RUNNING) return
        pauseRequested.set(true)
        _state.value = RunState.PAUSED
    }

    fun resume() {
        if (_state.value != RunState.PAUSED) return
        pauseRequested.set(false)
        _state.value = RunState.RUNNING
    }

    fun markIdle() {
        stopRequested.set(false)
        pauseRequested.set(false)
        _currentScript.value = null
        _state.value = RunState.IDLE
    }

    fun markError() {
        _state.value = RunState.ERROR
    }

    val isRunning: Boolean get() = _state.value == RunState.RUNNING || _state.value == RunState.PAUSED

    /**
     * Consultado em todo lugar. Retorna true se o script deve encerrar AGORA.
     * Também aplica o limite de tempo automático.
     */
    fun shouldStop(): Boolean {
        if (stopRequested.get()) return true
        val limit = maxRuntimeMinutes
        if (limit > 0 && startedAt > 0) {
            val elapsedMin = (System.currentTimeMillis() - startedAt) / 60_000
            if (elapsedMin >= limit) {
                stopMessage = "Limite de $limit min atingido — parada automática."
                stopRequested.set(true)
                return true
            }
        }
        return false
    }

    /**
     * Espera enquanto estiver pausado. Retorna false se, durante a pausa, o
     * usuário mandou parar de vez.
     */
    fun awaitIfPaused(): Boolean {
        while (pauseRequested.get() && !stopRequested.get()) {
            Thread.sleep(100)
        }
        return !stopRequested.get()
    }

    /**
     * Sleep cooperativo: dorme em fatias, checando parada entre elas.
     * @return false se foi interrompido.
     */
    fun interruptibleSleep(millis: Long): Boolean {
        if (millis <= 0) return !shouldStop()
        val deadline = System.currentTimeMillis() + millis
        while (System.currentTimeMillis() < deadline) {
            if (shouldStop()) return false
            if (!awaitIfPaused()) return false
            val remaining = deadline - System.currentTimeMillis()
            Thread.sleep(remaining.coerceIn(1L, SLICE_MS))
        }
        return !shouldStop()
    }

    private companion object {
        const val SLICE_MS = 80L
    }
}
