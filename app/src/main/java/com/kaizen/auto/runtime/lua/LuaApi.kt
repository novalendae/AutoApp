package com.kaizen.auto.runtime.lua

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.kaizen.auto.core.input.InputController
import com.kaizen.auto.core.vision.MatchResult
import com.kaizen.auto.core.vision.Pattern
import com.kaizen.auto.core.vision.ScreenRegion
import com.kaizen.auto.core.vision.VisionEngine
import com.kaizen.auto.healing.HealingEngine
import com.kaizen.auto.runtime.FindFailedException
import com.kaizen.auto.runtime.ScriptController
import com.kaizen.auto.runtime.ScriptExitException
import com.kaizen.auto.runtime.ScriptStoppedException
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.lib.OneArgFunction
import org.luaj.vm2.lib.ThreeArgFunction
import org.luaj.vm2.lib.TwoArgFunction
import org.luaj.vm2.lib.VarArgFunction
import org.luaj.vm2.lib.ZeroArgFunction
import kotlin.random.Random

/**
 * Ponte Kotlin ⇄ Lua. Expõe uma API compatível em espírito com AnkuLua/Sikuli:
 *
 *   click("botao.png")            existsClick("ok.png", 5)
 *   wait("tela.png", 10)          waitVanish("carregando.png", 30)
 *   exists("erro.png")            findAll("moeda.png")
 *   Region(x,y,w,h):click(...)    Pattern("x.png"):similar(0.9)
 *   swipe(a, b)                   type("texto")
 *   toast("msg")                  log("msg")
 *
 * Diferenças conscientes em relação ao AnkuLua:
 *  - Toda função que espera é interrompível (o botão PARAR funciona sempre).
 *  - findText()/clickText() usam OCR, sem precisar recortar imagem.
 *  - heal.on()/heal.off() controlam o self-healing dentro do próprio script.
 */
