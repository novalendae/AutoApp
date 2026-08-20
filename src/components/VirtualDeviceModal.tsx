import React from 'react';
import { X, Smartphone, Play, Pause, Square, Sparkles, RefreshCw } from 'lucide-react';
import { VisionEngine } from '../runtime/visionEngine';
import { RunState, ScreenRegion } from '../types';

interface VirtualDeviceModalProps {
  isOpen: boolean;
  onClose: () => void;
  vision: VisionEngine;
  runState: RunState;
  runningScript: string | null;
  onRun: () => void;
  onPause: () => void;
  onResume: () => void;
  onStop: () => void;
  lastHighlight?: { region: ScreenRegion; type: 'tap' | 'find' | 'swipe'; timestamp: number } | null;
}

export const VirtualDeviceModal: React.FC<VirtualDeviceModalProps> = ({
  isOpen,
  onClose,
  vision,
  runState,
  runningScript,
  onRun,
  onPause,
  onResume,
  onStop,
  lastHighlight,
}) => {
  const [screenState, setScreenState] = React.useState(vision.getScreenState());
  const [activeTab, setActiveTab] = React.useState<'game' | 'app' | 'settings'>('game');

  React.useEffect(() => {
    const interval = setInterval(() => {
      setScreenState({ ...vision.getScreenState() });
    }, 100);
    return () => clearInterval(interval);
  }, [vision]);

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/75 backdrop-blur-sm p-3 sm:p-6 overflow-y-auto">
      <div className="w-full max-w-4xl bg-[#1C1B1F] border border-white/10 rounded-3xl overflow-hidden shadow-2xl flex flex-col md:flex-row max-h-[90vh]">
        {/* Device Screen Frame */}
        <div className="flex-1 flex flex-col items-center justify-center bg-[#141218] p-4 sm:p-6 border-b md:border-b-0 md:border-r border-white/10">
          <div className="flex items-center justify-between w-full max-w-xs mb-3 text-xs text-[#CAC4D0]">
            <div className="flex items-center gap-1.5 font-medium">
              <Smartphone className="w-4 h-4 text-[#D0BCFF]" />
              <span>Dispositivo Alvo Virtual (1080x1920)</span>
            </div>
            <div className="flex items-center gap-1">
              <span className={`w-2 h-2 rounded-full ${runState === 'RUNNING' ? 'bg-[#00BFA5] animate-pulse' : 'bg-[#938F99]'}`} />
              <span className="capitalize">{runState.toLowerCase()}</span>
            </div>
          </div>

          {/* Phone Shell */}
          <div className="relative w-full max-w-[280px] aspect-[9/16] bg-[#2B2930] rounded-[36px] border-4 border-[#49454F] shadow-inner p-2.5 overflow-hidden flex flex-col">
            {/* Status Bar */}
            <div className="flex justify-between items-center px-3 py-1 text-[10px] text-[#CAC4D0] font-mono shrink-0 select-none">
              <span>12:00</span>
              <div className="w-12 h-3 bg-black/40 rounded-full mx-auto" />
              <span>100% 🔋</span>
            </div>

            {/* Target App Content */}
            <div className="relative flex-1 bg-[#1C1B1F] rounded-[24px] overflow-hidden border border-white/5 flex flex-col">
              {/* Virtual App Header */}
              <div className="bg-[#2B2930] px-3 py-2 border-b border-white/5 flex items-center justify-between">
                <span className="text-[11px] font-medium text-[#E6E1E5]">Target Arena v1.0</span>
                <span className="text-[10px] px-1.5 py-0.5 rounded bg-[#4F378B] text-[#EADDFF]">Online</span>
              </div>

              {/* Elements on Screen */}
              <div className="relative flex-1 p-3 flex flex-col justify-between select-none">
                {/* Top elements */}
                <div className="flex justify-between items-center">
                  <div
                    id="elem_settings"
                    className="px-2.5 py-1 rounded bg-[#2B2930] border border-white/10 text-[11px] font-medium text-[#E6E1E5] flex items-center gap-1 shadow-xs"
                  >
                    ⚙ Configurações
                  </div>
                  <div
                    id="elem_close"
                    className="w-8 h-8 rounded-full bg-[#BA1A1A] text-white flex items-center justify-center text-xs font-bold shadow-md active:scale-95"
                  >
                    ✕
                  </div>
                </div>

                {/* Center / Game canvas area */}
                <div className="flex flex-col items-center justify-center my-auto text-center gap-3">
                  <div className="w-16 h-16 rounded-2xl bg-gradient-to-br from-[#6750A4] to-[#00BFA5] flex items-center justify-center text-2xl shadow-lg animate-pulse">
                    ⚔️
                  </div>
                  <div className="text-xs font-medium text-[#CAC4D0]">Modo de Combate</div>

                  {/* Reward button */}
                  <div
                    id="elem_reward"
                    className="px-4 py-2 rounded-lg bg-[#FFD54F] text-black font-bold text-xs shadow-md active:scale-95 transition-transform"
                  >
                    ★ RECOMPENSA ★
                  </div>

                  {/* Loading status */}
                  <div className="text-[10px] text-[#B0BEC5] flex items-center gap-1 bg-[#37474F]/50 px-2 py-0.5 rounded">
                    <span>Carregando...</span>
                  </div>
                </div>

                {/* Bottom Action Area */}
                <div className="flex flex-col gap-2 pt-2">
                  <div
                    id="elem_battle"
                    className="w-full py-2.5 rounded-xl bg-[#FF8F00] text-white font-bold text-xs text-center shadow-lg active:scale-95 transition-transform"
                  >
                    BATALHAR
                  </div>
                  <div
                    id="elem_play"
                    className="w-full py-2.5 rounded-xl bg-[#00BFA5] text-white font-bold text-xs text-center shadow-lg active:scale-95 transition-transform"
                  >
                    JOGAR
                  </div>
                </div>

                {/* Visual Highlight Overlay for Touches and Swipes */}
                {lastHighlight && Date.now() - lastHighlight.timestamp < 1500 && (
                  <div
                    className="absolute pointer-events-none transition-all duration-300 rounded-lg border-2 border-[#D0BCFF] bg-[#D0BCFF]/30 z-30 animate-ping"
                    style={{
                      left: `${Math.max(10, Math.min(80, (lastHighlight.region.x / 1080) * 100))}%`,
                      top: `${Math.max(10, Math.min(80, (lastHighlight.region.y / 1920) * 100))}%`,
                      width: '40px',
                      height: '40px',
                      transform: 'translate(-50%, -50%)',
                    }}
                  />
                )}

                {/* Tap pointer effect */}
                {screenState.lastTap && Date.now() - screenState.lastTap.time < 1200 && (
                  <div
                    className="absolute pointer-events-none w-8 h-8 rounded-full border-2 border-[#00BFA5] bg-[#00BFA5]/40 -translate-x-1/2 -translate-y-1/2 z-40 animate-pulse flex items-center justify-center"
                    style={{
                      left: `${Math.max(5, Math.min(95, (screenState.lastTap.x / 1080) * 100))}%`,
                      top: `${Math.max(5, Math.min(95, (screenState.lastTap.y / 1920) * 100))}%`,
                    }}
                  >
                    <span className="w-2 h-2 rounded-full bg-white" />
                  </div>
                )}
              </div>

              {/* Navigation Bar Pills */}
              <div className="bg-[#2B2930] py-1 px-4 flex justify-around items-center border-t border-white/5">
                <span className="w-3 h-3 border-l-2 border-b-2 border-[#CAC4D0] rotate-45" />
                <span className="w-3 h-3 rounded-full border-2 border-[#CAC4D0]" />
                <span className="w-3 h-3 border-2 border-[#CAC4D0] rounded-xs" />
              </div>
            </div>
          </div>
        </div>

        {/* Right Info & Live Control Panel */}
        <div className="w-full md:w-80 p-5 flex flex-col justify-between bg-[#1C1B1F] text-[#E6E1E5]">
          <div>
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-base font-medium flex items-center gap-2">
                <Sparkles className="w-4 h-4 text-[#D0BCFF]" />
                Painel de Controle
              </h3>
              <button onClick={onClose} className="p-1 rounded-lg text-[#CAC4D0] hover:text-white hover:bg-white/5">
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="bg-[#2B2930] rounded-xl p-3.5 mb-4 border border-white/5">
              <div className="text-xs text-[#CAC4D0] mb-1">Script Selecionado</div>
              <div className="text-sm font-semibold text-white">{runningScript || 'Nenhum rodando'}</div>
              <div className="text-xs text-[#D0BCFF] mt-1 flex items-center gap-1.5">
                <span className="w-2 h-2 rounded-full bg-[#00BFA5]" />
                Visão Computacional & Self-Healing Ativos
              </div>
            </div>

            <div className="space-y-2 mb-4">
              <div className="text-xs uppercase font-semibold text-[#D0BCFF]">Elementos Monitorados</div>
              <div className="text-xs space-y-1 text-[#CAC4D0] font-mono bg-[#141218] p-3 rounded-lg border border-white/5">
                <div className="flex justify-between">
                  <span>botao_jogar.png</span>
                  <span className="text-[#00BFA5]">✓ Presente</span>
                </div>
                <div className="flex justify-between">
                  <span>fechar.png</span>
                  <span className="text-[#00BFA5]">✓ Presente</span>
                </div>
                <div className="flex justify-between">
                  <span>iniciar_batalha.png</span>
                  <span className="text-[#00BFA5]">✓ Presente</span>
                </div>
                <div className="flex justify-between">
                  <span>OCR "Configurações"</span>
                  <span className="text-[#D0BCFF]">✓ 0.98 score</span>
                </div>
              </div>
            </div>
          </div>

          {/* Playback Controls */}
          <div className="pt-4 border-t border-white/10 flex flex-col gap-2">
            <div className="flex gap-2">
              {runState === 'RUNNING' ? (
                <button
                  onClick={onPause}
                  className="flex-1 flex items-center justify-center gap-2 py-2.5 rounded-xl bg-[#4A4458] text-[#E8DEF8] hover:bg-[#5C556C] font-medium text-xs transition-colors"
                >
                  <Pause className="w-4 h-4" /> Pausar
                </button>
              ) : runState === 'PAUSED' ? (
                <button
                  onClick={onResume}
                  className="flex-1 flex items-center justify-center gap-2 py-2.5 rounded-xl bg-[#00BFA5] text-white hover:bg-[#00A892] font-medium text-xs transition-colors"
                >
                  <Play className="w-4 h-4" /> Continuar
                </button>
              ) : (
                <button
                  onClick={onRun}
                  className="flex-1 flex items-center justify-center gap-2 py-2.5 rounded-xl bg-[#D0BCFF] text-[#381E72] hover:bg-[#EADDFF] font-medium text-xs transition-colors"
                >
                  <Play className="w-4 h-4" /> Executar
                </button>
              )}

              {runState !== 'IDLE' && (
                <button
                  onClick={onStop}
                  className="flex items-center justify-center gap-1.5 px-4 py-2.5 rounded-xl bg-[#BA1A1A] text-white hover:bg-[#93000A] font-medium text-xs transition-colors"
                >
                  <Square className="w-4 h-4 fill-current" /> Parar
                </button>
              )}
            </div>

            <button
              onClick={onClose}
              className="w-full py-2 text-center text-xs text-[#CAC4D0] hover:text-white"
            >
              Fechar Visualizador
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
