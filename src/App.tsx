import React from 'react';
import { ScriptEntry, ReadinessState, RunState, PatternMemory, HealingEvent, RunLog, AppSettings, ScriptImage, ScreenRegion } from './types';
import { storage } from './data/storage';
import { VisionEngine } from './runtime/visionEngine';
import { HealingEngine } from './runtime/healingEngine';
import { ScriptRunner } from './runtime/luaRunner';
import { DEFAULT_TEMPLATE } from './data/sampleScripts';
import { Navigation, TabType } from './components/Navigation';
import { ScriptsScreen } from './screens/ScriptsScreen';
import { EditorScreen } from './screens/EditorScreen';
import { LogsScreen } from './screens/LogsScreen';
import { LearningScreen } from './screens/LearningScreen';
import { HelpScreen } from './screens/HelpScreen';
import { SettingsScreen } from './screens/SettingsScreen';
import { VirtualDeviceModal } from './components/VirtualDeviceModal';

export const App: React.FC = () => {
  const [currentTab, setCurrentTab] = React.useState<TabType>('scripts');
  const [scripts, setScripts] = React.useState<ScriptEntry[]>(() => storage.getScripts());
  const [currentEditorScript, setCurrentEditorScript] = React.useState<ScriptEntry | null>(
    () => scripts[0] || null
  );
  const [patternMemory, setPatternMemory] = React.useState<PatternMemory[]>(() => storage.getPatternMemory());
  const [healingEvents, setHealingEvents] = React.useState<HealingEvent[]>(() => storage.getHealingEvents());
  const [runLogs, setRunLogs] = React.useState<RunLog[]>(() => storage.getRunLogs());
  const [readiness, setReadiness] = React.useState<ReadinessState>(() => storage.getReadiness());
  const [settings, setSettings] = React.useState<AppSettings>(() => storage.getSettings());
  const [runState, setRunState] = React.useState<RunState>('IDLE');
  const [runningScript, setRunningScript] = React.useState<string | null>(null);
  const [toastMessage, setToastMessage] = React.useState<string | null>(null);
  const [isVirtualDeviceOpen, setIsVirtualDeviceOpen] = React.useState(false);
  const [lastHighlight, setLastHighlight] = React.useState<{ region: ScreenRegion; type: 'tap' | 'find' | 'swipe'; timestamp: number } | null>(null);

  // Vision, Healing, Runner singletons
  const visionRef = React.useRef<VisionEngine>(new VisionEngine());
  const healingRef = React.useRef<HealingEngine>(new HealingEngine(visionRef.current));
  const runnerRef = React.useRef<ScriptRunner | null>(null);

  const showToast = React.useCallback((msg: string) => {
    setToastMessage(msg);
    setTimeout(() => {
      setToastMessage((current) => (current === msg ? null : current));
    }, 3500);
  }, []);

  const addLog = React.useCallback((level: 'INFO' | 'WARN' | 'ERROR' | 'HEAL', message: string) => {
    const newLog: RunLog = {
      id: Date.now() + Math.random(),
      scriptName: runningScript || '',
      level,
      message,
      createdAt: Date.now(),
    };
    setRunLogs((prev) => {
      const updated = [newLog, ...prev].slice(0, 400);
      storage.saveRunLogs(updated);
      return updated;
    });
  }, [runningScript]);

  // Initialize runner
  React.useEffect(() => {
    healingRef.current.enabled = settings.healingEnabled;
    healingRef.current.learnVariants = settings.learnVariants;
    healingRef.current.onLog = (level, msg) => addLog(level, msg);

    runnerRef.current = new ScriptRunner(visionRef.current, healingRef.current, {
      onStateChange: (state, runningName) => {
        setRunState(state);
        setRunningScript(runningName);
        setPatternMemory(storage.getPatternMemory());
        setHealingEvents(storage.getHealingEvents());
      },
      onLog: (level, msg) => addLog(level, msg),
      onToast: (msg) => showToast(msg),
      onHighlight: (region, type) => {
        setLastHighlight({ region, type, timestamp: Date.now() });
      },
    });
  }, [addLog, showToast, settings]);

  const handleUpdateReadiness = (updated: ReadinessState) => {
    setReadiness(updated);
    storage.saveReadiness(updated);
  };

  const handleUpdateSettings = (updated: AppSettings) => {
    setSettings(updated);
    storage.saveSettings(updated);
    if (healingRef.current) {
      healingRef.current.enabled = updated.healingEnabled;
      healingRef.current.learnVariants = updated.learnVariants;
    }
  };

  const handleRunScript = (script: ScriptEntry) => {
    if (!readiness.accessibilityOn || !readiness.captureOn) {
      showToast('⚠️ Ative as permissões de Acessibilidade e Captura antes de executar');
      return;
    }
    runnerRef.current?.run(script);
  };

  const handlePauseScript = () => {
    runnerRef.current?.pause();
  };

  const handleResumeScript = () => {
    runnerRef.current?.resume();
  };

  const handleStopScript = () => {
    runnerRef.current?.stop();
  };

  const handleEditScript = (script: ScriptEntry) => {
    setCurrentEditorScript(script);
    setCurrentTab('editor');
  };

  const handleCreateScript = (name: string) => {
    const cleanName = name.replace(/\s+/g, '_').toLowerCase();
    if (scripts.some((s) => s.name === cleanName)) {
      showToast(`Já existe um script com o nome '${cleanName}'`);
      return;
    }
    const newScript: ScriptEntry = {
      name: cleanName,
      lastModified: Date.now(),
      sizeBytes: DEFAULT_TEMPLATE.length,
      imageCount: 0,
      code: DEFAULT_TEMPLATE.replace('{{NAME}}', cleanName),
      images: [],
    };
    const updated = [newScript, ...scripts];
    setScripts(updated);
    storage.saveScripts(updated);
    setCurrentEditorScript(newScript);
    setCurrentTab('editor');
    showToast(`Script '${cleanName}' criado!`);
  };

  const handleRenameScript = (oldName: string, newName: string) => {
    const cleanName = newName.replace(/\s+/g, '_').toLowerCase();
    if (scripts.some((s) => s.name === cleanName && s.name !== oldName)) {
      showToast(`Já existe um script com o nome '${cleanName}'`);
      return;
    }
    const updated = scripts.map((s) =>
      s.name === oldName ? { ...s, name: cleanName, lastModified: Date.now() } : s
    );
    setScripts(updated);
    storage.saveScripts(updated);
    if (currentEditorScript?.name === oldName) {
      setCurrentEditorScript({ ...currentEditorScript, name: cleanName });
    }
    showToast(`Script renomeado para '${cleanName}'`);
  };

  const handleDuplicateScript = (script: ScriptEntry) => {
    const duplicateName = `${script.name}_copia`;
    const newScript: ScriptEntry = {
      ...script,
      name: duplicateName,
      lastModified: Date.now(),
      images: [...(script.images || [])],
    };
    const updated = [newScript, ...scripts];
    setScripts(updated);
    storage.saveScripts(updated);
    showToast(`Script '${duplicateName}' duplicado!`);
  };

  const handleDeleteScript = (name: string) => {
    const updated = scripts.filter((s) => s.name !== name);
    setScripts(updated);
    storage.saveScripts(updated);
    if (currentEditorScript?.name === name) {
      setCurrentEditorScript(updated[0] || null);
    }
    showToast(`Script '${name}' apagado.`);
  };

  const handleSaveScript = (name: string, code: string) => {
    const updated = scripts.map((s) =>
      s.name === name
        ? {
            ...s,
            code,
            lastModified: Date.now(),
            sizeBytes: new Blob([code]).size,
          }
        : s
    );
    setScripts(updated);
    storage.saveScripts(updated);
    if (currentEditorScript?.name === name) {
      setCurrentEditorScript({ ...currentEditorScript, code });
    }
    showToast(`Script '${name}' salvo!`);
  };

  const handleSaveAndRun = (name: string, code: string) => {
    handleSaveScript(name, code);
    const target = scripts.find((s) => s.name === name) || {
      name,
      code,
      lastModified: Date.now(),
      sizeBytes: new Blob([code]).size,
      imageCount: 0,
      images: [],
    };
    handleRunScript({ ...target, code });
  };

  const handleAddImage = (scriptName: string, image: ScriptImage) => {
    const updated = scripts.map((s) => {
      if (s.name === scriptName) {
        const existingImgs = s.images?.filter((img) => img.name !== image.name) || [];
        return {
          ...s,
          images: [image, ...existingImgs],
          imageCount: existingImgs.length + 1,
          lastModified: Date.now(),
        };
      }
      return s;
    });
    setScripts(updated);
    storage.saveScripts(updated);
    if (currentEditorScript?.name === scriptName) {
      const currentImgs = currentEditorScript.images?.filter((i) => i.name !== image.name) || [];
      setCurrentEditorScript({
        ...currentEditorScript,
        images: [image, ...currentImgs],
        imageCount: currentImgs.length + 1,
      });
    }
    showToast(`Imagem '${image.name}' adicionada!`);
  };

  const handleDeleteImage = (scriptName: string, imageName: string) => {
    const updated = scripts.map((s) => {
      if (s.name === scriptName) {
        const existingImgs = s.images?.filter((img) => img.name !== imageName) || [];
        return {
          ...s,
          images: existingImgs,
          imageCount: existingImgs.length,
          lastModified: Date.now(),
        };
      }
      return s;
    });
    setScripts(updated);
    storage.saveScripts(updated);
    if (currentEditorScript?.name === scriptName) {
      const currentImgs = currentEditorScript.images?.filter((i) => i.name !== imageName) || [];
      setCurrentEditorScript({
        ...currentEditorScript,
        images: currentImgs,
        imageCount: currentImgs.length,
      });
    }
    showToast(`Imagem '${imageName}' removida.`);
  };

  const handleClearLogs = () => {
    setRunLogs([]);
    storage.saveRunLogs([]);
    showToast('Logs limpos!');
  };

  const handleClearBotMemory = () => {
    setPatternMemory([]);
    setHealingEvents([]);
    storage.savePatternMemory([]);
    storage.saveHealingEvents([]);
    showToast('Memória do bot resetada.');
  };

  return (
    <div className="min-h-screen bg-[#141218] text-[#E6E1E5] flex flex-col antialiased select-none">
      {/* Top Header & Navigation */}
      <Navigation
        currentTab={currentTab}
        onSelectTab={setCurrentTab}
        runState={runState}
        runningScript={runningScript}
        onOpenVirtualDevice={() => setIsVirtualDeviceOpen(true)}
        onPause={handlePauseScript}
        onResume={handleResumeScript}
        onStop={handleStopScript}
      />

      {/* Main Screen Views */}
      <main className="flex-1 overflow-y-auto">
        {currentTab === 'scripts' && (
          <ScriptsScreen
            scripts={scripts}
            readiness={readiness}
            runState={runState}
            runningScript={runningScript}
            onUpdateReadiness={handleUpdateReadiness}
            onRunScript={handleRunScript}
            onPauseScript={handlePauseScript}
            onResumeScript={handleResumeScript}
            onStopScript={handleStopScript}
            onEditScript={handleEditScript}
            onCreateScript={handleCreateScript}
            onRenameScript={handleRenameScript}
            onDuplicateScript={handleDuplicateScript}
            onDeleteScript={handleDeleteScript}
          />
        )}

        {currentTab === 'editor' && (
          <EditorScreen
            scripts={scripts}
            currentScript={currentEditorScript}
            runState={runState}
            onSelectScript={setCurrentEditorScript}
            onSaveScript={handleSaveScript}
            onSaveAndRun={handleSaveAndRun}
            onAddImage={handleAddImage}
            onDeleteImage={handleDeleteImage}
          />
        )}

        {currentTab === 'logs' && (
          <LogsScreen logs={runLogs} onClearLogs={handleClearLogs} />
        )}

        {currentTab === 'bot' && (
          <LearningScreen
            patterns={patternMemory}
            events={healingEvents}
            onClearAll={handleClearBotMemory}
          />
        )}

        {currentTab === 'help' && <HelpScreen />}

        {currentTab === 'settings' && (
          <SettingsScreen
            settings={settings}
            readiness={readiness}
            onUpdateSettings={handleUpdateSettings}
            onUpdateReadiness={handleUpdateReadiness}
          />
        )}
      </main>

      {/* Interactive Virtual Device / Simulation Canvas Modal */}
      <VirtualDeviceModal
        isOpen={isVirtualDeviceOpen}
        onClose={() => setIsVirtualDeviceOpen(false)}
        vision={visionRef.current}
        runState={runState}
        runningScript={runningScript || currentEditorScript?.name || null}
        onRun={() => currentEditorScript && handleRunScript(currentEditorScript)}
        onPause={handlePauseScript}
        onResume={handleResumeScript}
        onStop={handleStopScript}
        lastHighlight={lastHighlight}
      />

      {/* Floating Android-style Toast notification */}
      {toastMessage && (
        <div className="fixed bottom-20 left-1/2 -translate-x-1/2 z-50 bg-[#322F35] text-[#F4EFF4] px-5 py-2.5 rounded-full shadow-2xl border border-white/10 text-xs sm:text-sm font-medium animate-fade-in flex items-center gap-2 max-w-[90vw] text-center">
          <span>{toastMessage}</span>
        </div>
      )}
    </div>
  );
};
