import { ScreenRegion, MatchResult, ScriptImage, SimulatedScreenElement } from '../types';

export interface SimulatedScreenState {
  width: number;
  height: number;
  packageName: string;
  elements: SimulatedScreenElement[];
  lastTap?: { x: number; y: number; time: number; label?: string };
  lastSwipe?: { fromX: number; fromY: number; toX: number; toY: number; time: number; human?: boolean };
}

export class VisionEngine {
  public scriptDimension: number = 1080;
  public compareDimension: number = 1080;
  public screenSize: { width: number; height: number } = { width: 1080, height: 1920 };
  private activeScreen: SimulatedScreenState;

  constructor() {
    this.activeScreen = {
      width: 1080,
      height: 1920,
      packageName: 'com.target.game',
      elements: [
        { id: '1', type: 'text', label: 'Configurações', x: 120, y: 180, w: 260, h: 48, bgColor: '#2B2930', textColor: '#E6E1E5' },
        { id: '2', type: 'button', label: 'JOGAR', imageKey: 'botao_jogar.png', x: 440, y: 1300, w: 200, h: 64, bgColor: '#00BFA5', textColor: '#FFFFFF' },
        { id: '3', type: 'button', label: '✕', imageKey: 'fechar.png', x: 960, y: 100, w: 56, h: 56, bgColor: '#BA1A1A', textColor: '#FFFFFF' },
        { id: '4', type: 'button', label: 'BATALHAR', imageKey: 'iniciar_batalha.png', x: 420, y: 1100, w: 240, h: 68, bgColor: '#FF8F00', textColor: '#FFFFFF' },
        { id: '5', type: 'button', label: '★ RECOMPENSA ★', imageKey: 'recompensa.png', x: 400, y: 700, w: 280, h: 60, bgColor: '#FFD54F', textColor: '#000000' },
        { id: '6', type: 'text', label: 'Carregando...', imageKey: 'carregando.png', x: 440, y: 950, w: 200, h: 40, bgColor: '#37474F', textColor: '#B0BEC5' },
        { id: '7', type: 'icon', label: 'Config', imageKey: 'icone_config.png', x: 80, y: 100, w: 140, h: 50, bgColor: '#4A4458', textColor: '#EADDFF' },
      ],
    };
  }

  public getScreenState(): SimulatedScreenState {
    return this.activeScreen;
  }

  public setScreenElements(elements: SimulatedScreenElement[]) {
    this.activeScreen.elements = elements;
  }

  public registerTap(x: number, y: number, label?: string) {
    this.activeScreen.lastTap = { x, y, time: Date.now(), label };
  }

  public registerSwipe(fromX: number, fromY: number, toX: number, toY: number, human: boolean = false) {
    this.activeScreen.lastSwipe = { fromX, fromY, toX, toY, time: Date.now(), human };
  }

  public find(
    patternSource: string,
    similarity: number = 0.8,
    region: ScreenRegion | null = null,
    images: ScriptImage[] = []
  ): MatchResult | null {
    const cleanSource = patternSource.replace(/['"]/g, '').trim();

    // Check if target is in current simulated screen elements
    const element = this.activeScreen.elements.find(el => {
      const matchKey = el.imageKey === cleanSource || el.imageKey?.toLowerCase() === cleanSource.toLowerCase() ||
        el.label.toLowerCase() === cleanSource.toLowerCase() || cleanSource.includes(el.label.toLowerCase());

      if (!matchKey) return false;

      if (region) {
        const cx = el.x + el.w / 2;
        const cy = el.y + el.h / 2;
        return cx >= region.x && cx <= region.x + region.w && cy >= region.y && cy <= region.y + region.h;
      }
      return true;
    });

    if (element) {
      // Calculate realistic matching score based on similarity
      const calculatedScore = 0.85 + Math.random() * 0.12;
      if (calculatedScore >= similarity) {
        return {
          region: { x: element.x, y: element.y, w: element.w, h: element.h },
          score: calculatedScore,
          scale: 1.0,
          strategy: 'TEMPLATE',
          targetX: element.x + Math.floor(element.w / 2),
          targetY: element.y + Math.floor(element.h / 2),
        };
      }
    }

    return null;
  }

  public findText(query: string, region: ScreenRegion | null = null): MatchResult | null {
    const cleanQuery = query.toLowerCase().trim();
    const element = this.activeScreen.elements.find(el => {
      const textMatch = el.label.toLowerCase().includes(cleanQuery);
      if (!textMatch) return false;

      if (region) {
        const cx = el.x + el.w / 2;
        const cy = el.y + el.h / 2;
        return cx >= region.x && cx <= region.x + region.w && cy >= region.y && cy <= region.y + region.h;
      }
      return true;
    });

    if (element) {
      return {
        region: { x: element.x, y: element.y, w: element.w, h: element.h },
        score: 0.96,
        scale: 1.0,
        strategy: 'OCR_TEXT',
        targetX: element.x + Math.floor(element.w / 2),
        targetY: element.y + Math.floor(element.h / 2),
      };
    }

    return null;
  }

  public readAllText(region: ScreenRegion | null = null): string {
    const visibleElements = this.activeScreen.elements.filter(el => {
      if (!region) return true;
      const cx = el.x + el.w / 2;
      const cy = el.y + el.h / 2;
      return cx >= region.x && cx <= region.x + region.w && cy >= region.y && cy <= region.y + region.h;
    });

    return visibleElements.map(el => el.label).join('\n');
  }

  public findAll(
    patternSource: string,
    similarity: number = 0.8,
    region: ScreenRegion | null = null
  ): MatchResult[] {
    const match = this.find(patternSource, similarity, region);
    return match ? [match] : [];
  }
}
