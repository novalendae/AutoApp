import { MatchResult, PatternMemory, HealingEvent, ScreenRegion, ScriptImage } from '../types';
import { storage } from '../data/storage';
import { VisionEngine } from './visionEngine';

export interface HealingOutcome {
  recovered: boolean;
  match?: MatchResult;
  tactic?: string;
  triedTactics: string[];
}

export class HealingEngine {
  public enabled: boolean = true;
  public learnVariants: boolean = true;
  public currentScriptName: string = 'desconhecido';
  public onLog?: (level: 'INFO' | 'WARN' | 'ERROR' | 'HEAL', message: string) => void;

  constructor(private vision: VisionEngine) {}

  public effectiveThreshold(patternKey: string, requested: number): number {
    if (!this.enabled) return requested;
    const memory = this.getMemory(patternKey);
    if (!memory) return requested;
    return Math.max(0.55, Math.min(0.99, requested + memory.thresholdDelta));
  }

  public suggestedRegion(patternKey: string): ScreenRegion | null {
    if (!this.enabled) return null;
    const memory = this.getMemory(patternKey);
    if (!memory || memory.successCount < 4 || memory.lastX < 0) return null;
    const margin = Math.max(memory.lastW, memory.lastH);
    return {
      x: Math.max(0, memory.lastX - margin),
      y: Math.max(0, memory.lastY - margin),
      w: memory.lastW + margin * 2,
      h: memory.lastH + margin * 2,
    };
  }

  public attemptHeal(
    patternKey: string,
    requestedSimilarity: number,
    region: ScreenRegion | null,
    images: ScriptImage[] = []
  ): HealingOutcome {
    if (!this.enabled) {
      return { recovered: false, triedTactics: [] };
    }

    const memory = this.getMemory(patternKey);
    const baseThreshold = this.effectiveThreshold(patternKey, requestedSimilarity);
    const tried: string[] = [];

    const tactics = [
      'RELAX_THRESHOLD',
      'LEARNED_VARIANT',
      'WIDE_MULTISCALE',
      'ORB_FEATURES',
      'OCR_TEXT',
      'A11Y_TREE',
    ];

    // Priority ordering based on memory
    if (memory?.preferredStrategy) {
      const idx = tactics.indexOf(memory.preferredStrategy);
      if (idx > 0) {
        tactics.splice(idx, 1);
        tactics.unshift(memory.preferredStrategy);
      }
    }

    for (const tactic of tactics) {
      tried.push(tactic);
      let result: MatchResult | null = null;

      if (tactic === 'RELAX_THRESHOLD') {
        result = this.relaxThreshold(patternKey, baseThreshold, region, images);
      } else if (tactic === 'LEARNED_VARIANT') {
        result = this.useLearnedVariant(patternKey, baseThreshold, region, images);
      } else if (tactic === 'WIDE_MULTISCALE') {
        result = this.wideMultiScale(patternKey, baseThreshold, images);
      } else if (tactic === 'ORB_FEATURES') {
        result = this.orbFeatures(patternKey, region);
      } else if (tactic === 'OCR_TEXT') {
        result = this.ocrFallback(patternKey, region);
      } else if (tactic === 'A11Y_TREE') {
        result = this.accessibilityFallback(patternKey);
      }

      if (result) {
        this.onHealSuccess(patternKey, tactic, result, baseThreshold);
        this.onLog?.('HEAL', `🔧 Curado via ${tactic} (score ${result.score.toFixed(2)})`);
        return {
          recovered: true,
          match: result,
          tactic,
          triedTactics: tried,
        };
      }
    }

    this.recordEvent(
      patternKey,
      'GIVE_UP',
      false,
      baseThreshold,
      0.0,
      `Todas as táticas falharam: ${tried.join(', ')}`
    );
    this.onLog?.('ERROR', `❌ Não consegui encontrar '${patternKey}' nem com self-healing.`);
    return { recovered: false, triedTactics: tried };
  }

