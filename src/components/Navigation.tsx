import React from 'react';
import {
  FileCode,
  Edit3,
  Terminal,
  Brain,
  HelpCircle,
  Settings as SettingsIcon,
  Smartphone,
  Play,
  Pause,
  Square,
} from 'lucide-react';
import { RunState } from '../types';

export type TabType = 'scripts' | 'editor' | 'logs' | 'bot' | 'help' | 'settings';

interface NavigationProps {
  currentTab: TabType;
  onSelectTab: (tab: TabType) => void;
  runState: RunState;
  runningScript: string | null;
  onOpenVirtualDevice: () => void;
  onPause: () => void;
  onResume: () => void;
  onStop: () => void;
}

export const Navigation: React.FC<NavigationProps> = ({
  currentTab,
  onSelectTab,
  runState,
  runningScript,
  onOpenVirtualDevice,
  onPause,
  onResume,
  onStop,
}) => {
  const tabs = [
    { id: 'scripts' as TabType, label: 'Scripts', icon: FileCode },
    { id: 'editor' as TabType, label: 'Editor', icon: Edit3 },
    { id: 'logs' as TabType, label: 'Logs', icon: Terminal },
    { id: 'bot' as TabType, label: 'Bot', icon: Brain },
    { id: 'help' as TabType, label: 'Guia', icon: HelpCircle },
    { id: 'settings' as TabType, label: 'Ajustes', icon: SettingsIcon },
  ];

  return (
    <>
      {/* Top App Bar */}
      <header className="sticky top-0 z-30 bg-[#1C1B1F] border-b border-white/10 px-4 py-3 flex items-center justify-between shadow-md">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-lg bg-[#4F378B] flex items-center justify-center text-[#D0BCFF] font-bold shadow-inner">
            K
          </div>
          <div>
            <h1 className="text-base font-bold text-white leading-none">KaizenAuto</h1>
            <p className="text-[11px] text-[#CAC4D0] mt-0.5">Automação por Visão + Lua</p>
          </div>
        </div>

        <div className="flex items-center gap-2">
          {/* Running Status Pill */}
          {runState !== 'IDLE' && (
            <div className="flex items-center gap-1.5 px-3 py-1 rounded-full bg-[#4F378B]/60 border border-[#D0BCFF]/30 text-xs font-mono text-[#D0BCFF]">
              <span className={`w-2 h-2 rounded-full ${runState === 'RUNNING' ? 'bg-[#00BFA5] animate-ping' : 'bg-[#FFB74D]'}`} />
              <span className="truncate max-w-[100px] sm:max-w-[160px]">{runningScript}</span>
              
              {runState === 'RUNNING' ? (
                <button onClick={onPause} title="Pausar" className="p-0.5 hover:text-white">
                  <Pause className="w-3.5 h-3.5" />
                </button>
              ) : (
                <button onClick={onResume} title="Continuar" className="p-0.5 hover:text-white">
                  <Play className="w-3.5 h-3.5" />
                </button>
              )}

              <button onClick={onStop} title="Parar" className="p-0.5 hover:text-[#F2B8B5]">
                <Square className="w-3.5 h-3.5 fill-current text-[#F2B8B5]" />
              </button>
            </div>
          )}

          {/* Virtual Target Device Simulator Launcher */}
          <button
            onClick={onOpenVirtualDevice}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-[#2B2930] hover:bg-[#36343B] border border-white/10 text-xs font-medium text-[#E6E1E5] transition-colors"
            title="Abrir Tela Virtual do Dispositivo"
          >
            <Smartphone className="w-4 h-4 text-[#D0BCFF]" />
            <span className="hidden sm:inline">Dispositivo Alvo</span>
          </button>
        </div>
      </header>

      {/* Bottom Navigation Bar (Material 3 style) */}
      <nav className="fixed bottom-0 left-0 right-0 z-30 bg-[#1C1B1F] border-t border-white/10 px-2 py-1.5 flex justify-around items-center max-w-7xl mx-auto shadow-2xl">
        {tabs.map((tab) => {
          const Icon = tab.icon;
          const isActive = currentTab === tab.id;
          return (
            <button
              key={tab.id}
              onClick={() => onSelectTab(tab.id)}
              className={`flex flex-col items-center justify-center py-1 px-3 rounded-2xl transition-all ${
                isActive
                  ? 'text-[#D0BCFF]'
                  : 'text-[#CAC4D0] hover:text-[#E6E1E5] hover:bg-white/5'
              }`}
            >
              <div
                className={`px-4 py-1 rounded-full flex items-center justify-center transition-all ${
                  isActive ? 'bg-[#4F378B] text-[#EADDFF]' : ''
                }`}
              >
                <Icon className="w-5 h-5" />
              </div>
              <span className="text-[11px] font-medium mt-1 leading-none">{tab.label}</span>
            </button>
          );
        })}
      </nav>
    </>
  );
};
