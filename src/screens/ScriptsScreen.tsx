import React from 'react';
import {
  Play,
  Pause,
  Square,
  Plus,
  MoreVertical,
  Edit,
  Copy,
  Trash2,
  FileCode,
  Image as ImageIcon,
  Clock,
  HardDrive,
  FolderOpen,
} from 'lucide-react';
import { ScriptEntry, ReadinessState, RunState } from '../types';
import { RequirementRow, TextInputDialog, ConfirmDialog, EmptyState } from '../components/Common';

interface ScriptsScreenProps {
  scripts: ScriptEntry[];
  readiness: ReadinessState;
  runState: RunState;
  runningScript: string | null;
  onUpdateReadiness: (updated: ReadinessState) => void;
  onRunScript: (script: ScriptEntry) => void;
  onPauseScript: () => void;
  onResumeScript: () => void;
  onStopScript: () => void;
  onEditScript: (script: ScriptEntry) => void;
  onCreateScript: (name: string) => void;
  onRenameScript: (oldName: string, newName: string) => void;
  onDuplicateScript: (script: ScriptEntry) => void;
  onDeleteScript: (name: string) => void;
}

export const ScriptsScreen: React.FC<ScriptsScreenProps> = ({
  scripts,
  readiness,
  runState,
  runningScript,
  onUpdateReadiness,
  onRunScript,
  onPauseScript,
  onResumeScript,
  onStopScript,
  onEditScript,
  onCreateScript,
  onRenameScript,
  onDuplicateScript,
  onDeleteScript,
}) => {
  const [dialogMode, setDialogMode] = React.useState<'create' | 'rename' | 'delete' | null>(null);
  const [selectedScript, setSelectedScript] = React.useState<ScriptEntry | null>(null);
  const [activeMenuScript, setActiveMenuScript] = React.useState<string | null>(null);

  const isReady = readiness.accessibilityOn && readiness.captureOn;

  const formatDate = (timestamp: number) => {
    const diffHours = (Date.now() - timestamp) / (1000 * 60 * 60);
    if (diffHours < 1) return 'Pouco tempo atrás';
    if (diffHours < 24) return `${Math.floor(diffHours)}h atrás`;
    return new Date(timestamp).toLocaleDateString('pt-BR');
  };

  return (
    <div className="p-4 sm:p-6 max-w-4xl mx-auto space-y-4 pb-24">
      {/* Permission Requirements Panel */}
      {(!readiness.accessibilityOn || !readiness.captureOn) && (
        <div className="bg-[#2B2930] rounded-2xl p-4 border border-white/5 space-y-2.5 shadow-sm">
          <div className="text-xs uppercase font-bold tracking-wider text-[#D0BCFF]">
            Permissões Necessárias
          </div>
          <RequirementRow
            title="Acessibilidade"
            description="Permite enviar toques e gestos na tela"
            satisfied={readiness.accessibilityOn}
            actionLabel="Ativar"
            onAction={() => onUpdateReadiness({ ...readiness, accessibilityOn: true })}
          />
          <RequirementRow
            title="Captura de tela"
            description="Permite ler a tela e encontrar imagens"
            satisfied={readiness.captureOn}
            actionLabel="Preparar"
            onAction={() => onUpdateReadiness({ ...readiness, captureOn: true })}
          />
          <RequirementRow
            title="Sobreposição"
            description="Mostra o indicador flutuante na tela"
            satisfied={readiness.overlayOn}
            actionLabel="Permitir"
            onAction={() => onUpdateReadiness({ ...readiness, overlayOn: true })}
          />
        </div>
      )}

      {/* Active Running Banner */}
      {runState !== 'IDLE' && (
        <div className="bg-[#4F378B]/40 border border-[#D0BCFF]/40 rounded-2xl p-4 flex items-center justify-between shadow-md">
          <div className="flex items-center gap-3">
            <div className="w-3 h-3 rounded-full bg-[#00BFA5] animate-ping" />
            <div>
              <div className="text-xs font-mono text-[#D0BCFF] uppercase font-semibold">
                {runState === 'RUNNING' ? 'Executando' : 'Pausado'}
              </div>
              <div className="text-base font-bold text-white">{runningScript}</div>
            </div>
          </div>
          <div className="flex items-center gap-2">
            {runState === 'RUNNING' ? (
              <button
                onClick={onPauseScript}
                className="px-3.5 py-1.5 rounded-xl bg-[#4A4458] text-[#E8DEF8] text-xs font-medium hover:bg-[#5C556C] flex items-center gap-1.5"
              >
                <Pause className="w-4 h-4" /> Pausar
              </button>
            ) : (
              <button
                onClick={onResumeScript}
                className="px-3.5 py-1.5 rounded-xl bg-[#00BFA5] text-white text-xs font-medium hover:bg-[#00A892] flex items-center gap-1.5"
              >
                <Play className="w-4 h-4" /> Continuar
              </button>
            )}
            <button
              onClick={onStopScript}
              className="px-3.5 py-1.5 rounded-xl bg-[#BA1A1A] text-white text-xs font-medium hover:bg-[#93000A] flex items-center gap-1.5"
            >
              <Square className="w-4 h-4 fill-current" /> Parar
            </button>
          </div>
        </div>
      )}

      {/* Header with Title and Create Action */}
      <div className="flex items-center justify-between pt-2">
        <div>
          <h2 className="text-xl font-bold text-white flex items-center gap-2">
            <FolderOpen className="w-5 h-5 text-[#D0BCFF]" />
            Seus Scripts ({scripts.length})
          </h2>
          <p className="text-xs text-[#CAC4D0] mt-0.5">
            Scripts Lua armazenados localmente com galeria de templates
          </p>
        </div>
        <button
          onClick={() => setDialogMode('create')}
          className="flex items-center gap-2 px-4 py-2 rounded-xl bg-[#D0BCFF] text-[#381E72] hover:bg-[#EADDFF] font-semibold text-xs transition-colors shadow-md"
        >
          <Plus className="w-4 h-4" /> Novo Script
        </button>
      </div>

      {/* Script List */}
      {scripts.length === 0 ? (
        <EmptyState
          title="Nenhum script encontrado"
          subtitle="Toque no botão 'Novo Script' acima para criar sua primeira automação Lua."
        />
      ) : (
        <div className="grid grid-cols-1 gap-3">
          {scripts.map((script) => {
            const isCurrentlyRunning = runningScript === script.name;

            return (
              <div
                key={script.name}
                className={`bg-[#2B2930] rounded-2xl p-4 border transition-all hover:border-[#D0BCFF]/40 ${
                  isCurrentlyRunning ? 'border-[#D0BCFF] bg-[#2B2930]/90 shadow-lg' : 'border-white/5'
                }`}
              >
                <div className="flex items-start justify-between gap-3">
                  <div className="flex items-start gap-3 flex-1 min-w-0">
                    <div className="w-10 h-10 rounded-xl bg-[#4A4458] flex items-center justify-center text-[#D0BCFF] shrink-0 mt-0.5">
                      <FileCode className="w-5 h-5" />
                    </div>
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center gap-2">
                        <span className="text-base font-semibold text-white truncate">
                          {script.name}
                        </span>
                        {isCurrentlyRunning && (
                          <span className="px-2 py-0.5 rounded text-[10px] font-mono bg-[#4F378B] text-[#D0BCFF] font-semibold">
                            RODANDO
                          </span>
                        )}
                      </div>

                      {/* Meta chips */}
                      <div className="flex flex-wrap items-center gap-3 mt-1.5 text-xs text-[#CAC4D0]">
                        <span className="flex items-center gap-1">
                          <Clock className="w-3.5 h-3.5 text-[#938F99]" />
                          {formatDate(script.lastModified)}
                        </span>
                        <span className="flex items-center gap-1">
                          <ImageIcon className="w-3.5 h-3.5 text-[#938F99]" />
                          {script.images?.length || 0} imagens
                        </span>
                        <span className="flex items-center gap-1">
                          <HardDrive className="w-3.5 h-3.5 text-[#938F99]" />
                          {(script.sizeBytes / 1024).toFixed(1)} KB
                        </span>
                      </div>
                    </div>
                  </div>

                  {/* Actions */}
                  <div className="flex items-center gap-1.5 shrink-0 relative">
                    {isCurrentlyRunning ? (
                      <button
                        onClick={onStopScript}
                        className="p-2.5 rounded-xl bg-[#BA1A1A] text-white hover:bg-[#93000A] transition-colors"
                        title="Parar execução"
                      >
                        <Square className="w-4 h-4 fill-current" />
                      </button>
                    ) : (
                      <button
                        onClick={() => onRunScript(script)}
                        className="p-2.5 rounded-xl bg-[#D0BCFF] text-[#381E72] hover:bg-[#EADDFF] transition-colors shadow-sm"
                        title="Executar script"
                      >
                        <Play className="w-4 h-4 fill-current" />
                      </button>
                    )}

                    <button
                      onClick={() => onEditScript(script)}
                      className="p-2.5 rounded-xl bg-[#4A4458] text-[#E8DEF8] hover:bg-[#5C556C] transition-colors"
                      title="Editar código e imagens"
                    >
                      <Edit className="w-4 h-4" />
                    </button>

                    {/* Context Menu Button */}
                    <button
                      onClick={() =>
                        setActiveMenuScript(activeMenuScript === script.name ? null : script.name)
                      }
                      className="p-2 rounded-xl text-[#CAC4D0] hover:text-white hover:bg-white/5"
                    >
                      <MoreVertical className="w-4 h-4" />
                    </button>

                    {/* Dropdown Menu */}
                    {activeMenuScript === script.name && (
                      <div className="absolute right-0 top-12 z-20 w-44 bg-[#1C1B1F] border border-white/10 rounded-xl shadow-xl py-1 overflow-hidden">
                        <button
                          onClick={() => {
                            setSelectedScript(script);
                            setDialogMode('rename');
                            setActiveMenuScript(null);
                          }}
                          className="w-full text-left px-3.5 py-2 text-xs text-[#E6E1E5] hover:bg-[#4F378B]/40 flex items-center gap-2"
                        >
                          <Edit className="w-3.5 h-3.5" /> Renomear
                        </button>
                        <button
                          onClick={() => {
                            onDuplicateScript(script);
                            setActiveMenuScript(null);
                          }}
                          className="w-full text-left px-3.5 py-2 text-xs text-[#E6E1E5] hover:bg-[#4F378B]/40 flex items-center gap-2"
                        >
                          <Copy className="w-3.5 h-3.5" /> Duplicar
                        </button>
                        <div className="h-px bg-white/10 my-1" />
                        <button
                          onClick={() => {
                            setSelectedScript(script);
                            setDialogMode('delete');
                            setActiveMenuScript(null);
                          }}
                          className="w-full text-left px-3.5 py-2 text-xs text-[#F2B8B5] hover:bg-[#8C1D18]/40 flex items-center gap-2"
                        >
                          <Trash2 className="w-3.5 h-3.5" /> Apagar
                        </button>
                      </div>
                    )}
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Dialogs */}
      {dialogMode === 'create' && (
        <TextInputDialog
          title="Novo Script"
          label="Nome do script (ex: farm_ouro)"
          confirmLabel="Criar"
          onDismiss={() => setDialogMode(null)}
          onConfirm={(name) => {
            onCreateScript(name);
            setDialogMode(null);
          }}
        />
      )}

      {dialogMode === 'rename' && selectedScript && (
        <TextInputDialog
          title="Renomear Script"
          label="Novo nome"
          initial={selectedScript.name}
          confirmLabel="Salvar"
          onDismiss={() => setDialogMode(null)}
          onConfirm={(newName) => {
            onRenameScript(selectedScript.name, newName);
            setDialogMode(null);
          }}
        />
      )}

      {dialogMode === 'delete' && selectedScript && (
        <ConfirmDialog
          title="Apagar Script"
          message={`Tem certeza que deseja apagar '${selectedScript.name}' e todas as suas ${selectedScript.images?.length || 0} imagens associadas? Essa ação não pode ser desfeita.`}
          confirmLabel="Apagar Script"
          onDismiss={() => setDialogMode(null)}
          onConfirm={() => {
            onDeleteScript(selectedScript.name);
            setDialogMode(null);
          }}
        />
      )}
    </div>
  );
};
