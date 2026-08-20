package com.kaizen.auto.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kaizen.auto.KaizenApp
import com.kaizen.auto.data.ScriptEntry
import com.kaizen.auto.data.ScriptRepository
import com.kaizen.auto.data.db.HealingEvent
import com.kaizen.auto.data.db.PatternMemory
import com.kaizen.auto.data.db.RunLog
import com.kaizen.auto.healing.PassiveObserver
import com.kaizen.auto.runtime.RunState
import com.kaizen.auto.service.AutomationService
import com.kaizen.auto.service.KaizenAccessibilityService
import com.kaizen.auto.service.ProjectionRequestActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

/** Estado das permissões/pré-requisitos que o app precisa para funcionar. */
data class ReadinessState(
    val accessibilityOn: Boolean = false,
    val captureOn: Boolean = false,
    val overlayOn: Boolean = false,
) {
    val canRun: Boolean get() = accessibilityOn && captureOn
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val kaizen = app as KaizenApp
    val repository: ScriptRepository = kaizen.repository

    val scripts: StateFlow<List<ScriptEntry>> = repository.scripts
    val runState: StateFlow<RunState> = kaizen.scriptController.state
    val currentScript: StateFlow<String?> = kaizen.scriptController.currentScript

    private val _readiness = MutableStateFlow(ReadinessState())
    val readiness: StateFlow<ReadinessState> = _readiness

    private val _selected = MutableStateFlow<ScriptEntry?>(null)
    val selected: StateFlow<ScriptEntry?> = _selected

    private val _editorText = MutableStateFlow("")
    val editorText: StateFlow<String> = _editorText

    private val _images = MutableStateFlow<List<File>>(emptyList())
    val images: StateFlow<List<File>> = _images

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    val logs: StateFlow<List<RunLog>> = kaizen.database.runLogDao()
        .observeRecent(400)
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val healingEvents: StateFlow<List<HealingEvent>> = kaizen.database.healingEventDao()
        .observeRecent(200)
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val patternMemory: StateFlow<List<PatternMemory>> = kaizen.database.patternMemoryDao()
        .observeAll()
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ------------------------------------------------------------------
    // Configurações do motor (espelham o HealingEngine / InputController)
    // ------------------------------------------------------------------

    private val _healingEnabled = MutableStateFlow(kaizen.healingEngine.enabled)
    val healingEnabled: StateFlow<Boolean> = _healingEnabled

    private val _learnVariants = MutableStateFlow(kaizen.healingEngine.learnVariants)
    val learnVariants: StateFlow<Boolean> = _learnVariants

    private val _passiveObserver = MutableStateFlow(PassiveObserver.enabled)
    val passiveObserver: StateFlow<Boolean> = _passiveObserver

    private val _humanize = MutableStateFlow(kaizen.inputController.humanize)
    val humanize: StateFlow<Boolean> = _humanize

    private val _maxRuntime = MutableStateFlow(kaizen.scriptController.maxRuntimeMinutes)
    val maxRuntime: StateFlow<Int> = _maxRuntime

    fun setHealing(value: Boolean) {
        kaizen.healingEngine.enabled = value
        _healingEnabled.value = value
    }

    fun setLearnVariants(value: Boolean) {
        kaizen.healingEngine.learnVariants = value
        _learnVariants.value = value
    }

    fun setPassiveObserver(value: Boolean) {
        PassiveObserver.enabled = value
        _passiveObserver.value = value
    }

    fun setHumanize(value: Boolean) {
        kaizen.inputController.humanize = value
        _humanize.value = value
    }

    fun setMaxRuntime(minutes: Int) {
        kaizen.scriptController.maxRuntimeMinutes = minutes.coerceIn(0, 720)
        _maxRuntime.value = kaizen.scriptController.maxRuntimeMinutes
    }

    // ------------------------------------------------------------------
    // Ciclo de vida
    // ------------------------------------------------------------------

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            repository.refresh()
            refreshReadiness()
        }
    }

    fun refreshReadiness() {
        val ctx = getApplication<Application>()
        _readiness.value = ReadinessState(
            accessibilityOn = KaizenAccessibilityService.isEnabled(ctx),
            captureOn = kaizen.captureManager.hasVision(),
            overlayOn = Settings.canDrawOverlays(ctx),
        )
    }

    // ------------------------------------------------------------------
    // Scripts
    // ------------------------------------------------------------------

    fun select(entry: ScriptEntry?) {
        _selected.value = entry
        if (entry == null) {
            _editorText.value = ""
            _images.value = emptyList()
            return
        }
        viewModelScope.launch {
            _editorText.value = repository.read(entry)
            _images.value = repository.listImages(entry)
        }
    }

    fun onEditorChange(text: String) {
        _editorText.value = text
    }

    fun createScript(name: String) {
        viewModelScope.launch {
            val created = repository.create(name)
            if (created == null) {
                notify("Já existe um script com esse nome.")
            } else {
                notify("Script '${created.name}' criado.")
                select(created)
            }
        }
    }

    fun saveCurrent(onDone: () -> Unit = {}) {
        val entry = _selected.value ?: return
        viewModelScope.launch {
            val ok = repository.save(entry, _editorText.value)
            notify(if (ok) "Salvo." else "Não consegui salvar.")
            onDone()
        }
    }

    fun delete(entry: ScriptEntry) {
        viewModelScope.launch {
            repository.delete(entry)
            if (_selected.value?.name == entry.name) select(null)
            notify("Script '${entry.name}' apagado.")
        }
    }

    fun duplicate(entry: ScriptEntry) {
        viewModelScope.launch {
            repository.duplicate(entry)
            notify("Cópia criada.")
        }
    }

    fun rename(entry: ScriptEntry, newName: String) {
        viewModelScope.launch {
            val ok = repository.rename(entry, newName)
            notify(if (ok) "Renomeado." else "Nome inválido ou já usado.")
            if (ok) select(repository.scripts.value.firstOrNull { it.name == newName })
        }
    }

    fun importImage(uri: Uri, fileName: String?) {
        val entry = _selected.value ?: return
        viewModelScope.launch {
            val file = repository.importImage(entry, uri, fileName)
            _images.value = repository.listImages(entry)
            notify(if (file != null) "Imagem '${file.name}' adicionada." else "Falha ao importar.")
            repository.refresh()
        }
    }

    fun deleteImage(file: File) {
        val entry = _selected.value ?: return
        repository.deleteImage(file)
        _images.value = repository.listImages(entry)
        notify("Imagem removida.")
    }

    // ------------------------------------------------------------------
    // Execução
    // ------------------------------------------------------------------

    /**
     * Start. Se ainda não temos captura de tela, pedimos a permissão antes —
     * a activity transparente devolve o token direto ao serviço.
     */
    fun run(entry: ScriptEntry) {
        val ctx = getApplication<Application>()
        if (!KaizenAccessibilityService.isEnabled(ctx)) {
            notify("Ative a Acessibilidade do KaizenAuto primeiro.")
            return
        }
        if (kaizen.scriptRunner.isRunning()) {
            notify("Já tem script rodando. Pare antes.")
            return
        }
        if (kaizen.captureManager.hasVision()) {
            AutomationService.start(ctx, entry.file.absolutePath, entry.name)
        } else {
            ProjectionRequestActivity.request(ctx, entry.file.absolutePath, entry.name)
        }
    }

    fun stop() {
        AutomationService.stop(getApplication())
    }

    fun pause() {
        getApplication<Application>().startService(
            Intent(getApplication(), AutomationService::class.java)
                .setAction(AutomationService.ACTION_PAUSE),
        )
    }

    fun resumeRun() {
        getApplication<Application>().startService(
            Intent(getApplication(), AutomationService::class.java)
                .setAction(AutomationService.ACTION_RESUME),
        )
    }

    /** Só liga a captura, sem rodar nada — útil para testar as permissões. */
    fun prepareCapture() {
        ProjectionRequestActivity.request(getApplication(), null, "preparo")
    }

    fun openAccessibilitySettings() {
        KaizenAccessibilityService.openSettings(getApplication())
    }

    fun openOverlaySettings() {
        val ctx = getApplication<Application>()
        ctx.startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${ctx.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    // ------------------------------------------------------------------
    // Manutenção
    // ------------------------------------------------------------------

    fun clearLogs() {
        viewModelScope.launch(Dispatchers.IO) { kaizen.database.runLogDao().clear() }
    }

    fun clearLearning() {
        viewModelScope.launch(Dispatchers.IO) {
            kaizen.database.patternMemoryDao().clear()
            kaizen.database.healingEventDao().clear()
            kaizen.database.screenObservationDao().clear()
            PassiveObserver.clearMemory()
        }
        notify("Memória do bot zerada.")
    }

    fun notify(text: String) {
        _message.value = text
    }

    fun consumeMessage() {
        _message.value = null
    }
}
