import { RunState, ScriptEntry, MatchResult, ScreenRegion } from '../types';
import { VisionEngine } from './visionEngine';
import { HealingEngine } from './healingEngine';
import { storage } from '../data/storage';

export interface RunnerCallbacks {
  onStateChange: (state: RunState, runningScript: string | null) => void;
  onLog: (level: 'INFO' | 'WARN' | 'ERROR' | 'HEAL', message: string) => void;
  onToast: (message: string) => void;
  onHighlight: (region: ScreenRegion, type: 'tap' | 'find' | 'swipe') => void;
}

export class ScriptRunner {
  private runState: RunState = 'IDLE';
  private currentScriptName: string | null = null;
  private shouldStopFlag: boolean = false;
  private isPausedFlag: boolean = false;
  private stopMessage: string = '';
  private maxRuntimeTimer: any = null;
  private lastMatch: MatchResult | null = null;
  private defaultSimilarity: number = 0.8;
  private autoWaitTimeout: number = 3.0;
  private clickDelayMs: number = 250;
  private humanize: boolean = true;
  private currentEntry: ScriptEntry | null = null;

  constructor(
    private vision: VisionEngine,
    private healing: HealingEngine,
    private callbacks: RunnerCallbacks
  ) {}

  public isRunning(): boolean {
    return this.runState === 'RUNNING' || this.runState === 'PAUSED';
  }

  public getState(): RunState {
    return this.runState;
  }

  public getCurrentScript(): string | null {
    return this.currentScriptName;
  }

  public async run(entry: ScriptEntry) {
    if (this.isRunning()) {
      this.callbacks.onLog('WARN', 'Já tem script rodando. Pare antes.');
      return;
    }

    this.currentEntry = entry;
    this.currentScriptName = entry.name;
    this.healing.currentScriptName = entry.name;
    this.shouldStopFlag = false;
    this.isPausedFlag = false;
    this.stopMessage = '';
    this.setState('RUNNING');

    this.callbacks.onLog('INFO', `▶ Iniciando script '${entry.name}'...`);
    const settings = storage.getSettings();

    if (settings.maxRuntimeMinutes > 0) {
      if (this.maxRuntimeTimer) clearTimeout(this.maxRuntimeTimer);
      this.maxRuntimeTimer = setTimeout(() => {
        if (this.isRunning()) {
          this.callbacks.onLog('WARN', `Tempo limite atingido (${settings.maxRuntimeMinutes} min). Parando.`);
          this.stop();
        }
      }, settings.maxRuntimeMinutes * 60 * 1000);
    }

    try {
      await this.executeScript(entry.code);
      if (this.runState !== 'ERROR' && this.runState !== 'STOPPING') {
        const msg = this.stopMessage || `Script '${entry.name}' finalizado com sucesso.`;
        this.callbacks.onLog('INFO', msg);
        this.callbacks.onToast(msg);
      }
    } catch (e: any) {
      if (e?.message === 'SCRIPT_STOPPED') {
        this.callbacks.onLog('WARN', 'Script interrompido pelo usuário.');
      } else {
        this.callbacks.onLog('ERROR', `Erro de execução: ${e?.message || e}`);
        this.setState('ERROR');
        return;
      }
    } finally {
      if (this.maxRuntimeTimer) clearTimeout(this.maxRuntimeTimer);
      this.setState('IDLE');
      this.currentScriptName = null;
    }
  }

  public stop() {
    if (!this.isRunning()) return;
    this.shouldStopFlag = true;
    this.isPausedFlag = false;
    this.setState('STOPPING');
    this.callbacks.onLog('WARN', '⏹ Solicitada parada do script...');
  }

  public pause() {
    if (this.runState === 'RUNNING') {
      this.isPausedFlag = true;
      this.setState('PAUSED');
      this.callbacks.onLog('INFO', '⏸ Script pausado.');
    }
  }

