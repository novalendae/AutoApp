import React from 'react';
import {
  Save,
  Play,
  Image as ImageIcon,
  Plus,
  Trash2,
  ChevronDown,
  Sparkles,
  Check,
  Upload,
} from 'lucide-react';
import { ScriptEntry, ScriptImage, RunState } from '../types';
import { ConfirmDialog } from '../components/Common';

interface EditorScreenProps {
  scripts: ScriptEntry[];
  currentScript: ScriptEntry | null;
  runState: RunState;
  onSelectScript: (script: ScriptEntry) => void;
  onSaveScript: (name: string, code: string) => void;
  onSaveAndRun: (name: string, code: string) => void;
  onAddImage: (scriptName: string, image: ScriptImage) => void;
  onDeleteImage: (scriptName: string, imageName: string) => void;
}

const SNIPPETS = [
  { label: 'click', code: 'click("botao.png")\n' },
  { label: 'exists', code: 'if exists("icone.png", 5) then\n    -- acao\nend\n' },
  { label: 'wait', code: 'wait("tela_carregada.png", 10)\n' },
  { label: 'waitClick', code: 'waitClick("ok.png", 10)\n' },
  { label: 'existsClick', code: 'existsClick("popup_fechar.png", 3)\n' },
  { label: 'waitVanish', code: 'waitVanish("carregando.png", 20)\n' },
  { label: 'clickText', code: 'clickText("Confirmar", 5)\n' },
  { label: 'swipe', code: 'swipe(500, 1500, 500, 400, 350)\n' },
  { label: 'sleep', code: 'sleep(1.5)\n' },
  { label: 'loop', code: 'while not shouldStop() do\n    -- acao repetitiva\n    sleep(1)\nend\n' },
  { label: 'Region', code: 'local topo = Region(0, 0, 1080, 400)\nif topo:exists("item.png") then\n    click(getLastMatch())\nend\n' },
  { label: 'log', code: 'log("Mensagem de status")\n' },
  { label: 'toast', code: 'toast("Aviso na tela")\n' },
  { label: 'exit', code: 'scriptExit("Terminou o ciclo")\n' },
  { label: 'Settings', code: 'Settings:setScriptDimension(true, 1080)\nSettings:setAutoWaitTimeout(5)\nSettings:setSimilarity(0.85)\n' },
];

