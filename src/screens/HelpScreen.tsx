import React from 'react';
import {
  HelpCircle,
  ChevronDown,
  ChevronUp,
  Search,
  Hand,
  Type,
  Move,
  Maximize2,
  Clock,
  Terminal,
  Brain,
  Copy,
  Check,
} from 'lucide-react';

interface HelpSection {
  id: string;
  title: string;
  icon: React.ComponentType<{ className?: string }>;
  description: string;
  examples: { code: string; explanation: string }[];
}

const HELP_SECTIONS: HelpSection[] = [
  {
    id: 'start',
    title: '1. Começando e Configurações',
    icon: HelpCircle,
    description: 'Comandos colocados no topo do script para calibrar dimensões e limites.',
    examples: [
      {
        code: 'Settings:setScriptDimension(true, 1080)',
        explanation: 'Define a resolução do aparelho onde você tirou os prints das imagens.',
      },
      {
        code: 'Settings:setAutoWaitTimeout(5)',
        explanation: 'Tempo padrão de espera (em segundos) para comandos com espera implícita.',
      },
      {
        code: 'Settings:setSimilarity(0.85)',
        explanation: 'Limiar padrão de similaridade (0.0 a 1.0) para busca por imagem.',
      },
      {
        code: 'Settings:setMaxRuntimeMinutes(30)',
        explanation: 'Trava de segurança que para o script automaticamente após X minutos.',
      },
    ],
  },
  {
    id: 'search',
    title: '2. Procurar Imagens',
    icon: Search,
    description: 'Funções para localizar botões e imagens na tela.',
    examples: [
      {
        code: 'exists("botao.png", 5)',
        explanation: 'Procura por até 5s. Retorna true se encontrar, false se não achar.',
      },
      {
        code: 'find("botao.png")',
        explanation: 'Busca a imagem imediatamente. Lança erro no script se não encontrar.',
      },
      {
        code: 'wait("tela_carregada.png", 10)',
        explanation: 'Espera a imagem aparecer na tela por até 10 segundos.',
      },
      {
        code: 'waitVanish("carregando.png", 30)',
        explanation: 'Espera a imagem sumir da tela (útil para telas de loading).',
      },
      {
        code: 'local m = getLastMatch()\nprint(m.score, m.targetX, m.targetY)',
        explanation: 'Obtém detalhes do último resultado encontrado com sucesso.',
      },
    ],
  },
  {
    id: 'touch',
    title: '3. Tocar e Clicar',
    icon: Hand,
    description: 'Interações de toque na tela por imagem ou coordenadas.',
    examples: [
      {
        code: 'click("botao.png")',
        explanation: 'Encontra a imagem na tela e dispara o toque central.',
      },
      {
        code: 'waitClick("ok.png", 10)',
        explanation: 'Espera a imagem aparecer e clica assim que ela surgir.',
      },
      {
        code: 'existsClick("fechar.png", 2)',
        explanation: 'Clica na imagem apenas SE ela existir; retorna true/false.',
      },
      {
        code: 'doubleClick("icone.png")',
        explanation: 'Realiza toque duplo rápido na imagem.',
      },
      {
        code: 'longClick("item.png", 1200)',
        explanation: 'Toque longo pressionando pelo tempo informado em milissegundos.',
      },
      {
        code: 'tap(540, 1200)',
        explanation: 'Toque direto nas coordenadas brutas X e Y.',
      },
    ],
  },
  {
    id: 'text',
    title: '4. Texto e OCR (Offline)',
    icon: Type,
    description: 'Reconhecimento de caracteres para ler e clicar em textos sem recortar imagens.',
    examples: [
      {
        code: 'clickText("Continuar", 5)',
        explanation: 'Lê a tela com OCR e toca diretamente na palavra "Continuar".',
      },
      {
        code: 'findText("Entrar")',
        explanation: 'Busca o texto e retorna as coordenadas do elemento.',
      },
      {
        code: 'local texto = readText()',
        explanation: 'Lê todo o texto visível na tela atual e armazena numa variável string.',
      },
    ],
  },
  {
    id: 'gestures',
    title: '5. Gestos e Sistema',
    icon: Move,
    description: 'Gestos de deslizar, arrastar e navegação do Android.',
    examples: [
      {
        code: 'swipe(500, 1500, 500, 400, 350)',
        explanation: 'Arrasto linear de (x1, y1) até (x2, y2) com duração de 350ms.',
      },
      {
        code: 'humanSwipe(500, 1500, 500, 400)',
        explanation: 'Arrasto com curva Bézier e aceleração que imita toque de mão humana.',
      },
      {
        code: 'back()   home()   recents()',
        explanation: 'Dispara os botões de navegação Voltar, Início ou Recentes do Android.',
      },
      {
        code: 'openApp("com.supercell.clashofclans")',
        explanation: 'Abre o aplicativo ou jogo informado pelo nome do pacote Android.',
      },
    ],
  },
  {
    id: 'regions',
    title: '6. Regiões e Padrões Avançados',
    icon: Maximize2,
    description: 'Restringir a busca a áreas da tela e configurar offsets.',
    examples: [
      {
        code: 'local topo = Region(0, 0, 1080, 400)\ntopo:click("fechar.png")',
        explanation: 'Busca a imagem apenas no topo da tela, economizando processamento.',
      },
      {
        code: 'local p = Pattern("botao.png"):similar(0.92):targetOffset(0, -30)\nclick(p)',
        explanation: 'Define alta similaridade e clica 30 pixels acima do centro.',
      },
    ],
  },
  {
    id: 'flow',
    title: '7. Controle de Tempo e Laços',
    icon: Clock,
    description: 'Controle de repetição seguro e interrompível.',
    examples: [
      {
        code: 'sleep(1.5)   waitMs(300)   waitMsRandom(200, 800)',
        explanation: 'Pausa a execução (tempo sempre interrompível pelo botão Parar).',
      },
      {
        code: 'while not shouldStop() do\n    -- rotina\n    sleep(1)\nend',
        explanation: 'Laço seguro que obedece ao botão Parar do aplicativo.',
      },
      {
        code: 'scriptExit("Concluído com sucesso")',
        explanation: 'Encerra a execução do script com uma mensagem de status.',
      },
    ],
  },
  {
    id: 'healing',
    title: '8. Self-Healing do Bot',
    icon: Brain,
    description: 'Comandos para controlar o aprendizado e tolerância a falhas do bot.',
    examples: [
      {
        code: 'heal.on()   heal.off()',
        explanation: 'Liga ou desliga a cascata de recuperação automática no script.',
      },
      {
        code: 'heal.remember("botao.png")',
        explanation: 'Salva a aparência atual da tela como variante visual boa para a imagem.',
      },
    ],
  },
];

