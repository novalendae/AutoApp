import { ScriptEntry, PatternMemory, HealingEvent, RunLog, AppSettings, ReadinessState } from '../types';
import { INITIAL_SCRIPTS } from './sampleScripts';

const STORAGE_KEYS = {
  SCRIPTS: 'kaizen_scripts',
  PATTERN_MEMORY: 'kaizen_pattern_memory',
  HEALING_EVENTS: 'kaizen_healing_events',
  RUN_LOGS: 'kaizen_run_logs',
  SETTINGS: 'kaizen_settings',
  READINESS: 'kaizen_readiness',
};

const DEFAULT_SETTINGS: AppSettings = {
  healingEnabled: true,
  learnVariants: true,
  passiveObserver: true,
  humanize: true,
  maxRuntimeMinutes: 30,
};

const DEFAULT_READINESS: ReadinessState = {
  accessibilityOn: true,
  captureOn: true,
  overlayOn: false,
};

const INITIAL_PATTERN_MEMORY: PatternMemory[] = [
  {
    id: 1,
    patternKey: 'botao_jogar.png',
    successCount: 24,
    failureCount: 2,
    lastX: 470,
    lastY: 1320,
    lastW: 140,
    lastH: 50,
    avgScore: 0.94,
    minSuccessScore: 0.82,
    avgScale: 1.0,
    thresholdDelta: -0.05,
    learnedVariantPath: 'learned_variants/botao_jogar_var1.png',
    preferredStrategy: 'TEMPLATE',
    updatedAt: Date.now() - 1000 * 60 * 15,
    reliability: 24 / 26,
  },
  {
    id: 2,
    patternKey: 'fechar.png',
    successCount: 18,
    failureCount: 1,
    lastX: 980,
    lastY: 140,
    lastW: 44,
    lastH: 44,
    avgScore: 0.89,
    minSuccessScore: 0.78,
    avgScale: 0.95,
    thresholdDelta: 0.0,
    learnedVariantPath: null,
    preferredStrategy: 'TEMPLATE',
    updatedAt: Date.now() - 1000 * 60 * 45,
    reliability: 18 / 19,
  },
  {
    id: 3,
    patternKey: 'iniciar_batalha.png',
    successCount: 12,
    failureCount: 3,
    lastX: 460,
    lastY: 1100,
    lastW: 160,
    lastH: 56,
    avgScore: 0.86,
    minSuccessScore: 0.75,
    avgScale: 1.05,
    thresholdDelta: -0.08,
    learnedVariantPath: 'learned_variants/iniciar_batalha_recorte.png',
    preferredStrategy: 'ORB',
    updatedAt: Date.now() - 1000 * 60 * 120,
    reliability: 12 / 15,
  },
  {
    id: 4,
    patternKey: 'Configurações',
    successCount: 15,
    failureCount: 0,
    lastX: 200,
    lastY: 450,
    lastW: 240,
    lastH: 40,
    avgScore: 0.98,
    minSuccessScore: 0.95,
    avgScale: 1.0,
    thresholdDelta: 0.0,
    learnedVariantPath: null,
    preferredStrategy: 'OCR',
    updatedAt: Date.now() - 1000 * 60 * 180,
    reliability: 1.0,
  }
];

const INITIAL_HEALING_EVENTS: HealingEvent[] = [
  {
    id: 1,
    patternKey: 'iniciar_batalha.png',
    scriptName: 'coleta_diaria',
    tactic: 'ORB_FEATURES',
    succeeded: true,
    scoreBefore: 0.62,
    scoreAfter: 0.88,
    details: 'Limiar ajustado para 0.72; estratégia features ORB',
    createdAt: Date.now() - 1000 * 60 * 30,
  },
  {
    id: 2,
    patternKey: 'botao_jogar.png',
    scriptName: 'exemplo_basico',
    tactic: 'RELAX_THRESHOLD',
    succeeded: true,
    scoreBefore: 0.74,
    scoreAfter: 0.85,
    details: 'Limiar relaxado em -0.05 com sucesso na região memorizada',
    createdAt: Date.now() - 1000 * 60 * 75,
  },
  {
    id: 3,
    patternKey: 'icone_antigo.png',
    scriptName: 'teste_antigo',
    tactic: 'GIVE_UP',
    succeeded: false,
    scoreBefore: 0.80,
    scoreAfter: 0.0,
    details: 'Todas as táticas falharam: RELAX_THRESHOLD, LEARNED_VARIANT, WIDE_MULTISCALE, ORB, OCR, A11Y',
    createdAt: Date.now() - 1000 * 60 * 240,
  }
];

