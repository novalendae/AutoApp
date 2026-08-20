package com.kaizen.auto.runtime

import android.content.Context
import android.util.Log
import com.kaizen.auto.core.input.InputController
import com.kaizen.auto.core.vision.VisionEngine
import com.kaizen.auto.data.ScriptEntry
import com.kaizen.auto.healing.HealingEngine
import com.kaizen.auto.runtime.lua.LuaApi
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.lib.VarArgFunction
import org.luaj.vm2.lib.jse.JsePlatform
import java.io.File
import kotlin.concurrent.thread

/**
 * Executa um script Lua numa thread dedicada, com parada garantida.
 *
 * O truque central está no [installInterruptHook]: registramos um hook de debug
 * que o interpretador chama a cada N instruções. Se o usuário pediu parada, o
 * hook lança [ScriptStoppedException] de dentro do próprio interpretador — por
 * isso mesmo um `while true do end` puro, que nunca chama nossa API, morre na
 * hora. Sem isso, a única saída seria matar o processo.
 */
class ScriptRunner(
    private val context: Context,
    private val vision: VisionEngine,
    private val input: InputController,
    private val healing: HealingEngine,
    val controller: ScriptController,
) {

    /** Callback de log estruturado (nível, mensagem). */
    var onLog: ((String, String) -> Unit)? = null

    /** Callback para exibir toast na tela. */
    var onToast: ((String) -> Unit)? = null

    /** Chamado quando a execução termina, com o motivo. */
    var onFinished: ((success: Boolean, message: String) -> Unit)? = null

    private var workerThread: Thread? = null

    fun isRunning(): Boolean = workerThread?.isAlive == true

    /**
     * Dispara o script. Retorna imediatamente; o trabalho acontece na thread.
     */
    fun start(script: ScriptEntry) {
        if (isRunning()) {
            log("WARN", "Já existe um script rodando. Pare antes de iniciar outro.")
            return
        }

        controller.markRunning(script.name)
        healing.currentScriptName = script.name
        healing.onLog = { msg -> log("HEAL", msg) }
        vision.imageBaseDir = script.file.parentFile
        vision.clearTemplateCache()

        workerThread = thread(name = "KaizenLua", isDaemon = true, priority = Thread.NORM_PRIORITY) {
            var success = false
            var message = ""
            try {
                log("INFO", "▶ Iniciando '${script.name}'")
                execute(script)
                success = true
                message = controller.stopMessage ?: "Script concluído."
            } catch (e: ScriptStoppedException) {
                success = true
                message = controller.stopMessage ?: "Parado pelo usuário."
                log("INFO", "⏹ $message")
            } catch (e: ScriptExitException) {
                success = true
                message = e.exitMessage ?: "Script encerrou por conta própria."
                log("INFO", "✔ $message")
            } catch (e: FindFailedException) {
                message = "Imagem não encontrada: ${e.patternKey}"
                log("ERROR", "✖ $message")
            } catch (e: LuaError) {
                message = friendlyLuaError(e)
                log("ERROR", "✖ $message")
            } catch (e: Throwable) {
                message = e.message ?: e.javaClass.simpleName
                log("ERROR", "✖ Erro inesperado: $message")
                Log.e(TAG, "Falha na execução", e)
            } finally {
                controller.markIdle()
                healing.onLog = null
                workerThread = null
                onFinished?.invoke(success, message)
            }
        }
    }

    /**
     * Pede parada. Espera um pouco pela saída limpa; se a thread ignorar,
     * interrompe de verdade.
     */
    fun stop(joinMillis: Long = 2_000L) {
        if (!isRunning()) {
            controller.markIdle()
            return
        }
        controller.requestStop()
        val t = workerThread ?: return
        t.join(joinMillis)
        if (t.isAlive) {
            log("WARN", "Script não respondeu; forçando interrupção.")
            @Suppress("DEPRECATION")
            t.interrupt()
        }
    }

    fun pause() = controller.requestPause()
    fun resume() = controller.resume()

    // ------------------------------------------------------------------

    private fun execute(script: ScriptEntry) {
        val globals: Globals = JsePlatform.standardGlobals()

        // Removemos o que pode travar ou escapar do sandbox.
        globals.set("os", sanitizedOs(globals))
        globals.set("io", LuaValue.NIL)
        globals.set("dofile", LuaValue.NIL)
        globals.set("loadfile", LuaValue.NIL)

        // API do KaizenAuto
        LuaApi(
            context = context,
            vision = vision,
            input = input,
            healing = healing,
            controller = controller,
            logger = { level, msg -> log(level, msg) },
            toaster = { msg -> onToast?.invoke(msg) },
        ).install(globals)

        // scriptPath() aponta para a pasta do script, para require/imagens.
        val dir = script.file.parentFile?.absolutePath?.plus(File.separator).orEmpty()
        globals.set("scriptPath", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs = LuaValue.valueOf(dir)
        })

        installInterruptHook(globals)

        val source = script.file.readText()
        val chunk = globals.load(source, script.name)
        chunk.call()
    }

    /**
     * Instala o hook de instruções. É isto que torna a parada infalível.
     *
     * O LuaJ chama a função de hook a cada [HOOK_INSTRUCTION_COUNT] instruções
     * VM. Como lançamos a exceção de dentro do hook, o unwind acontece no meio
     * de qualquer laço, mesmo que o script nunca chame uma função nossa.
     */
    private fun installInterruptHook(globals: Globals) {
        val debug = globals.get("debug")
        if (debug.isnil()) {
            Log.w(TAG, "Biblioteca debug indisponível; parada dependerá da API.")
            return
        }

        val hook = object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                if (controller.shouldStop()) throw ScriptStoppedException()
                // Pausa é tratada aqui também, sem consumir CPU.
                if (!controller.awaitIfPaused()) throw ScriptStoppedException()
                return LuaValue.NONE
            }
        }

        // debug.sethook(hook, "", count)
        debug.get("sethook").call(hook, LuaValue.valueOf(""), LuaValue.valueOf(HOOK_INSTRUCTION_COUNT))
    }

    /** `os` sem execute/exit/remove — só o que é útil e seguro num script. */
    private fun sanitizedOs(globals: Globals): LuaValue {
        val original = globals.get("os")
        val safe = LuaValue.tableOf()
        listOf("time", "clock", "date", "difftime").forEach { key ->
            safe.set(key, original.get(key))
        }
        return safe
    }

    private fun friendlyLuaError(e: LuaError): String {
        val raw = e.message ?: "erro de Lua"
        // Mensagens do LuaJ vêm como "script.lua:12: attempt to ..."
        return when {
            raw.contains("attempt to call a nil value") ->
                "$raw — função não existe. Confira o nome (a API é sensível a maiúsculas)."
            raw.contains("attempt to index") ->
                "$raw — você usou ':' ou '.' em algo que é nil."
            else -> raw
        }
    }

    private fun log(level: String, message: String) {
        Log.d(TAG, "[$level] $message")
        onLog?.invoke(level, message)
    }

    private companion object {
        const val TAG = "ScriptRunner"
        /** Balanço entre responsividade (~ms) e overhead do hook. */
        const val HOOK_INSTRUCTION_COUNT = 2_000
    }
}