export const HelpScreen: React.FC = () => {
  const [expandedSections, setExpandedSections] = React.useState<Record<string, boolean>>({
    start: true,
    search: true,
    touch: true,
  });
  const [copied, setCopied] = React.useState(false);

  const toggleSection = (id: string) => {
    setExpandedSections((prev) => ({
      ...prev,
      [id]: !prev[id],
    }));
  };

  const sampleFullCode = `-- Script completo de exemplo no KaizenAuto
Settings:setScriptDimension(true, 1080)
Settings:setAutoWaitTimeout(5)
Settings:setMaxRuntimeMinutes(15)

heal.on()
log("Iniciando rotina...")
toast("Começando em 2 segundos")
sleep(2)

if existsClick("botao_jogar.png", 4) then
    log("Partida iniciada")
    waitVanish("carregando.png", 20)
end

-- Rola a tela para baixo com gesto humanizado
humanSwipe(500, 1400, 500, 600)
sleep(1)

-- Clica no botão por texto
if clickText("Confirmar", 3) then
    toast("Confirmado!")
end

setStopMessage("Rotina finalizada!")`;

  const copyToClipboard = () => {
    navigator.clipboard.writeText(sampleFullCode);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="p-4 sm:p-6 max-w-4xl mx-auto space-y-4 pb-24">
      <div>
        <h2 className="text-xl font-bold text-white flex items-center gap-2">
          <HelpCircle className="w-5 h-5 text-[#D0BCFF]" />
          Guia de Referência da API Lua
        </h2>
        <p className="text-xs text-[#CAC4D0] mt-0.5">
          Documentação de todas as funções e objetos disponíveis nos scripts
        </p>
      </div>

      {/* Accordion List */}
      <div className="space-y-3">
        {HELP_SECTIONS.map((section) => {
          const isExpanded = expandedSections[section.id];
          const Icon = section.icon;

          return (
            <div
              key={section.id}
              className="bg-[#2B2930] rounded-2xl border border-white/5 overflow-hidden transition-all shadow-xs"
            >
              <button
                onClick={() => toggleSection(section.id)}
                className="w-full p-4 flex items-center justify-between text-left hover:bg-white/5 transition-colors"
              >
                <div className="flex items-center gap-3">
                  <div className="w-8 h-8 rounded-xl bg-[#4A4458] flex items-center justify-center text-[#D0BCFF] shrink-0">
                    <Icon className="w-4 h-4" />
                  </div>
                  <div>
                    <h3 className="text-sm font-bold text-white">{section.title}</h3>
                    <p className="text-xs text-[#CAC4D0] mt-0.5">{section.description}</p>
                  </div>
                </div>
                {isExpanded ? (
                  <ChevronUp className="w-4 h-4 text-[#CAC4D0]" />
                ) : (
                  <ChevronDown className="w-4 h-4 text-[#CAC4D0]" />
                )}
              </button>

              {isExpanded && (
                <div className="p-4 pt-0 space-y-2.5 border-t border-white/5">
                  {section.examples.map((ex, idx) => (
                    <div
                      key={idx}
                      className="bg-[#1C1B1F] rounded-xl p-3 border border-white/5 space-y-1.5"
                    >
                      <pre className="font-mono text-xs text-[#D0BCFF] whitespace-pre-wrap selection:bg-[#4F378B]">
                        {ex.code}
                      </pre>
                      <p className="text-xs text-[#CAC4D0] leading-snug">{ex.explanation}</p>
                    </div>
                  ))}
                </div>
              )}
            </div>
          );
        })}
      </div>

      {/* Full Sample Script Card */}
      <div className="bg-[#2B2930] rounded-2xl p-4 border border-white/5 space-y-3 mt-6 shadow-md">
        <div className="flex items-center justify-between">
          <h3 className="text-sm font-bold text-white flex items-center gap-2">
            <Terminal className="w-4 h-4 text-[#D0BCFF]" />
            Exemplo Completo de Script
          </h3>
          <button
            onClick={copyToClipboard}
            className="flex items-center gap-1 px-3 py-1.5 rounded-lg bg-[#4A4458] hover:bg-[#5C556C] text-xs text-[#E8DEF8] transition-colors"
          >
            {copied ? (
              <>
                <Check className="w-3.5 h-3.5 text-[#00BFA5]" /> Copiado
              </>
            ) : (
              <>
                <Copy className="w-3.5 h-3.5" /> Copiar Código
              </>
            )}
          </button>
        </div>

        <pre className="bg-[#1C1B1F] p-3.5 rounded-xl border border-white/5 font-mono text-xs text-[#E6E1E5] whitespace-pre-wrap leading-relaxed overflow-x-auto selection:bg-[#4F378B]">
          {sampleFullCode}
        </pre>
      </div>
    </div>
  );
};