export const storage = {
  getScripts(): ScriptEntry[] {
    try {
      const data = localStorage.getItem(STORAGE_KEYS.SCRIPTS);
      if (!data) {
        localStorage.setItem(STORAGE_KEYS.SCRIPTS, JSON.stringify(INITIAL_SCRIPTS));
        return INITIAL_SCRIPTS;
      }
      return JSON.parse(data);
    } catch {
      return INITIAL_SCRIPTS;
    }
  },

  saveScripts(scripts: ScriptEntry[]) {
    try {
      localStorage.setItem(STORAGE_KEYS.SCRIPTS, JSON.stringify(scripts));
    } catch (e) {
      console.error('Failed to save scripts to localStorage', e);
    }
  },

  getPatternMemory(): PatternMemory[] {
    try {
      const data = localStorage.getItem(STORAGE_KEYS.PATTERN_MEMORY);
      if (!data) {
        localStorage.setItem(STORAGE_KEYS.PATTERN_MEMORY, JSON.stringify(INITIAL_PATTERN_MEMORY));
        return INITIAL_PATTERN_MEMORY;
      }
      return JSON.parse(data);
    } catch {
      return INITIAL_PATTERN_MEMORY;
    }
  },

  savePatternMemory(memory: PatternMemory[]) {
    try {
      localStorage.setItem(STORAGE_KEYS.PATTERN_MEMORY, JSON.stringify(memory));
    } catch (e) {
      console.error('Failed to save pattern memory', e);
    }
  },

  getHealingEvents(): HealingEvent[] {
    try {
      const data = localStorage.getItem(STORAGE_KEYS.HEALING_EVENTS);
      if (!data) {
        localStorage.setItem(STORAGE_KEYS.HEALING_EVENTS, JSON.stringify(INITIAL_HEALING_EVENTS));
        return INITIAL_HEALING_EVENTS;
      }
      return JSON.parse(data);
    } catch {
      return INITIAL_HEALING_EVENTS;
    }
  },

  saveHealingEvents(events: HealingEvent[]) {
    try {
      localStorage.setItem(STORAGE_KEYS.HEALING_EVENTS, JSON.stringify(events));
    } catch (e) {
      console.error('Failed to save healing events', e);
    }
  },

  getRunLogs(): RunLog[] {
    try {
      const data = localStorage.getItem(STORAGE_KEYS.RUN_LOGS);
      if (!data) return [];
      return JSON.parse(data);
    } catch {
      return [];
    }
  },

  saveRunLogs(logs: RunLog[]) {
    try {
      localStorage.setItem(STORAGE_KEYS.RUN_LOGS, JSON.stringify(logs.slice(0, 400)));
    } catch (e) {
      console.error('Failed to save run logs', e);
    }
  },

  getSettings(): AppSettings {
    try {
      const data = localStorage.getItem(STORAGE_KEYS.SETTINGS);
      if (!data) return DEFAULT_SETTINGS;
      return { ...DEFAULT_SETTINGS, ...JSON.parse(data) };
    } catch {
      return DEFAULT_SETTINGS;
    }
  },

  saveSettings(settings: AppSettings) {
    try {
      localStorage.setItem(STORAGE_KEYS.SETTINGS, JSON.stringify(settings));
    } catch (e) {
      console.error('Failed to save settings', e);
    }
  },

  getReadiness(): ReadinessState {
    try {
      const data = localStorage.getItem(STORAGE_KEYS.READINESS);
      if (!data) return DEFAULT_READINESS;
      return { ...DEFAULT_READINESS, ...JSON.parse(data) };
    } catch {
      return DEFAULT_READINESS;
    }
  },

  saveReadiness(readiness: ReadinessState) {
    try {
      localStorage.setItem(STORAGE_KEYS.READINESS, JSON.stringify(readiness));
    } catch (e) {
      console.error('Failed to save readiness', e);
    }
  },
};