  public resume() {
    if (this.runState === 'PAUSED') {
      this.isPausedFlag = false;
      this.setState('RUNNING');
      this.callbacks.onLog('INFO', '▶ Script retomado.');
    }
  }

  private setState(state: RunState) {
    this.runState = state;
    this.callbacks.onStateChange(state, this.currentScriptName);
  }

  public async sleep(seconds: number): Promise<void> {
    const totalMs = seconds * 1000;
    const interval = 100;
    let elapsed = 0;

    while (elapsed < totalMs) {
      if (this.shouldStopFlag) throw new Error('SCRIPT_STOPPED');
      while (this.isPausedFlag) {
        if (this.shouldStopFlag) throw new Error('SCRIPT_STOPPED');
        await new Promise(r => setTimeout(r, 100));
      }
      const step = Math.min(interval, totalMs - elapsed);
      await new Promise(r => setTimeout(r, step));
      elapsed += step;
    }
  }

  private async checkStop() {
    if (this.shouldStopFlag) throw new Error('SCRIPT_STOPPED');
    while (this.isPausedFlag) {
      if (this.shouldStopFlag) throw new Error('SCRIPT_STOPPED');
      await new Promise(r => setTimeout(r, 100));
    }
  }

  // Parse and execute lua statements asynchronously
  private async executeScript(code: string) {
    const lines = code.split('\n');
    let i = 0;

    while (i < lines.length) {
      await this.checkStop();
      let rawLine = lines[i].trim();
      i++;

      // Skip empty or comment lines
      if (!rawLine || rawLine.startsWith('--')) continue;

      // Clean inline comments
      if (rawLine.includes('--')) {
        rawLine = rawLine.split('--')[0].trim();
      }

      // Settings
      if (rawLine.includes('Settings:setScriptDimension')) {
        const match = rawLine.match(/Settings:setScriptDimension\s*\(\s*(true|false)\s*,\s*(\d+)\s*\)/i);
        if (match) this.vision.scriptDimension = match[1] === 'true' ? parseInt(match[2], 10) : 0;
        continue;
      }
      if (rawLine.includes('Settings:setSimilarity')) {
        const match = rawLine.match(/Settings:setSimilarity\s*\(\s*([\d.]+)\s*\)/i);
        if (match) this.defaultSimilarity = parseFloat(match[1]);
        continue;
      }
      if (rawLine.includes('Settings:setAutoWaitTimeout')) {
        const match = rawLine.match(/Settings:setAutoWaitTimeout\s*\(\s*([\d.]+)\s*\)/i);
        if (match) this.autoWaitTimeout = parseFloat(match[1]);
        continue;
      }
      if (rawLine.includes('heal.on()')) {
        this.healing.enabled = true;
        this.callbacks.onLog('INFO', 'Self-healing ativado.');
        continue;
      }
      if (rawLine.includes('heal.off()')) {
        this.healing.enabled = false;
        this.callbacks.onLog('WARN', 'Self-healing desativado.');
        continue;
      }

      // log / toast / print
      if (rawLine.startsWith('log(') || rawLine.startsWith('print(') || rawLine.startsWith('logWarn(') || rawLine.startsWith('logError(') || rawLine.startsWith('toast(')) {
        this.handleOutputLine(rawLine);
        continue;
      }

      // sleep
      if (rawLine.startsWith('sleep(')) {
        const match = rawLine.match(/sleep\s*\(\s*([\d.]+)\s*\)/);
        if (match) {
          await this.sleep(parseFloat(match[1]));
        }
        continue;
      }

      // waitMsRandom
      if (rawLine.startsWith('waitMsRandom(')) {
        const match = rawLine.match(/waitMsRandom\s*\(\s*(\d+)\s*,\s*(\d+)\s*\)/);
        if (match) {
          const min = parseInt(match[1], 10);
          const max = parseInt(match[2], 10);
          const ms = Math.floor(Math.random() * (max - min + 1)) + min;
          await this.sleep(ms / 1000);
        }
        continue;
      }

      // clickText / findText
      if (rawLine.startsWith('clickText(') || rawLine.includes('if clickText(')) {
        const match = rawLine.match(/clickText\s*\(\s*["']([^"']+)["']\s*(?:,\s*(\d+))?\s*\)/);
        if (match) {
          const text = match[1];
          const timeout = match[2] ? parseInt(match[2], 10) : this.autoWaitTimeout;
          await this.doClickText(text, timeout);
        }
        continue;
      }

      // existsClick
      if (rawLine.startsWith('existsClick(') || rawLine.includes('if existsClick(')) {
        const match = rawLine.match(/existsClick\s*\(\s*["']([^"']+)["']\s*(?:,\s*([\d.]+))?\s*\)/);
        if (match) {
          const img = match[1];
          const timeout = match[2] ? parseFloat(match[2]) : 0;
          await this.doExistsClick(img, timeout);
        }
        continue;
      }

      // click
      if (rawLine.startsWith('click(')) {
        const match = rawLine.match(/click\s*\(\s*["']([^"']+)["']\s*\)/);
        if (match) {
          await this.doClick(match[1]);
        } else if (rawLine.includes('getLastMatch()')) {
          if (this.lastMatch) {
            this.callbacks.onHighlight(this.lastMatch.region, 'tap');
            this.vision.registerTap(this.lastMatch.targetX, this.lastMatch.targetY);
            await this.sleep(this.clickDelayMs / 1000);
          }
        }
        continue;
      }

      // swipe / humanSwipe
      if (rawLine.startsWith('swipe(') || rawLine.startsWith('humanSwipe(')) {
        const match = rawLine.match(/(?:swipe|humanSwipe)\s*\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)/);
        if (match) {
          const fx = parseInt(match[1], 10);
          const fy = parseInt(match[2], 10);
          const tx = parseInt(match[3], 10);
          const ty = parseInt(match[4], 10);
          const isHuman = rawLine.startsWith('humanSwipe');
          this.vision.registerSwipe(fx, fy, tx, ty, isHuman);
          this.callbacks.onHighlight({ x: Math.min(fx, tx), y: Math.min(fy, ty), w: Math.abs(tx - fx) || 40, h: Math.abs(ty - fy) || 40 }, 'swipe');
          this.callbacks.onLog('INFO', `Arrasto de (${fx},${fy}) até (${tx},${ty})${isHuman ? ' [humanizado]' : ''}`);
          await this.sleep(0.4);
        }
        continue;
      }

      // back / home / recents / openApp
      if (rawLine.startsWith('back()')) {
        this.callbacks.onLog('INFO', 'Botão Voltar acionado.');
        await this.sleep(0.3);
        continue;
      }
      if (rawLine.startsWith('home()')) {
        this.callbacks.onLog('INFO', 'Botão Início acionado.');
        await this.sleep(0.3);
        continue;
      }
      if (rawLine.startsWith('openApp(') || rawLine.startsWith('launchApp(')) {
        const match = rawLine.match(/(?:openApp|launchApp)\s*\(\s*["']([^"']+)["']\s*\)/);
        if (match) {
          const pkg = match[1];
          this.callbacks.onLog('INFO', `🚀 Abrindo app: ${pkg}`);
          this.callbacks.onToast(`Abrindo: ${pkg}`);
          this.launchAndroidPackage(pkg);
          await this.sleep(1.5);
        }
        continue;
      }

      // setStopMessage
      if (rawLine.startsWith('setStopMessage(')) {
        const match = rawLine.match(/setStopMessage\s*\(\s*["']([^"']+)["']\s*\)/);
        if (match) this.stopMessage = match[1];
        continue;
      }
    }
  }

  private handleOutputLine(line: string) {
    if (line.startsWith('toast(')) {
      const match = line.match(/toast\s*\(\s*["']([^"']+)["']/);
      const msg = match ? match[1] : line;
      this.callbacks.onToast(msg);
      return;
    }

    let level: 'INFO' | 'WARN' | 'ERROR' = 'INFO';
    if (line.startsWith('logWarn(')) level = 'WARN';
    if (line.startsWith('logError(')) level = 'ERROR';

    let content = line.replace(/^(?:log|logWarn|logError|print)\s*\(\s*/, '').replace(/\s*\)$/, '');
    content = content.replace(/["']\s*\.\.\s*["']/g, '').replace(/["']/g, '');

    // Replace dynamic placeholders
    content = content.replace('getScreenWidth()', '1080');
    content = content.replace('getScreenHeight()', '1920');

    this.callbacks.onLog(level, content);
  }

  private async doClick(patternKey: string) {
    let match = this.vision.find(patternKey, this.defaultSimilarity, null, this.currentEntry?.images || []);

    if (!match && this.healing.enabled) {
      const outcome = this.healing.attemptHeal(patternKey, this.defaultSimilarity, null, this.currentEntry?.images || []);
      if (outcome.recovered && outcome.match) {
        match = outcome.match;
      }
    }

    if (match) {
      this.lastMatch = match;
      this.healing.recordSuccess(patternKey, match);
      this.callbacks.onHighlight(match.region, 'tap');
      this.vision.registerTap(match.targetX, match.targetY, patternKey);
      await this.sleep(this.clickDelayMs / 1000);
      return true;
    } else {
      this.healing.recordFailure(patternKey);
      throw new Error(`click: alvo '${patternKey}' não encontrado`);
    }
  }

  private async doExistsClick(patternKey: string, timeout: number): Promise<boolean> {
    let match = this.vision.find(patternKey, this.defaultSimilarity, null, this.currentEntry?.images || []);

    if (!match && this.healing.enabled) {
      const outcome = this.healing.attemptHeal(patternKey, this.defaultSimilarity, null, this.currentEntry?.images || []);
      if (outcome.recovered && outcome.match) {
        match = outcome.match;
      }
    }

    if (match) {
      this.lastMatch = match;
      this.healing.recordSuccess(patternKey, match);
      this.callbacks.onHighlight(match.region, 'tap');
      this.vision.registerTap(match.targetX, match.targetY, patternKey);
      await this.sleep(this.clickDelayMs / 1000);
      return true;
    } else {
      this.healing.recordFailure(patternKey);
      return false;
    }
  }

  private async doClickText(text: string, timeout: number): Promise<boolean> {
    const match = this.vision.findText(text);
    if (match) {
      this.lastMatch = match;
      this.healing.recordSuccess(text, match);
      this.callbacks.onHighlight(match.region, 'tap');
      this.vision.registerTap(match.targetX, match.targetY, text);
      await this.sleep(this.clickDelayMs / 1000);
      return true;
    } else {
      this.healing.recordFailure(text);
      return false;
    }
  }

  private launchAndroidPackage(packageName: string) {
    try {
      // Se estiver em ambiente nativo Android WebView (Capacitor / Intent Scheme)
      if (typeof window !== 'undefined') {
        const intentUrl = `android-app://${packageName}`;
        const fallbackUrl = `https://play.google.com/store/apps/details?id=${packageName}`;
        
        // Tenta abrir via scheme Android Intent se for Android nativo
        const isAndroidDevice = /android/i.test(navigator.userAgent || '');
        if (isAndroidDevice) {
          window.location.href = `intent:#Intent;package=${packageName};end`;
        } else {
          // Em simulador/web preview, loga a intenção de execução
          console.log(`[KaizenAuto] Disparado Intent para pacote: ${packageName}`);
        }
      }
    } catch (err) {
      console.warn(`[KaizenAuto] Não foi possível iniciar o pacote ${packageName}:`, err);
    }
  }
}
