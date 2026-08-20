export type RunState = 'IDLE' | 'RUNNING' | 'PAUSED' | 'STOPPING' | 'ERROR';

export interface ScriptImage {
  name: string;
  dataUrl: string;
  sizeBytes: number;
  width?: number;
  height?: number;
  updatedAt?: number;
}

export interface ScriptEntry {
  name: string;
  lastModified: number;
  sizeBytes: number;
  imageCount: number;
  code: string;
  images: ScriptImage[];
}

export interface ReadinessState {
  accessibilityOn: boolean;
  captureOn: boolean;
  overlayOn: boolean;
}

export interface PatternMemory {
  id: number;
  patternKey: string;
  successCount: number;
  failureCount: number;
  lastX: number;
  lastY: number;
  lastW: number;
  lastH: number;
  avgScore: number;
  minSuccessScore: number;
  avgScale: number;
  thresholdDelta: number;
  learnedVariantPath?: string | null;
  learnedVariantDataUrl?: string | null;
  preferredStrategy: string;
  updatedAt: number;
  reliability: number;
}

export interface HealingEvent {
  id: number;
  patternKey: string;
  scriptName: string;
  tactic: string;
  succeeded: boolean;
  scoreBefore: number;
  scoreAfter: number;
  details: string;
  screenshotPath?: string | null;
  screenshotDataUrl?: string | null;
  createdAt: number;
}

export interface ScreenObservation {
  id: number;
  signature: string;
  packageName: string;
  elementsJson: string;
  seenCount: number;
  lastSeenAt: number;
}

export interface RunLog {
  id: number;
  scriptName: string;
  level: 'INFO' | 'WARN' | 'ERROR' | 'HEAL';
  message: string;
  createdAt: number;
}

export interface ScreenRegion {
  x: number;
  y: number;
  w: number;
  h: number;
}

export interface MatchResult {
  region: ScreenRegion;
  score: number;
  scale: number;
  strategy: 'TEMPLATE' | 'RELAX_THRESHOLD' | 'LEARNED_VARIANT' | 'WIDE_MULTISCALE' | 'ORB_FEATURES' | 'OCR_TEXT' | 'A11Y_TREE';
  targetX: number;
  targetY: number;
}

export interface AppSettings {
  healingEnabled: boolean;
  learnVariants: boolean;
  passiveObserver: boolean;
  humanize: boolean;
  maxRuntimeMinutes: number;
}

export interface SimulatedScreenElement {
  id: string;
  type: 'button' | 'text' | 'image' | 'input' | 'icon' | 'container';
  label: string;
  x: number;
  y: number;
  w: number;
  h: number;
  imageKey?: string;
  bgColor?: string;
  textColor?: string;
  onClick?: () => void;
}
