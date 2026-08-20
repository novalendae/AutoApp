import { ScriptEntry, ScriptImage } from '../types';

function createSvgDataUrl(text: string, bgColor: string = '#4F378B', textColor: string = '#FFFFFF', width: number = 160, height: number = 60): string {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}">
    <rect width="${width}" height="${height}" rx="8" fill="${bgColor}"/>
    <text x="50%" y="55%" dominant-baseline="middle" text-anchor="middle" fill="${textColor}" font-family="sans-serif" font-weight="bold" font-size="16">${text}</text>
  </svg>`;
  return `data:image/svg+xml;utf8,${encodeURIComponent(svg)}`;
}

export const SAMPLE_IMAGES_BASIC: ScriptImage[] = [
  {
    name: 'botao_jogar.png',
    dataUrl: createSvgDataUrl('JOGAR', '#00BFA5', '#FFFFFF', 140, 50),
    sizeBytes: 1420,
    width: 140,
    height: 50,
  },
  {
    name: 'fechar.png',
    dataUrl: createSvgDataUrl('✕', '#BA1A1A', '#FFFFFF', 44, 44),
    sizeBytes: 850,
    width: 44,
    height: 44,
  },
  {
    name: 'icone_config.png',
    dataUrl: createSvgDataUrl('⚙ Config', '#4A4458', '#EADDFF', 120, 48),
    sizeBytes: 1280,
    width: 120,
    height: 48,
  },
];

export const SAMPLE_IMAGES_COMBAT: ScriptImage[] = [
  {
    name: 'iniciar_batalha.png',
    dataUrl: createSvgDataUrl('BATALHAR', '#FF8F00', '#FFFFFF', 160, 56),
    sizeBytes: 1650,
    width: 160,
    height: 56,
  },
  {
    name: 'recompensa.png',
    dataUrl: createSvgDataUrl('★ RECOMPENSA ★', '#FFD54F', '#000000', 180, 50),
    sizeBytes: 1890,
    width: 180,
    height: 50,
  },
  {
    name: 'carregando.png',
    dataUrl: createSvgDataUrl('Carregando...', '#37474F', '#B0BEC5', 150, 40),
    sizeBytes: 1100,
    width: 150,
    height: 40,
  },
];

export const DEFAULT_TEMPLATE = `-- {{NAME}}
-- Script criado no KaizenAuto

-- Resolução de referência: se você criou as imagens num aparelho
-- diferente, o app redimensiona os templates automaticamente.
Settings:setScriptDimension(true, 1080)

-- Para o script sozinho depois de 30 minutos (segurança de bateria).
Settings:setMaxRuntimeMinutes(30)

log("Script iniciado!")

-- Exemplo: clicar num botão quando ele aparecer
-- if existsClick("botao.png", 5) then
--     toast("Cliquei no botão")
-- end

log("Fim.")`;

export const INITIAL_SCRIPTS: ScriptEntry[] = [
  {
    name: 'exemplo_basico',
    lastModified: Date.now() - 3600000 * 2,
    sizeBytes: 1450,
    imageCount: 3,
    images: SAMPLE_IMAGES_BASIC,
    code: `-- ============================================
--  Exemplo básico — KaizenAuto
--  Mostra as funções mais usadas no dia a dia.
-- ============================================

Settings:setScriptDimension(true, 1080)
Settings:setAutoWaitTimeout(5)
Settings:setMaxRuntimeMinutes(10)

-- O bot de self-healing começa ligado; para desligar: heal.off()
heal.on()

log("Tela: " .. getScreenWidth() .. "x" .. getScreenHeight())
toast("Começando em 2 segundos...")
sleep(2)

-- 1) Clicar por TEXTO (usa OCR, não precisa de imagem)
if clickText("Configurações") then
    log("Abri as configurações pelo texto na tela")
    sleep(1)
    back()
end

-- 2) Clicar por IMAGEM (coloque o arquivo em images/)
if existsClick("botao_jogar.png", 5) then
    log("Cliquei em jogar")
end

-- 3) Buscar dentro de uma região específica (mais rápido)
local topo = Region(0, 0, getScreenWidth(), 300)
if topo:exists("fechar.png") then
    click(getLastMatch())
end

-- 4) Laço seguro: sempre dá para parar pelo botão do app
local contador = 0
while contador < 3 do
    if shouldStop() then break end
    contador = contador + 1
    toast("Volta " .. contador .. " de 3")
    sleep(1)
end

-- 5) Gestos
-- swipe(500, 1500, 500, 500, 400)   -- rolar para cima
-- humanSwipe(500, 1500, 500, 500)   -- versão com curva natural

setStopMessage("Exemplo finalizado com sucesso")
log("Execução do exemplo_basico concluída!")`,
  },
  {
    name: 'coleta_diaria',
    lastModified: Date.now() - 3600000 * 12,
    sizeBytes: 1120,
    imageCount: 3,
    images: SAMPLE_IMAGES_COMBAT,
    code: `-- Coleta diária automatizada
Settings:setScriptDimension(true, 1080)
Settings:setAutoWaitTimeout(3)
Settings:setSimilarity(0.8)

log("Iniciando rotina de coleta...")
toast("Iniciando coleta diária...")

while not shouldStop() do
    -- Fecha eventuais popups de aviso
    if existsClick("fechar.png", 1) then
        log("Fechou popup promocional")
        sleep(1)
    end

    -- Clica no botão de batalha se disponível
    if existsClick("iniciar_batalha.png", 2) then
        log("Iniciou batalha com sucesso")
        waitVanish("carregando.png", 15)
        sleep(2)
    end

    -- Recolhe recompensa
    if existsClick("recompensa.png", 2) then
        toast("★ Recompensa resgatada!")
        log("Recompensa resgatada!")
    end

    sleep(1)
    break
end

setStopMessage("Coleta concluída")
log("Fim do script.")`,
  },
];
