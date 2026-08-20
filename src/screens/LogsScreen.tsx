import React from 'react';
import { Trash2, Terminal, Filter, ArrowDown } from 'lucide-react';
import { RunLog } from '../types';
import { Chip, EmptyState } from '../components/Common';

interface LogsScreenProps {
  logs: RunLog[];
  onClearLogs: () => void;
}

export const LogsScreen: React.FC<LogsScreenProps> = ({ logs, onClearLogs }) => {
  const [filter, setFilter] = React.useState<'ALL' | 'ERRORS' | 'HEAL'>('ALL');
  const scrollRef = React.useRef<HTMLDivElement>(null);

  const filteredLogs = logs.filter((log) => {
    if (filter === 'ERRORS') return log.level === 'WARN' || log.level === 'ERROR';
    if (filter === 'HEAL') return log.level === 'HEAL';
    return true;
  });

  const formatTime = (timestamp: number) => {
    const d = new Date(timestamp);
    return `${d.getHours().toString().padStart(2, '0')}:${d
      .getMinutes()
      .toString()
      .padStart(2, '0')}:${d.getSeconds().toString().padStart(2, '0')}`;
  };

  const getLevelColor = (level: RunLog['level']) => {
    switch (level) {
      case 'INFO':
        return '#D0BCFF';
      case 'WARN':
        return '#FFB74D';
      case 'ERROR':
        return '#F2B8B5';
      case 'HEAL':
        return '#80CBC4';
      default:
        return '#CAC4D0';
    }
  };

  const scrollToBottom = () => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  };

  return (
    <div className="p-4 sm:p-6 max-w-4xl mx-auto space-y-3 pb-24 h-[calc(100vh-120px)] flex flex-col">
      {/* Header & Filter Controls */}
      <div className="bg-[#2B2930] rounded-2xl p-3 flex flex-wrap items-center justify-between gap-2 border border-white/5 shadow-sm shrink-0">
        <div className="flex items-center gap-2">
          <Terminal className="w-5 h-5 text-[#D0BCFF]" />
          <h2 className="text-base font-bold text-white">Logs de Execução</h2>
          <span className="text-xs text-[#CAC4D0] font-mono">({filteredLogs.length})</span>
        </div>

        <div className="flex items-center gap-2">
          {/* Filter Pills */}
          <div className="flex bg-[#1C1B1F] p-1 rounded-xl border border-white/5 text-xs">
            <button
              onClick={() => setFilter('ALL')}
              className={`px-3 py-1 rounded-lg font-medium transition-colors ${
                filter === 'ALL' ? 'bg-[#4F378B] text-[#EADDFF]' : 'text-[#CAC4D0] hover:text-white'
              }`}
            >
              TUDO
            </button>
            <button
              onClick={() => setFilter('ERRORS')}
              className={`px-3 py-1 rounded-lg font-medium transition-colors ${
                filter === 'ERRORS'
                  ? 'bg-[#8C1D18] text-[#F9DEDC]'
                  : 'text-[#CAC4D0] hover:text-white'
              }`}
            >
              ERROS
            </button>
            <button
              onClick={() => setFilter('HEAL')}
              className={`px-3 py-1 rounded-lg font-medium transition-colors ${
                filter === 'HEAL'
                  ? 'bg-[#004D40] text-[#E0F2F1]'
                  : 'text-[#CAC4D0] hover:text-white'
              }`}
            >
              CURA
            </button>
          </div>

          {/* Clear Logs Button */}
          <button
            onClick={onClearLogs}
            className="p-2 rounded-xl bg-[#1C1B1F] hover:bg-[#8C1D18]/30 border border-white/5 text-[#CAC4D0] hover:text-[#F2B8B5] transition-colors"
            title="Limpar todos os logs"
          >
            <Trash2 className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* Logs Console Container */}
      <div
        ref={scrollRef}
        className="flex-1 bg-[#141218] rounded-2xl border border-white/10 p-3.5 overflow-y-auto font-mono text-xs space-y-1.5 shadow-inner"
      >
        {filteredLogs.length === 0 ? (
          <EmptyState
            title="Nenhum log registrado"
            subtitle="Execute um script ou dispare ações para ver a saída do console aqui."
          />
        ) : (
          filteredLogs.map((log) => (
            <div
              key={log.id}
              className="flex items-start gap-2.5 py-1 px-2 rounded-lg hover:bg-white/5 transition-colors leading-relaxed"
            >
              <span className="text-[#938F99] shrink-0">{formatTime(log.createdAt)}</span>
              <Chip text={log.level} color={getLevelColor(log.level)} />
              {log.scriptName && (
                <span className="text-[#D0BCFF]/70 shrink-0">[{log.scriptName}]</span>
              )}
              <span
                className={`break-all ${
                  log.level === 'ERROR'
                    ? 'text-[#F2B8B5]'
                    : log.level === 'WARN'
                    ? 'text-[#FFB74D]'
                    : log.level === 'HEAL'
                    ? 'text-[#80CBC4]'
                    : 'text-[#E6E1E5]'
                }`}
              >
                {log.message}
              </span>
            </div>
          ))
        )}
      </div>

      {/* Floating Scroll-to-Bottom helper */}
      {filteredLogs.length > 10 && (
        <button
          onClick={scrollToBottom}
          className="self-end flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-[#2B2930] hover:bg-[#36343B] text-xs text-[#CAC4D0] border border-white/10 shadow-lg"
        >
          <ArrowDown className="w-3.5 h-3.5" /> Ir para o final
        </button>
      )}
    </div>
  );
};