  private relaxThreshold(
    patternKey: string,
    baseThreshold: number,
    region: ScreenRegion | null,
    images: ScriptImage[]
  ): MatchResult | null {
    const steps = [0.05, 0.10, 0.15, 0.20];
    for (const step of steps) {
      const relaxed = Math.max(0.55, baseThreshold - step);
      const hit = this.vision.find(patternKey, relaxed, region, images);
      if (hit) {
        return { ...hit, strategy: 'RELAX_THRESHOLD' };
      }
    }
    return null;
  }

  private useLearnedVariant(
    patternKey: string,
    baseThreshold: number,
    region: ScreenRegion | null,
    images: ScriptImage[]
  ): MatchResult | null {
    const memory = this.getMemory(patternKey);
    if (!memory?.learnedVariantPath) return null;
    const hit = this.vision.find(patternKey, Math.max(0.55, baseThreshold - 0.05), region, images);
    return hit ? { ...hit, strategy: 'LEARNED_VARIANT' } : null;
  }

  private wideMultiScale(patternKey: string, baseThreshold: number, images: ScriptImage[]): MatchResult | null {
    const hit = this.vision.find(patternKey, Math.max(0.55, baseThreshold - 0.08), null, images);
    return hit ? { ...hit, scale: 0.9, strategy: 'WIDE_MULTISCALE' } : null;
  }

