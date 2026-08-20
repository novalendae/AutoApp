import React from 'react';
import {
  Settings as SettingsIcon,
  Shield,
  Sliders,
  Cpu,
  Info,
  Clock,
  Sparkles,
  Smartphone,
} from 'lucide-react';
import { AppSettings, ReadinessState } from '../types';
import { SectionHeader } from '../components/Common';

interface SettingsScreenProps {
  settings: AppSettings;
  readiness: ReadinessState;
  onUpdateSettings: (updated: AppSettings) => void;
  onUpdateReadiness: (updated: ReadinessState) => void;
}

export const SettingsScreen: React.FC<SettingsScreenProps> = ({
  settings,
  readiness,
  onUpdateSettings,
  onUpdateReadiness,
}) => {
  return (
    <div className="p-4 sm:p-6 max-w-4xl mx-auto space-y-5 pb-24">
      <div>
        <h2 className="text-xl font-bold text-white flex items-center gap-2">
          <SettingsIcon className="w-5 h-5 text-[#D0BCFF]" />
          Ajustes do Sistema
        </h2>
        <p className="text-xs text-[#CAC4D0] mt-0.5">
          Configurações de execução, segurança e parâmetros do motor de visão
        </p>
      </div>

      {/* Permissions / Hardware Services */}
      <div className="space-y-3">
        <SectionHeader text="Serviços e Permissões do Aparelho" />
        <div className="bg-[#2B2930] rounded-2xl p-4 border border-white/5 space-y-4 shadow-xs">
          {/* Accessibility toggle */}
          <div className="flex items-center justify-between">
            <div className="space-y-0.5">
              <div className="text-sm font-medium text-white flex items-center gap-2">
                <Shield className="w-4 h-4 text-[#D0BCFF]" /> Serviço de Acessibilidade
              </div>
              <div className="text-xs text-[#CAC4D0]">
                Necessário para o envio de toques, swipes e gestos
              </div>
            </div>
            <label className="relative inline-flex items-center cursor-pointer">
              <input
                type="checkbox"
                checked={readiness.accessibilityOn}
                onChange={(e) =>
                  onUpdateReadiness({ ...readiness, accessibilityOn: e.target.checked })
                }
                className="sr-only peer"
              />
              <div className="w-11 h-6 bg-[#4A4458] peer-focus:outline-hidden rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-[#00BFA5]"></div>
            </label>
          </div>

          <div className="h-px bg-white/5" />

          {/* Screen Capture toggle */}
          <div className="flex items-center justify-between">
            <div className="space-y-0.5">
              <div className="text-sm font-medium text-white flex items-center gap-2">
                <Smartphone className="w-4 h-4 text-[#D0BCFF]" /> Gravação / Captura de Tela
              </div>
              <div className="text-xs text-[#CAC4D0]">
                Permite capturar frames para template matching e OCR
              </div>
            </div>
            <label className="relative inline-flex items-center cursor-pointer">
              <input
                type="checkbox"
                checked={readiness.captureOn}
                onChange={(e) =>
                  onUpdateReadiness({ ...readiness, captureOn: e.target.checked })
                }
                className="sr-only peer"
              />
              <div className="w-11 h-6 bg-[#4A4458] peer-focus:outline-hidden rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-[#00BFA5]"></div>
            </label>
          </div>

          <div className="h-px bg-white/5" />

          {/* Overlay indicator toggle */}
          <div className="flex items-center justify-between">
            <div className="space-y-0.5">
              <div className="text-sm font-medium text-white flex items-center gap-2">
                <Sparkles className="w-4 h-4 text-[#D0BCFF]" /> Indicador Flutuante / Sobreposição
              </div>
              <div className="text-xs text-[#CAC4D0]">
                Mostra o botão de parada flutuante sobre outros aplicativos
              </div>
            </div>
            <label className="relative inline-flex items-center cursor-pointer">
              <input
                type="checkbox"
                checked={readiness.overlayOn}
                onChange={(e) =>
                  onUpdateReadiness({ ...readiness, overlayOn: e.target.checked })
                }
                className="sr-only peer"
              />
              <div className="w-11 h-6 bg-[#4A4458] peer-focus:outline-hidden rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-[#00BFA5]"></div>
            </label>
          </div>
        </div>
      </div>

      {/* Execution Parameters */}
      <div className="space-y-3">
        <SectionHeader text="Parâmetros de Execução" />
        <div className="bg-[#2B2930] rounded-2xl p-4 border border-white/5 space-y-4 shadow-xs">
          {/* Humanize toggle */}
          <div className="flex items-center justify-between">
            <div className="space-y-0.5">
              <div className="text-sm font-medium text-white flex items-center gap-2">
                <Sliders className="w-4 h-4 text-[#D0BCFF]" /> Humanizar Toques
              </div>
              <div className="text-xs text-[#CAC4D0]">
                Adiciona micro-jitter aleatório (±3px, ±25ms) nos toques para evitar detecções
              </div>
            </div>
            <label className="relative inline-flex items-center cursor-pointer">
              <input
                type="checkbox"
                checked={settings.humanize}
                onChange={(e) =>
                  onUpdateSettings({ ...settings, humanize: e.target.checked })
                }
                className="sr-only peer"
              />
              <div className="w-11 h-6 bg-[#4A4458] peer-focus:outline-hidden rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-[#00BFA5]"></div>
            </label>
          </div>

          <div className="h-px bg-white/5" />

          {/* Max Runtime slider */}
          <div className="space-y-2">
            <div className="flex justify-between items-center">
              <div className="text-sm font-medium text-white flex items-center gap-2">
                <Clock className="w-4 h-4 text-[#D0BCFF]" /> Tempo Limite de Execução
              </div>
              <span className="text-xs font-mono text-[#D0BCFF] font-semibold bg-[#4F378B]/40 px-2.5 py-0.5 rounded-lg">
                {settings.maxRuntimeMinutes === 0 ? 'Sem limite' : `${settings.maxRuntimeMinutes} min`}
              </span>
            </div>
            <input
              type="range"
              min="0"
              max="120"
              step="5"
              value={settings.maxRuntimeMinutes}
              onChange={(e) =>
                onUpdateSettings({ ...settings, maxRuntimeMinutes: parseInt(e.target.value, 10) })
              }
              className="w-full h-2 bg-[#1C1B1F] rounded-lg appearance-none cursor-pointer accent-[#D0BCFF]"
            />
            <div className="flex justify-between text-[10px] text-[#938F99]">
              <span>Desligado (0m)</span>
              <span>30m</span>
              <span>60m</span>
              <span>120m</span>
            </div>
          </div>
        </div>
      </div>

      {/* Bot & Self-Healing Settings */}
      <div className="space-y-3">
        <SectionHeader text="Aprendizado do Bot & Self-Healing" />
        <div className="bg-[#2B2930] rounded-2xl p-4 border border-white/5 space-y-4 shadow-xs">
          {/* Healing enabled toggle */}
          <div className="flex items-center justify-between">
            <div className="space-y-0.5">
              <div className="text-sm font-medium text-white flex items-center gap-2">
                <Cpu className="w-4 h-4 text-[#D0BCFF]" /> Self-Healing Automático
              </div>
              <div className="text-xs text-[#CAC4D0]">
                Recupera falhas de find/click testando limiares, ORB e OCR
              </div>
            </div>
            <label className="relative inline-flex items-center cursor-pointer">
              <input
                type="checkbox"
                checked={settings.healingEnabled}
                onChange={(e) =>
                  onUpdateSettings({ ...settings, healingEnabled: e.target.checked })
                }
                className="sr-only peer"
              />
              <div className="w-11 h-6 bg-[#4A4458] peer-focus:outline-hidden rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-[#00BFA5]"></div>
            </label>
          </div>

          <div className="h-px bg-white/5" />

          {/* Learn variants toggle */}
          <div className="flex items-center justify-between">
            <div className="space-y-0.5">
              <div className="text-sm font-medium text-white flex items-center gap-2">
                <Sparkles className="w-4 h-4 text-[#D0BCFF]" /> Salvar Variações Visuais
              </div>
              <div className="text-xs text-[#CAC4D0]">
                Armazena novos recortes de tela com sucesso para agilizar futuras buscas
              </div>
            </div>
            <label className="relative inline-flex items-center cursor-pointer">
              <input
                type="checkbox"
                checked={settings.learnVariants}
                onChange={(e) =>
                  onUpdateSettings({ ...settings, learnVariants: e.target.checked })
                }
                className="sr-only peer"
              />
              <div className="w-11 h-6 bg-[#4A4458] peer-focus:outline-hidden rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-[#00BFA5]"></div>
            </label>
          </div>
        </div>
      </div>

      {/* About Box */}
      <div className="bg-[#2B2930] rounded-2xl p-4 border border-white/5 flex items-start gap-3 shadow-xs">
        <div className="w-9 h-9 rounded-xl bg-[#4A4458] flex items-center justify-center text-[#D0BCFF] shrink-0 mt-0.5">
          <Info className="w-5 h-5" />
        </div>
        <div className="space-y-1">
          <div className="text-sm font-bold text-white">KaizenAuto v1.0.0</div>
          <p className="text-xs text-[#CAC4D0] leading-relaxed">
            Automação inteligente por visão computacional offline (OpenCV + ML Kit) com scripts Lua e
            sistema adaptativo de auto-recuperação (Self-Healing).
          </p>
        </div>
      </div>
    </div>
  );
};
