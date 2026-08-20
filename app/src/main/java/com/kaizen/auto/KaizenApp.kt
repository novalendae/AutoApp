package com.kaizen.auto

import android.app.Application
import android.util.Log
import android.widget.Toast
import com.kaizen.auto.core.capture.CaptureManager
import com.kaizen.auto.core.input.InputController
import com.kaizen.auto.core.vision.VisionEngine
import com.kaizen.auto.data.ScriptRepository
import com.kaizen.auto.data.db.KaizenDatabase
import com.kaizen.auto.data.db.RunLog
import com.kaizen.auto.healing.HealingEngine
import com.kaizen.auto.healing.PassiveObserver
import com.kaizen.auto.runtime.ScriptController
import com.kaizen.auto.runtime.ScriptRunner
import com.kaizen.auto.service.KaizenAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.opencv.android.OpenCVLoader

/**
 * Container de dependências. O app é pequeno o bastante para não precisar de
 * Hilt/Koin — um Application com lazies resolve, inicializa mais rápido e
 * mantém tudo explícito.
 */
class KaizenApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: KaizenDatabase by lazy { KaizenDatabase.get(this) }
    val repository: ScriptRepository by lazy { ScriptRepository(this) }
    val captureManager: CaptureManager by lazy { CaptureManager(this) }
    val inputController: InputController by lazy {
        InputController { KaizenAccessibilityService.instance }
    }
    val healingEngine: HealingEngine by lazy { HealingEngine(this, database) }
    val visionEngine: VisionEngine by lazy { VisionEngine(captureManager, healingEngine) }
    val scriptController: ScriptController by lazy { ScriptController() }

    val scriptRunner: ScriptRunner by lazy {
        ScriptRunner(
            context = this,
            vision = visionEngine,
            input = inputController,
            healing = healingEngine,
            controller = scriptController,
        ).apply {
            onLog = { level, message -> persistLog(level, message) }
            onToast = { message ->
                appScope.launch(Dispatchers.Main) {
                    Toast.makeText(this@KaizenApp, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        // OpenCV empacotado (org.opencv:opencv) inicializa sem o Manager externo.
        if (OpenCVLoader.initLocal()) {
            Log.i(TAG, "OpenCV carregado.")
        } else {
            Log.e(TAG, "Falha ao carregar OpenCV — o casamento de imagens não vai funcionar.")
        }

        PassiveObserver.attach(database)

        appScope.launch {
            repository.installSamplesIfNeeded()
            repository.refresh()
            // Higiene: logs e eventos com mais de 7 dias vão embora.
            val cutoff = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
            runCatching {
                database.runLogDao().purgeOlderThan(cutoff)
                database.healingEventDao().purgeOlderThan(cutoff)
            }
        }
    }

    private fun persistLog(level: String, message: String) {
        appScope.launch {
            runCatching {
                database.runLogDao().insert(
                    RunLog(
                        scriptName = scriptController.currentScript.value ?: "-",
                        level = level,
                        message = message,
                    ),
                )
            }
        }
    }

    private companion object {
        const val TAG = "KaizenApp"
    }
}