class LuaApi(
    private val context: Context,
    private val vision: VisionEngine,
    private val input: InputController,
    private val healing: HealingEngine,
    private val controller: ScriptController,
    private val logger: (String, String) -> Unit,
    private val toaster: (String) -> Unit,
) {

    /** Último match encontrado — equivalente ao getLastMatch() do Sikuli. */
    private var lastMatch: MatchResult? = null

    /** Configurações globais que o script pode ajustar. */
    private var defaultSimilarity = 0.80
    private var autoWaitTimeout = 3.0
    private var clickDelayMs = 250L

    // ==================================================================
    // Instalação das funções globais
    // ==================================================================

    fun install(globals: LuaValue) {
        // ---- Busca -----------------------------------------------------
        globals.set("find", varargs { args -> findOrThrow(args.toPattern(0), args.timeoutOr(1, 0.0)).toLua() })
        globals.set("exists", varargs { args ->
            val m = findWithWait(args.toPattern(0), args.timeoutOr(1, 0.0))
            if (m == null) LuaValue.FALSE else m.toLua()
        })
        globals.set("wait", varargs { args -> luaWait(args) })
        globals.set("waitVanish", varargs { args ->
            val ok = vision.waitVanish(args.toPattern(0), args.timeoutOr(1, autoWaitTimeout), controller::shouldStop)
            checkStop()
            LuaValue.valueOf(ok)
        })
        globals.set("findAll", varargs { args -> vision.findAll(args.toPattern(0)).toLuaTable() })
        globals.set("findAllNoFindException", varargs { args -> vision.findAll(args.toPattern(0)).toLuaTable() })

        // ---- Clique ----------------------------------------------------
        globals.set("click", varargs { args -> doClick(args, required = true, times = 1) })
        globals.set("doubleClick", varargs { args -> doClick(args, required = true, times = 2) })
        globals.set("waitClick", varargs { args ->
            val pattern = args.toPattern(0)
            val timeout = args.timeoutOr(1, autoWaitTimeout)
            val match = vision.waitFor(pattern, timeout, controller::shouldStop)
            checkStop()
            if (match == null) throw FindFailedException(pattern.source, "waitClick: '${pattern.source}' não apareceu em ${timeout}s")
            performClick(match)
            match.toLua()
        })
        globals.set("existsClick", varargs { args ->
            val pattern = args.toPattern(0)
            val timeout = args.timeoutOr(1, 0.0)
            val match = findWithWait(pattern, timeout)
            if (match == null) {
                LuaValue.FALSE
            } else {
                performClick(match)
                LuaValue.TRUE
            }
        })
        globals.set("longClick", varargs { args ->
            val target = args.resolveTarget() ?: return@varargs LuaValue.FALSE
            val duration = if (args.narg() > 1) args.todouble(2).toLong() else 800L
            invalidateAfter { input.longPress(target.first.toFloat(), target.second.toFloat(), duration) }
            LuaValue.TRUE
        })

        // ---- Texto / OCR ----------------------------------------------
        globals.set("findText", varargs { args ->
            val query = args.checkjstring(1)
            val m = vision.find(Pattern(source = query, isText = true))
            if (m == null) LuaValue.FALSE else { lastMatch = m; m.toLua() }
        })
        globals.set("clickText", varargs { args ->
            val query = args.checkjstring(1)
            val timeout = args.timeoutOr(1, autoWaitTimeout)
            val m = vision.waitFor(Pattern(source = query, isText = true), timeout, controller::shouldStop)
            checkStop()
            if (m == null) return@varargs LuaValue.FALSE
            performClick(m)
            LuaValue.TRUE
        })
        globals.set("readText", varargs { args ->
            val screen = vision.currentFrame() ?: return@varargs LuaValue.valueOf("")
            val region = if (args.narg() >= 1) args.arg(1).toRegionOrNull() else null
            val blocks = com.kaizen.auto.core.vision.OcrEngine.readAll(screen)
                .filter { region == null || region.containsCenter(it.region) }
            LuaValue.valueOf(blocks.joinToString("\n") { it.text })
        })

        // ---- Gestos ----------------------------------------------------
        globals.set("swipe", varargs { args -> doSwipe(args, human = false) })
        globals.set("humanSwipe", varargs { args -> doSwipe(args, human = true) })
        globals.set("dragDrop", varargs { args ->
            val from = args.arg(1).toPointOrNull() ?: return@varargs LuaValue.FALSE
            val to = args.arg(2).toPointOrNull() ?: return@varargs LuaValue.FALSE
            invalidateAfter {
                input.dragDrop(from.first.toFloat(), from.second.toFloat(), to.first.toFloat(), to.second.toFloat())
            }
            LuaValue.TRUE
        })
        globals.set("pinch", varargs { args ->
            val cx = args.todouble(1).toFloat()
            val cy = args.todouble(2).toFloat()
            val from = args.todouble(3).toFloat()
            val to = args.todouble(4).toFloat()
            invalidateAfter { input.pinch(cx, cy, from, to) }
            LuaValue.TRUE
        })
        globals.set("tap", two { x, y ->
            invalidateAfter { input.tap(x.tofloat(), y.tofloat()) }
            LuaValue.TRUE
        })

        // ---- Ações do sistema -------------------------------------------
        globals.set("back", zero { LuaValue.valueOf(invalidateAfter { input.back() }) })
        globals.set("home", zero { LuaValue.valueOf(invalidateAfter { input.home() }) })
        globals.set("recents", zero { LuaValue.valueOf(invalidateAfter { input.recents() }) })
        globals.set("openApp", one { pkg ->
            val name = pkg.checkjstring()
            val intent = context.packageManager.getLaunchIntentForPackage(name)
            if (intent == null) {
                logger("WARN", "App não encontrado: $name")
                LuaValue.FALSE
            } else {
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                LuaValue.TRUE
            }
        })

        // ---- Tempo / fluxo ------------------------------------------------
        globals.set("sleep", one { seconds ->
            if (!controller.interruptibleSleep((seconds.todouble() * 1000).toLong())) throw ScriptStoppedException()
            LuaValue.NIL
        })
        globals.set("waitMs", one { ms ->
            if (!controller.interruptibleSleep(ms.tolong())) throw ScriptStoppedException()
            LuaValue.NIL
        })
        globals.set("waitMsRandom", two { min, max ->
            val v = Random.nextLong(min.tolong(), max.tolong().coerceAtLeast(min.tolong() + 1))
            if (!controller.interruptibleSleep(v)) throw ScriptStoppedException()
            LuaValue.NIL
        })
        globals.set("scriptExit", varargs { args ->
            val msg = if (args.narg() > 0) args.tojstring(1) else null
            throw ScriptExitException(msg)
        })
        globals.set("setStopMessage", one { msg ->
            controller.stopMessage = msg.tojstring()
            LuaValue.NIL
        })
        globals.set("shouldStop", zero { LuaValue.valueOf(controller.shouldStop()) })

        // ---- Saída / diagnóstico -------------------------------------------
        globals.set("toast", one { msg -> toaster(msg.tojstring()); LuaValue.NIL })
        globals.set("log", one { msg -> logger("INFO", msg.tojstring()); LuaValue.NIL })
        globals.set("logWarn", one { msg -> logger("WARN", msg.tojstring()); LuaValue.NIL })
        globals.set("logError", one { msg -> logger("ERROR", msg.tojstring()); LuaValue.NIL })
        globals.set("print", varargs { args ->
            val sb = StringBuilder()
            for (i in 1..args.narg()) {
                if (i > 1) sb.append('\t')
                sb.append(args.tojstring(i))
            }
            logger("INFO", sb.toString())
            LuaValue.NIL
        })

        // ---- Tela ----------------------------------------------------------
        globals.set("getScreenWidth", zero { LuaValue.valueOf(vision.screenSize().first) })
        globals.set("getScreenHeight", zero { LuaValue.valueOf(vision.screenSize().second) })
        globals.set("saveScreenshot", one { path ->
            val bmp = vision.currentFrame(fresh = true) ?: return@one LuaValue.FALSE
            LuaValue.valueOf(saveBitmap(bmp, path.tojstring()))
        })

        // ---- Construtores --------------------------------------------------
        globals.set("Region", varargs { args ->
            makeRegion(
                ScreenRegion(args.toint(1), args.toint(2), args.toint(3), args.toint(4)),
            )
        })
        globals.set("Location", two { x, y -> makeLocation(x.toint(), y.toint()) })
        globals.set("Pattern", one { src -> makePattern(Pattern(src.checkjstring(), defaultSimilarity)) })
        globals.set("getLastMatch", zero { lastMatch?.toLua() ?: LuaValue.NIL })

        globals.set("Settings", makeSettings())
        globals.set("heal", makeHealTable())
    }

    // ==================================================================
    // Implementações
    // ==================================================================

    private fun luaWait(args: Varargs): LuaValue {
        // wait(2) → dorme 2s. wait("img.png", 10) → espera a imagem.
        val first = args.arg(1)
        if (first.isnumber()) {
            if (!controller.interruptibleSleep((first.todouble() * 1000).toLong())) {
                throw ScriptStoppedException()
            }
            return LuaValue.NIL
        }
        val pattern = args.toPattern(0)
        val timeout = args.timeoutOr(1, autoWaitTimeout)
        val match = vision.waitFor(pattern, timeout, controller::shouldStop)
        checkStop()
        if (match == null) {
            throw FindFailedException(pattern.source, "wait: '${pattern.source}' não apareceu em ${timeout}s")
        }
        lastMatch = match
        return match.toLua()
    }

    private fun doClick(args: Varargs, required: Boolean, times: Int): LuaValue {
        val target = args.resolveTarget()
            ?: if (required) {
                val name = args.arg(1).tojstring()
                throw FindFailedException(name, "click: alvo '$name' não encontrado")
            } else {
                return LuaValue.FALSE
            }

        invalidateAfter {
            if (times >= 2) {
                input.doubleTap(target.first.toFloat(), target.second.toFloat())
            } else {
                input.tap(target.first.toFloat(), target.second.toFloat())
            }
        }
        return LuaValue.TRUE
    }

    private fun doSwipe(args: Varargs, human: Boolean): LuaValue {
        // swipe(x1,y1,x2,y2[,ms]) ou swipe(loc1, loc2[,ms])
        val (from, to, duration) = if (args.arg(1).isnumber() && args.narg() >= 4) {
            Triple(
                args.toint(1) to args.toint(2),
                args.toint(3) to args.toint(4),
                if (args.narg() >= 5) args.tolong(5) else 400L,
            )
        } else {
            val a = args.arg(1).toPointOrNull() ?: return LuaValue.FALSE
            val b = args.arg(2).toPointOrNull() ?: return LuaValue.FALSE
            Triple(a, b, if (args.narg() >= 3) args.tolong(3) else 400L)
        }

        val ok = invalidateAfter {
            if (human) {
                input.humanSwipe(from.first.toFloat(), from.second.toFloat(), to.first.toFloat(), to.second.toFloat(), duration)
            } else {
                input.swipe(from.first.toFloat(), from.second.toFloat(), to.first.toFloat(), to.second.toFloat(), duration)
            }
        }
        return LuaValue.valueOf(ok)
    }

    private fun findOrThrow(pattern: Pattern, timeout: Double): MatchResult {
        val match = findWithWait(pattern, timeout)
            ?: throw FindFailedException(pattern.source, "find: '${pattern.source}' não encontrado")
        return match
    }

    private fun findWithWait(pattern: Pattern, timeout: Double): MatchResult? {
        checkStop()
        val match = if (timeout <= 0.0) {
            vision.find(pattern)
        } else {
            vision.waitFor(pattern, timeout, controller::shouldStop)
        }
        checkStop()
        if (match != null) lastMatch = match
        return match
    }

    private fun performClick(match: MatchResult) {
        lastMatch = match
        invalidateAfter { input.tap(match.targetX.toFloat(), match.targetY.toFloat()) }
    }

    /** Depois de qualquer interação a tela muda: invalida o cache de frame. */
    private inline fun invalidateAfter(action: () -> Boolean): Boolean {
        val result = action()
        vision.invalidateFrame()
        if (clickDelayMs > 0) controller.interruptibleSleep(clickDelayMs)
        return result
    }

    private fun checkStop() {
        if (controller.shouldStop()) throw ScriptStoppedException()
        if (!controller.awaitIfPaused()) throw ScriptStoppedException()
    }

    // ==================================================================
    // Objetos Lua (Region, Location, Pattern, Match)
    // ==================================================================

    private fun makeRegion(region: ScreenRegion): LuaTable {
        val t = LuaTable()
        t.set("__type", "Region")
        t.set("x", region.x); t.set("y", region.y)
        t.set("w", region.w); t.set("h", region.h)

        t.set("getX", zero { LuaValue.valueOf(region.x) })
        t.set("getY", zero { LuaValue.valueOf(region.y) })
        t.set("getW", zero { LuaValue.valueOf(region.w) })
        t.set("getH", zero { LuaValue.valueOf(region.h) })
        t.set("getCenter", zero { makeLocation(region.centerX, region.centerY) })
        t.set("offset", two { dx, dy ->
            makeRegion(ScreenRegion(region.x + dx.toint(), region.y + dy.toint(), region.w, region.h))
        })

        // Métodos de busca restritos a esta região (self é arg 1 em `obj:m()`).
        t.set("exists", varargs { args ->
            val m = findWithWait(args.toPattern(1).inRegion(region), args.timeoutOr(2, 0.0))
            if (m == null) LuaValue.FALSE else m.toLua()
        })
        t.set("find", varargs { args -> findOrThrow(args.toPattern(1).inRegion(region), args.timeoutOr(2, 0.0)).toLua() })
        t.set("wait", varargs { args ->
            val p = args.toPattern(1).inRegion(region)
            val m = vision.waitFor(p, args.timeoutOr(2, autoWaitTimeout), controller::shouldStop)
            checkStop()
            if (m == null) throw FindFailedException(p.source, "Region:wait falhou para '${p.source}'")
            lastMatch = m
            m.toLua()
        })
        t.set("click", varargs { args ->
            if (args.narg() <= 1) {
                // region:click() → clica no centro (com jitter dentro da área)
                val rx = region.x + Random.nextInt(region.w.coerceAtLeast(1))
                val ry = region.y + Random.nextInt(region.h.coerceAtLeast(1))
                invalidateAfter { input.tap(rx.toFloat(), ry.toFloat()) }
                return@varargs LuaValue.TRUE
            }
            val m = findWithWait(args.toPattern(1).inRegion(region), args.timeoutOr(2, 0.0))
                ?: throw FindFailedException(args.arg(2).tojstring(), "Region:click não encontrou o alvo")
            performClick(m)
            LuaValue.TRUE
        })
        t.set("existsClick", varargs { args ->
            val m = findWithWait(args.toPattern(1).inRegion(region), args.timeoutOr(2, 0.0))
            if (m == null) {
                LuaValue.FALSE
            } else {
                performClick(m); LuaValue.TRUE
            }
        })
        t.set("findAll", varargs { args -> vision.findAll(args.toPattern(1).inRegion(region)).toLuaTable() })
        t.set("waitVanish", varargs { args ->
            val ok = vision.waitVanish(args.toPattern(1).inRegion(region), args.timeoutOr(2, autoWaitTimeout), controller::shouldStop)
            checkStop(); LuaValue.valueOf(ok)
        })
        t.set("readText", varargs {
            val screen = vision.currentFrame() ?: return@varargs LuaValue.valueOf("")
            val text = com.kaizen.auto.core.vision.OcrEngine.readAll(screen)
                .filter { region.containsCenter(it.region) }
                .joinToString("\n") { it.text }
            LuaValue.valueOf(text)
        })
        return t
    }

    private fun makeLocation(x: Int, y: Int): LuaTable {
        val t = LuaTable()
        t.set("__type", "Location")
        t.set("x", x); t.set("y", y)
        t.set("getX", zero { LuaValue.valueOf(x) })
        t.set("getY", zero { LuaValue.valueOf(y) })
        t.set("offset", two { dx, dy -> makeLocation(x + dx.toint(), y + dy.toint()) })
        t.set("click", zero {
            invalidateAfter { input.tap(x.toFloat(), y.toFloat()) }
            LuaValue.TRUE
        })
        return t
    }

    private fun makePattern(pattern: Pattern): LuaTable {
        val t = LuaTable()
        t.set("__type", "Pattern")
        t.set("source", pattern.source)
        t.set("similarity", pattern.similarity)
        t.set("offsetX", pattern.targetOffsetX)
        t.set("offsetY", pattern.targetOffsetY)
        t.set("grayscale", LuaValue.valueOf(pattern.grayscale))

        t.set("similar", two { _, v -> makePattern(pattern.similar(v.todouble())) })
        t.set("targetOffset", varargs { args ->
            makePattern(pattern.targetOffset(args.toint(2), args.toint(3)))
        })
        t.set("color", one { makePattern(pattern.color()) })
        t.set("gray", one { makePattern(pattern.gray()) })
        t.set("isColor", one { LuaValue.valueOf(!pattern.grayscale) })
        return t
    }

    private fun MatchResult.toLua(): LuaTable {
        val t = makeRegion(region)
        t.set("__type", "Match")
        t.set("score", score)
        t.set("scale", scale)
        t.set("strategy", strategy.name)
        t.set("targetX", targetX)
        t.set("targetY", targetY)
        t.set("getScore", zero { LuaValue.valueOf(score) })
        t.set("getTarget", zero { makeLocation(targetX, targetY) })
        t.set("save", two { _, path ->
            val screen = vision.currentFrame() ?: return@two LuaValue.FALSE
            val crop = runCatching {
                Bitmap.createBitmap(
                    screen,
                    region.x.coerceIn(0, screen.width - 1),
                    region.y.coerceIn(0, screen.height - 1),
                    region.w.coerceIn(1, screen.width - region.x),
                    region.h.coerceIn(1, screen.height - region.y),
                )
            }.getOrNull() ?: return@two LuaValue.FALSE
            LuaValue.valueOf(saveBitmap(crop, path.tojstring()))
        })
        return t
    }

    private fun List<MatchResult>.toLuaTable(): LuaTable {
        val t = LuaTable()
        forEachIndexed { index, m -> t.set(index + 1, m.toLua()) }
        return t
    }

    private fun makeSettings(): LuaTable {
        val t = LuaTable()
        t.set("setScriptDimension", varargs { args ->
            // Settings:setScriptDimension(true, 1280)
            val enabled = args.arg(2).toboolean()
            val value = args.toint(3)
            vision.scriptDimension = if (enabled) value else 0
            LuaValue.NIL
        })
        t.set("setCompareDimension", varargs { args ->
            val enabled = args.arg(2).toboolean()
            val value = args.toint(3)
            vision.compareDimension = if (enabled) value else 0
            LuaValue.NIL
        })
        t.set("setSimilarity", two { _, v -> defaultSimilarity = v.todouble(); LuaValue.NIL })
        t.set("setAutoWaitTimeout", two { _, v -> autoWaitTimeout = v.todouble(); LuaValue.NIL })
        t.set("setClickDelay", two { _, v -> clickDelayMs = (v.todouble() * 1000).toLong(); LuaValue.NIL })
        t.set("setHumanize", two { _, v -> input.humanize = v.toboolean(); LuaValue.NIL })
        t.set("setMaxRuntimeMinutes", two { _, v -> controller.maxRuntimeMinutes = v.toint(); LuaValue.NIL })
        return t
    }

    /** Tabela `heal` — controle do self-healing de dentro do script. */
    private fun makeHealTable(): LuaTable {
        val t = LuaTable()
        t.set("on", zero { healing.enabled = true; LuaValue.NIL })
        t.set("off", zero { healing.enabled = false; LuaValue.NIL })
        t.set("isOn", zero { LuaValue.valueOf(healing.enabled) })
        t.set("learn", zero { healing.learnVariants = true; LuaValue.NIL })
        t.set("noLearn", zero { healing.learnVariants = false; LuaValue.NIL })
        /** heal.remember("img.png") — força salvar o recorte atual como variante. */
        t.set("remember", one { key ->
            val m = lastMatch ?: return@one LuaValue.FALSE
            val screen = vision.currentFrame() ?: return@one LuaValue.FALSE
            healing.learnVariantFrom(key.tojstring(), screen, m.region)
            LuaValue.TRUE
        })
        return t
    }

    // ==================================================================
    // Helpers de conversão
    // ==================================================================

    /**
     * Converte o argumento na posição [index] (0-based) em Pattern.
     * Aceita string ("x.png"), tabela Pattern, ou tabela Match/Region.
     */
    private fun Varargs.toPattern(index: Int): Pattern {
        val v = arg(index + 1)
        return when {
            v.isstring() -> Pattern(v.tojstring(), defaultSimilarity)
            v.istable() -> {
                val type = v.get("__type").optjstring("")
                if (type == "Pattern") {
                    Pattern(
                        source = v.get("source").tojstring(),
                        similarity = v.get("similarity").optdouble(defaultSimilarity),
                        targetOffsetX = v.get("offsetX").optint(0),
                        targetOffsetY = v.get("offsetY").optint(0),
                        grayscale = v.get("grayscale").optboolean(true),
                    )
                } else {
                    throw LuaError("Esperava imagem ou Pattern, recebi $type")
                }
            }
            else -> throw LuaError("Argumento inválido: esperava nome de imagem ou Pattern")
        }
    }

    private fun Varargs.timeoutOr(index: Int, default: Double): Double {
        val v = arg(index + 1)
        return if (v.isnumber()) v.todouble() else default
    }

    /** Resolve o argumento 1 em coordenada de clique (busca a imagem se preciso). */
    private fun Varargs.resolveTarget(): Pair<Int, Int>? {
        val v = arg(1)
        v.toPointOrNull()?.let { return it }
        // É uma imagem/Pattern: precisa procurar.
        val pattern = toPattern(0)
        val timeout = timeoutOr(1, 0.0)
        val match = findWithWait(pattern, timeout) ?: return null
        return match.targetX to match.targetY
    }

    private fun LuaValue.toPointOrNull(): Pair<Int, Int>? {
        if (!istable()) return null
        return when (get("__type").optjstring("")) {
            "Location" -> get("x").toint() to get("y").toint()
            "Match" -> get("targetX").toint() to get("targetY").toint()
            "Region" -> {
                val x = get("x").toint(); val y = get("y").toint()
                val w = get("w").toint(); val h = get("h").toint()
                (x + w / 2) to (y + h / 2)
            }
            else -> null
        }
    }

    private fun LuaValue.toRegionOrNull(): ScreenRegion? {
        if (!istable()) return null
        val type = get("__type").optjstring("")
        if (type != "Region" && type != "Match") return null
        return ScreenRegion(get("x").toint(), get("y").toint(), get("w").toint(), get("h").toint())
    }

    private fun ScreenRegion.containsCenter(other: ScreenRegion): Boolean =
        contains(other.centerX, other.centerY)

    private fun saveBitmap(bmp: Bitmap, path: String): Boolean = try {
        val file = if (path.startsWith("/")) java.io.File(path)
        else java.io.File(vision.imageBaseDir ?: context.filesDir, path)
        file.parentFile?.mkdirs()
        java.io.FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        true
    } catch (t: Throwable) {
        Log.w("LuaApi", "saveBitmap falhou: ${t.message}")
        false
    }

    // ---- Açúcar sintático para criar funções Lua ----------------------

    private inline fun zero(crossinline body: () -> LuaValue) = object : ZeroArgFunction() {
        override fun call(): LuaValue = body()
    }

    private inline fun one(crossinline body: (LuaValue) -> LuaValue) = object : OneArgFunction() {
        override fun call(arg: LuaValue): LuaValue = body(arg)
    }

    private inline fun two(crossinline body: (LuaValue, LuaValue) -> LuaValue) = object : TwoArgFunction() {
        override fun call(a: LuaValue, b: LuaValue): LuaValue = body(a, b)
    }

    private inline fun three(crossinline body: (LuaValue, LuaValue, LuaValue) -> LuaValue) = object : ThreeArgFunction() {
        override fun call(a: LuaValue, b: LuaValue, c: LuaValue): LuaValue = body(a, b, c)
    }

    private inline fun varargs(crossinline body: (Varargs) -> LuaValue) = object : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs = body(args)
    }
}