export const EditorScreen: React.FC<EditorScreenProps> = ({
  scripts,
  currentScript,
  runState,
  onSelectScript,
  onSaveScript,
  onSaveAndRun,
  onAddImage,
  onDeleteImage,
}) => {
  const [code, setCode] = React.useState(currentScript?.code || '');
  const [isDirty, setIsDirty] = React.useState(false);
  const [showGallery, setShowGallery] = React.useState(false);
  const [imageToDelete, setImageToDelete] = React.useState<string | null>(null);
  const fileInputRef = React.useRef<HTMLInputElement>(null);
  const textareaRef = React.useRef<HTMLTextAreaElement>(null);

  React.useEffect(() => {
    if (currentScript) {
      setCode(currentScript.code);
      setIsDirty(false);
    }
  }, [currentScript?.name]);

  const handleCodeChange = (newVal: string) => {
    setCode(newVal);
    setIsDirty(newVal !== currentScript?.code);
  };

  const handleSave = () => {
    if (!currentScript) return;
    onSaveScript(currentScript.name, code);
    setIsDirty(false);
  };

  const handleSaveAndRun = () => {
    if (!currentScript) return;
    onSaveAndRun(currentScript.name, code);
    setIsDirty(false);
  };

  const insertSnippet = (snippetCode: string) => {
    const textarea = textareaRef.current;
    if (!textarea) {
      handleCodeChange(code + '\n' + snippetCode);
      return;
    }
    const start = textarea.selectionStart;
    const end = textarea.selectionEnd;
    const before = code.substring(0, start);
    const after = code.substring(end);
    const updated = before + snippetCode + after;
    handleCodeChange(updated);

    setTimeout(() => {
      textarea.focus();
      textarea.selectionStart = textarea.selectionEnd = start + snippetCode.length;
    }, 50);
  };

  const insertImageName = (imgName: string) => {
    insertSnippet(`"${imgName}"`);
  };

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file || !currentScript) return;

    const reader = new FileReader();
    reader.onload = (event) => {
      const dataUrl = event.target?.result as string;
      const newImg: ScriptImage = {
        name: file.name,
        dataUrl,
        sizeBytes: file.size,
        updatedAt: Date.now(),
      };
      onAddImage(currentScript.name, newImg);
    };
    reader.readAsDataURL(file);
    e.target.value = '';
  };

  if (!currentScript) {
    return (
      <div className="p-6 max-w-4xl mx-auto flex flex-col items-center justify-center min-h-[60vh] text-center">
        <p className="text-sm text-[#CAC4D0] mb-4">Selecione um script na lista para editar.</p>
      </div>
    );
  }

  return (
    <div className="flex flex-col h-[calc(100vh-125px)] max-w-6xl mx-auto p-2 sm:p-4 pb-20">
      {/* Top Editor Bar */}
      <div className="bg-[#2B2930] rounded-2xl p-3 mb-2 flex flex-wrap items-center justify-between gap-2 border border-white/5 shadow-sm">
        <div className="flex items-center gap-2">
          {/* Script Switcher Dropdown */}
          <div className="relative">
            <select
              value={currentScript.name}
              onChange={(e) => {
                const target = scripts.find((s) => s.name === e.target.value);
                if (target) onSelectScript(target);
              }}
              className="bg-[#1C1B1F] border border-[#938F99] rounded-xl px-3 py-1.5 text-xs font-semibold text-white focus:outline-hidden focus:border-[#D0BCFF] pr-8 appearance-none cursor-pointer"
            >
              {scripts.map((s) => (
                <option key={s.name} value={s.name}>
                  {s.name} {s.name === currentScript.name && isDirty ? '*' : ''}
                </option>
              ))}
            </select>
            <ChevronDown className="w-3.5 h-3.5 text-[#CAC4D0] absolute right-2.5 top-3 pointer-events-none" />
          </div>

          {isDirty && (
            <span className="text-[11px] text-[#FFB74D] font-mono flex items-center gap-1 font-medium">
              ● Não salvo
            </span>
          )}
        </div>

        {/* Action Buttons */}
        <div className="flex items-center gap-2">
          {/* Images Gallery Toggle */}
          <button
            onClick={() => setShowGallery(!showGallery)}
            className={`flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-medium transition-colors ${
              showGallery
                ? 'bg-[#4F378B] text-[#EADDFF]'
                : 'bg-[#1C1B1F] text-[#CAC4D0] hover:text-white border border-white/10'
            }`}
          >
            <ImageIcon className="w-3.5 h-3.5 text-[#D0BCFF]" />
            <span>{currentScript.images?.length || 0} imagens</span>
          </button>

          {/* Save Button */}
          <button
            onClick={handleSave}
            disabled={!isDirty}
            className="flex items-center gap-1 px-3 py-1.5 rounded-xl bg-[#4A4458] text-[#E8DEF8] hover:bg-[#5C556C] disabled:opacity-40 text-xs font-medium transition-colors"
          >
            <Save className="w-3.5 h-3.5" /> Salvar
          </button>

          {/* Save & Run Button */}
          <button
            onClick={handleSaveAndRun}
            className="flex items-center gap-1.5 px-3.5 py-1.5 rounded-xl bg-[#D0BCFF] text-[#381E72] hover:bg-[#EADDFF] text-xs font-semibold shadow-md transition-colors"
          >
            <Play className="w-3.5 h-3.5 fill-current" /> Rodar
          </button>
        </div>
      </div>

      {/* Snippet Bar (15 Quick Snippets) */}
      <div className="flex items-center gap-1.5 overflow-x-auto py-1 px-1 mb-2 scrollbar-none select-none shrink-0">
        <span className="text-[10px] uppercase font-bold text-[#D0BCFF] mr-1 shrink-0 flex items-center gap-1">
          <Sparkles className="w-3 h-3" /> Snippets:
        </span>
        {SNIPPETS.map((snippet) => (
          <button
            key={snippet.label}
            onClick={() => insertSnippet(snippet.code)}
            className="px-2.5 py-1 rounded-lg bg-[#2B2930] hover:bg-[#4F378B]/50 border border-white/5 text-[11px] font-mono text-[#E6E1E5] whitespace-nowrap active:scale-95 transition-transform"
          >
            {snippet.label}
          </button>
        ))}
      </div>

      {/* Main Workspace Area: Code Editor + Image Gallery Drawer */}
      <div className="flex-1 flex flex-col md:flex-row gap-2 min-h-0">
        {/* Code Editor */}
        <div className="flex-1 bg-[#1C1B1F] rounded-2xl border border-white/10 overflow-hidden flex flex-col shadow-inner relative">
          <div className="bg-[#2B2930]/60 px-4 py-1.5 border-b border-white/5 flex justify-between items-center text-[10px] text-[#CAC4D0] font-mono">
            <span>main.lua • UTF-8</span>
            <span>{code.split('\n').length} linhas</span>
          </div>
          <textarea
            ref={textareaRef}
            value={code}
            onChange={(e) => handleCodeChange(e.target.value)}
            spellCheck={false}
            placeholder="-- Digite seu script Lua aqui..."
            className="flex-1 w-full bg-transparent text-[#E6E1E5] font-mono text-xs sm:text-sm p-4 resize-none focus:outline-hidden leading-relaxed selection:bg-[#4F378B] selection:text-white"
          />
        </div>

        {/* Images Drawer / Gallery */}
        {showGallery && (
          <div className="w-full md:w-72 bg-[#2B2930] rounded-2xl border border-white/10 p-3.5 flex flex-col gap-3 shadow-xl max-h-80 md:max-h-full overflow-y-auto shrink-0">
            <div className="flex items-center justify-between">
              <div className="text-xs uppercase font-bold text-[#D0BCFF]">
                Imagens do Script
              </div>
              <button
                onClick={() => fileInputRef.current?.click()}
                className="p-1 rounded-lg bg-[#4F378B] text-[#EADDFF] hover:bg-[#6750A4] text-xs flex items-center gap-1 px-2"
                title="Adicionar imagem"
              >
                <Plus className="w-3.5 h-3.5" /> Adicionar
              </button>
              <input
                type="file"
                ref={fileInputRef}
                onChange={handleFileUpload}
                accept="image/*"
                className="hidden"
              />
            </div>

            <p className="text-[11px] text-[#CAC4D0] leading-tight">
              Toque numa imagem para inserir seu nome no código Lua na posição do cursor.
            </p>

            {/* Images Grid */}
            {(!currentScript.images || currentScript.images.length === 0) ? (
              <div className="text-center py-6 text-xs text-[#938F99] border border-dashed border-white/10 rounded-xl">
                Nenhuma imagem adicionada.
              </div>
            ) : (
              <div className="grid grid-cols-2 gap-2">
                {currentScript.images.map((img) => (
                  <div
                    key={img.name}
                    className="group relative bg-[#1C1B1F] rounded-xl p-2 border border-white/5 hover:border-[#D0BCFF] flex flex-col items-center gap-1.5 cursor-pointer transition-all"
                    onClick={() => insertImageName(img.name)}
                  >
                    {/* Thumbnail preview */}
                    <div className="w-full h-16 rounded-lg bg-black/40 flex items-center justify-center overflow-hidden">
                      <img
                        src={img.dataUrl}
                        alt={img.name}
                        className="max-h-full max-w-full object-contain pointer-events-none"
                      />
                    </div>
                    <span className="text-[10px] font-mono text-[#E6E1E5] truncate w-full text-center">
                      {img.name}
                    </span>

                    {/* Delete button */}
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        setImageToDelete(img.name);
                      }}
                      className="absolute top-1 right-1 p-1 rounded-full bg-[#BA1A1A] text-white opacity-0 group-hover:opacity-100 transition-opacity"
                    >
                      <Trash2 className="w-3 h-3" />
                    </button>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </div>

      {/* Delete Image Confirmation */}
      {imageToDelete && (
        <ConfirmDialog
          title="Remover Imagem"
          message={`Tem certeza que deseja remover '${imageToDelete}' deste script?`}
          confirmLabel="Remover"
          onDismiss={() => setImageToDelete(null)}
          onConfirm={() => {
            onDeleteImage(currentScript.name, imageToDelete);
            setImageToDelete(null);
          }}
        />
      )}
    </div>
  );
};