  private orbFeatures(patternKey: string, region: ScreenRegion | null): MatchResult | null {
    const screen = this.vision.getScreenState();
    const cleanKey = patternKey.replace(/['"]/g, '').toLowerCase();
    const match = screen.elements.find(el =>
      el.imageKey?.toLowerCase().includes(cleanKey.replace('.png', '')) ||
      cleanKey.includes(el.label.toLowerCase())
    );
    if (match) {
      return {
        region: { x: match.x, y: match.y, w: match.w, h: match.h },
        score: 0.88,
        scale: 1.0,
        strategy: 'ORB_FEATURES',
        targetX: match.x + Math.floor(match.w / 2),
        targetY: match.y + Math.floor(match.h / 2),
      };
    }
    return null;
  }

  private ocrFallback(patternKey: string, region: ScreenRegion | null): MatchResult | null {
    const guess = this.toTextGuess(patternKey);
    if (!guess) return null;
    return this.vision.findText(guess, region);
  }

  private accessibilityFallback(patternKey: string): MatchResult | null {
    const screen = this.vision.getScreenState();
    const guess = this.toTextGuess(patternKey) || patternKey.replace(/\..+$/, '');
    const element = screen.elements.find(el =>
      el.label.toLowerCase().includes(guess.toLowerCase())
    );
    if (element) {
      return {
        region: { x: element.x, y: element.y, w: element.w, h: element.h },
        score: 0.95,
        scale: 1.0,
        strategy: 'A11Y_TREE',
        targetX: element.x + Math.floor(element.w / 2),
        targetY: element.y + Math.floor(element.h / 2),
      };
    }
    return null;
  }

  public recordSuccess(patternKey: string, match: MatchResult) {
    const allMemory = storage.getPatternMemory();
    const old = allMemory.find(m => m.patternKey === patternKey);
    const hits = (old?.successCount || 0) + 1;
    const avgScore = old ? (old.avgScore * old.successCount + match.score) / hits : match.score;
    const avgScale = old ? (old.avgScale * old.successCount + match.scale) / hits : match.scale;

    const updated: PatternMemory = {
      id: old?.id || Date.now(),
      patternKey,
      successCount: hits,
      failureCount: old?.failureCount || 0,
      lastX: match.region.x,
      lastY: match.region.y,
      lastW: match.region.w,
      lastH: match.region.h,
      avgScore: Number(avgScore.toFixed(2)),
      minSuccessScore: Math.min(old?.minSuccessScore ?? 1.0, match.score),
      avgScale: Number(avgScale.toFixed(2)),
      thresholdDelta: old?.thresholdDelta || 0.0,
      learnedVariantPath: old?.learnedVariantPath || (this.learnVariants ? `learned_variants/${patternKey}` : null),
      preferredStrategy: match.strategy,
      updatedAt: Date.now(),
      reliability: hits / (hits + (old?.failureCount || 0)),
    };

    const remaining = allMemory.filter(m => m.patternKey !== patternKey);
    storage.savePatternMemory([updated, ...remaining]);
  }

  public recordFailure(patternKey: string) {
    const allMemory = storage.getPatternMemory();
    const old = allMemory.find(m => m.patternKey === patternKey);
    const failures = (old?.failureCount || 0) + 1;
    const hits = old?.successCount || 0;

    const updated: PatternMemory = {
      id: old?.id || Date.now(),
      patternKey,
      successCount: hits,
      failureCount: failures,
      lastX: old?.lastX ?? -1,
      lastY: old?.lastY ?? -1,
      lastW: old?.lastW ?? 0,
      lastH: old?.lastH ?? 0,
      avgScore: old?.avgScore ?? 0.0,
      minSuccessScore: old?.minSuccessScore ?? 1.0,
      avgScale: old?.avgScale ?? 1.0,
      thresholdDelta: old?.thresholdDelta || 0.0,
      learnedVariantPath: old?.learnedVariantPath ?? null,
      preferredStrategy: old?.preferredStrategy || 'TEMPLATE',
      updatedAt: Date.now(),
      reliability: hits === 0 && failures === 0 ? 0 : hits / (hits + failures),
    };

    const remaining = allMemory.filter(m => m.patternKey !== patternKey);
    storage.savePatternMemory([updated, ...remaining]);
  }

  private onHealSuccess(
    patternKey: string,
    tactic: string,
    match: MatchResult,
    thresholdBefore: number
  ) {
    const allMemory = storage.getPatternMemory();
    const old = allMemory.find(m => m.patternKey === patternKey);

    let newDelta = old?.thresholdDelta || 0.0;
    if (match.score < thresholdBefore) {
      const needed = match.score - thresholdBefore - 0.03;
      newDelta = Math.max(-0.25, Math.min(0.0, (old?.thresholdDelta || 0.0) + needed));
    }

    const hits = (old?.successCount || 0) + 1;
    const updated: PatternMemory = {
      id: old?.id || Date.now(),
      patternKey,
      successCount: hits,
      failureCount: old?.failureCount || 0,
      lastX: match.region.x,
      lastY: match.region.y,
      lastW: match.region.w,
      lastH: match.region.h,
      avgScore: old ? Number(((old.avgScore * old.successCount + match.score) / hits).toFixed(2)) : match.score,
      minSuccessScore: Math.min(old?.minSuccessScore ?? 1.0, match.score),
      avgScale: old?.avgScale || 1.0,
      thresholdDelta: Number(newDelta.toFixed(2)),
      learnedVariantPath: this.learnVariants ? `learned_variants/${patternKey}` : old?.learnedVariantPath || null,
      preferredStrategy: tactic,
      updatedAt: Date.now(),
      reliability: hits / (hits + (old?.failureCount || 0)),
    };

    const remaining = allMemory.filter(m => m.patternKey !== patternKey);
    storage.savePatternMemory([updated, ...remaining]);

    this.recordEvent(
      patternKey,
      tactic,
      true,
      thresholdBefore,
      match.score,
      `Limiar ajustado para ${(thresholdBefore + newDelta).toFixed(2)}; estratégia ${tactic}`
    );
  }

  private recordEvent(
    patternKey: string,
    tactic: string,
    succeeded: boolean,
    scoreBefore: number,
    scoreAfter: number,
    details: string
  ) {
    const allEvents = storage.getHealingEvents();
    const newEvent: HealingEvent = {
      id: Date.now(),
      patternKey,
      scriptName: this.currentScriptName,
      tactic,
      succeeded,
      scoreBefore: Number(scoreBefore.toFixed(2)),
      scoreAfter: Number(scoreAfter.toFixed(2)),
      details,
      createdAt: Date.now(),
    };
    storage.saveHealingEvents([newEvent, ...allEvents].slice(0, 200));
  }

  private getMemory(key: string): PatternMemory | undefined {
    return storage.getPatternMemory().find(m => m.patternKey === key);
  }

  private toTextGuess(patternKey: string): string | null {
    const words = patternKey
      .replace(/^.*\//, '')
      .replace(/\.[^.]+$/, '')
      .split(/[_-]/)
      .filter(w => w.length > 2 && !['btn', 'button', 'botao', 'icon', 'img'].includes(w.toLowerCase()));
    return words.length > 0 ? words.join(' ') : null;
  }
}
