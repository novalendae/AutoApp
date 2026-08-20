import React from 'react';
import {
  Brain,
  Sparkles,
  CheckCircle2,
  AlertCircle,
  Trash2,
  Layers,
  Activity,
  Zap,
} from 'lucide-react';
import { PatternMemory, HealingEvent } from '../types';
import { Chip, SectionHeader, ConfirmDialog, EmptyState } from '../components/Common';

interface LearningScreenProps {
  patterns: PatternMemory[];
  events: HealingEvent[];
  onClearAll: () => void;
}

export const LearningScreen: React.FC<LearningScreenProps> = ({
  patterns,
  events,
  onClearAll,
}) => {
  const [showClearConfirm, setShowClearConfirm] = React.useState(false);

  const totalHits = patterns.reduce((acc, p) => acc + p.successCount, 0);
  const totalFails = patterns.reduce((acc, p) => acc + p.failureCount, 0);
  const successfulHeals = events.filter((e) => e.succeeded).length;

  const formatDate = (timestamp: number) => {
    return new Date(timestamp).toLocaleTimeString('pt-BR', {
      hour: '2-digit',
      minute: '2-digit',
    });
  };

  return (
    <div className="p-4 sm:p-6 max-w-4xl mx-auto space-y-5 pb-24">
      {/* Header & Reset Action */}
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-xl font-bold text-white flex items-center gap-2">
            <Brain className="w-5 h-5 text-[#D0BCFF]" />
            Aprendizado & Self-Healing
          </h2>
          <p className="text-xs text-[#CAC4D0] mt-0.5">
            Estatísticas de memória de imagem e cascata de recuperação
          </p>
        </div>
        <button
          onClick={() => setShowClearConfirm(true)}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-[#2B2930] hover:bg-[#8C1D18]/30 border border-white/5 text-xs text-[#CAC4D0] hover:text-[#F2B8B5] transition-colors"
        >
          <Trash2 className="w-3.5 h-3.5" /> Zerar Tudo
        </button>
      </div>

      {/* Summary KPI Grid */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
        <div className="bg-[#2B2930] p-3.5 rounded-2xl border border-white/5 flex flex-col justify-between">
          <span className="text-xs text-[#CAC4D0] flex items-center gap-1">
            <Layers className="w-3.5 h-3.5 text-[#D0BCFF]" /> Imagens
          </span>
          <span className="text-2xl font-bold text-white mt-1">{patterns.length}</span>
        </div>

        <div className="bg-[#2B2930] p-3.5 rounded-2xl border border-white/5 flex flex-col justify-between">
          <span className="text-xs text-[#CAC4D0] flex items-center gap-1">
            <CheckCircle2 className="w-3.5 h-3.5 text-[#00BFA5]" /> Acertos
          </span>
          <span className="text-2xl font-bold text-[#00BFA5] mt-1">{totalHits}</span>
        </div>

        <div className="bg-[#2B2930] p-3.5 rounded-2xl border border-white/5 flex flex-col justify-between">
          <span className="text-xs text-[#CAC4D0] flex items-center gap-1">
            <AlertCircle className="w-3.5 h-3.5 text-[#F2B8B5]" /> Falhas
          </span>
          <span className="text-2xl font-bold text-[#F2B8B5] mt-1">{totalFails}</span>
        </div>

        <div className="bg-[#2B2930] p-3.5 rounded-2xl border border-white/5 flex flex-col justify-between">
          <span className="text-xs text-[#CAC4D0] flex items-center gap-1">
            <Zap className="w-3.5 h-3.5 text-[#D0BCFF]" /> Curas
          </span>
          <span className="text-2xl font-bold text-[#D0BCFF] mt-1">{successfulHeals}</span>
        </div>
      </div>

      {/* Pattern Memory Section */}
      <div className="space-y-3">
        <SectionHeader text={`Memória de Padrões (${patterns.length})`} />

        {patterns.length === 0 ? (
          <EmptyState
            title="Nenhum padrão memorizado"
            subtitle="Conforme você executa scripts, o KaizenAuto aprenderá as regiões, escalas e limiares das imagens."
          />
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            {patterns.map((item) => {
              const relPercent = Math.round((item.reliability || 0) * 100);

              return (
                <div
                  key={item.patternKey}
                  className="bg-[#2B2930] rounded-2xl p-4 border border-white/5 space-y-3 shadow-xs"
                >
                  <div className="flex items-start justify-between gap-2">
                    <div className="min-w-0">
                      <div className="text-sm font-bold text-white truncate font-mono">
                        {item.patternKey}
                      </div>
                      <div className="text-xs text-[#CAC4D0] mt-0.5">
                        Atualizado às {formatDate(item.updatedAt)}
                      </div>
                    </div>
                    <Chip text={item.preferredStrategy} color="#D0BCFF" />
                  </div>

                  {/* Reliability Progress Bar */}
                  <div>
                    <div className="flex justify-between text-xs mb-1">
                      <span className="text-[#CAC4D0]">Confiabilidade</span>
                      <span className="font-mono text-white font-medium">{relPercent}%</span>
                    </div>
                    <div className="w-full bg-[#1C1B1F] h-2 rounded-full overflow-hidden">
                      <div
                        className={`h-full rounded-full transition-all duration-500 ${
                          relPercent > 80
                            ? 'bg-[#00BFA5]'
                            : relPercent > 50
                            ? 'bg-[#FFB74D]'
                            : 'bg-[#BA1A1A]'
                        }`}
                        style={{ width: `${relPercent}%` }}
                      />
                    </div>
                  </div>

                  {/* Stats Badges */}
                  <div className="grid grid-cols-3 gap-2 text-[11px] font-mono text-[#CAC4D0] bg-[#1C1B1F] p-2.5 rounded-xl border border-white/5">
                    <div>
                      <span className="text-[#938F99] block text-[9px] uppercase">Acertos</span>
                      <span className="text-[#00BFA5] font-semibold">{item.successCount}</span>
                    </div>
                    <div>
                      <span className="text-[#938F99] block text-[9px] uppercase">Score Médio</span>
                      <span className="text-white font-semibold">{(item.avgScore * 100).toFixed(0)}%</span>
                    </div>
                    <div>
                      <span className="text-[#938F99] block text-[9px] uppercase">Δ Limiar</span>
                      <span className="text-[#D0BCFF] font-semibold">
                        {item.thresholdDelta >= 0 ? `+${item.thresholdDelta}` : item.thresholdDelta}
                      </span>
                    </div>
                  </div>

                  {item.learnedVariantPath && (
                    <div className="text-[11px] text-[#D0BCFF] flex items-center gap-1.5 bg-[#4F378B]/30 px-2.5 py-1 rounded-lg border border-[#D0BCFF]/20">
                      <Sparkles className="w-3.5 h-3.5" />
                      <span>Variante visual salva em cache</span>
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* Self-Healing Events Section */}
      <div className="space-y-3 pt-2">
        <SectionHeader text={`Eventos de Self-Healing Recentes (${events.length})`} />

        {events.length === 0 ? (
          <EmptyState
            title="Nenhum evento registrado"
            subtitle="Quando um elemento mudar na tela e o bot recuperar a ação automaticamente, o registro aparecerá aqui."
          />
        ) : (
          <div className="space-y-2">
            {events.map((event) => (
              <div
                key={event.id}
                className="bg-[#2B2930] rounded-2xl p-3.5 border border-white/5 flex flex-col sm:flex-row sm:items-center justify-between gap-3 shadow-xs"
              >
                <div className="space-y-1 min-w-0">
                  <div className="flex items-center gap-2 flex-wrap">
                    <span
                      className={`px-2 py-0.5 rounded text-[10px] font-bold uppercase ${
                        event.succeeded
                          ? 'bg-[#004D40] text-[#80CBC4]'
                          : 'bg-[#8C1D18] text-[#F2B8B5]'
                      }`}
                    >
                      {event.succeeded ? 'Curado' : 'Falhou'}
                    </span>
                    <span className="text-xs font-bold text-white font-mono">{event.patternKey}</span>
                    <span className="text-xs text-[#CAC4D0]">no script [{event.scriptName}]</span>
                  </div>
                  <div className="text-xs text-[#CAC4D0] leading-relaxed">{event.details}</div>
                </div>

                <div className="flex sm:flex-col items-end justify-between gap-1 shrink-0 text-xs font-mono">
                  <Chip text={event.tactic} color="#D0BCFF" />
                  <span className="text-[10px] text-[#938F99]">{formatDate(event.createdAt)}</span>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Confirmation Dialog */}
      {showClearConfirm && (
        <ConfirmDialog
          title="Zerar Memória do Bot"
          message="Tem certeza que deseja apagar todos os padrões aprendidos e histórico de self-healing? O bot começará a aprender do zero."
          confirmLabel="Zerar Memória"
          onDismiss={() => setShowClearConfirm(false)}
          onConfirm={() => {
            onClearAll();
            setShowClearConfirm(false);
          }}
        />
      )}
    </div>
  );
};
