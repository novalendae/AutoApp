# KaizenAuto

Automação Android por **visão computacional + scripts Lua**, no espírito do AnkuLua/Sikuli — **sem root**.

- **Toques e gestos:** `AccessibilityService.dispatchGesture()`
- **Captura de tela:** `MediaProjection` (API 24+) e `takeScreenshot()` do Accessibility (API 30+)
- **Visão:** OpenCV (template matching multi-escala + ORB) e ML Kit (OCR) — tudo **offline**
- **Scripts:** Lua via LuaJ 3.0.1, com editor, galeria de imagens e logs dentro do app
- **Bot (extra):** aprendizado passivo + *self-healing* em cascata quando um `find` falha
- **Controle explícito:** botão **Iniciar / Pausar / Parar** + limite de tempo de execução. Nada roda para sempre.

---

## 1. Abrir e gerar o APK

```bash
# Android Studio: File ▸ Open ▸ selecione a pasta KaizenAuto
# ou pela linha de comando:
cd KaizenAuto
./gradlew assembleDebug
# APK em: app/build/outputs/apk/debug/app-debug.apk
```

Requisitos: **JDK 17**, Android SDK 34. O wrapper (Gradle 8.7) já está incluído — não precisa instalar Gradle.

| Item | Versão |
|---|---|
| Pacote | `com.kaizen.auto` |
| minSdk / target / compile | 24 / 34 / 34 |
| Kotlin / AGP / KSP | 2.0.20 / 8.5.2 / 2.0.20-1.0.25 |
| UI | Jetpack Compose (BOM 2024.09.03) + Material 3 |
| Dados | Room 2.6.1 |
| Visão | `org.opencv:opencv:4.9.0` · `mlkit:text-recognition:16.0.1` |
| Lua | `org.luaj:luaj-jse:3.0.1` |

O primeiro build baixa ~300 MB de dependências (OpenCV é grande). Depois disso é rápido.

## 2. Primeiro uso no aparelho

1. Abra o app → aba **Scripts**. O painel do topo mostra o que falta liberar.
2. **Acessibilidade** → toque em *Ativar* → ligue "KaizenAuto" na lista. (É isso que permite os toques.)
3. **Captura de tela** → toque em *Preparar*: o Android pede a permissão de gravação. Aceite.
4. *(Opcional)* **Sobreposição** — só para o indicador flutuante.
5. Escolha um script e toque em **▶**.

O app instala dois scripts de exemplo na primeira execução, com comentários explicando cada função.

Os scripts vivem em `Android/data/com.kaizen.auto/files/scripts/<nome>/`:

```
scripts/
  MeuBot/
    main.lua
    images/
      botao_ok.png
      tela_inicial.png
```

Você pode adicionar imagens pela galeria do editor (importar da galeria do celular) ou soltar PNGs direto nessa pasta pelo gerenciador de arquivos.

## 3. A API Lua

### Busca

```lua
find("botao.png")                -- erro se não achar
exists("botao.png")              -- false se não achar (timeout 0 = instantâneo)
exists("botao.png", 5)           -- procura por até 5 s
wait("tela.png", 10)             -- espera aparecer; erro se estourar
waitVanish("carregando.png", 30) -- espera sumir
findAll("moeda.png")             -- tabela de Matches
```

### Clique

```lua
click("botao.png")            -- procura e clica; erro se não achar
click("botao.png", 5)         -- com timeout
doubleClick("icone.png")
waitClick("ok.png", 10)       -- espera aparecer e clica
existsClick("popup_x.png")    -- clica SE existir; retorna true/false
longClick("item.png", 1200)   -- pressão longa (ms)
tap(540, 1200)                -- coordenada crua
```

### Texto (OCR — sem precisar recortar imagem)

```lua
findText("Continuar")
clickText("Continuar", 5)
local tudo = readText()              -- tela inteira
local parte = readText(Region(0,0,1080,300))
```

### Gestos

```lua
swipe(500, 1500, 500, 400, 350)
humanSwipe(500, 1500, 500, 400)      -- curva de Bézier, parece humano
dragDrop(Location(100,100), Location(800,900))
pinch(540, 1000, 100, 500)           -- abre o zoom
back()  home()  recents()
openApp("com.whatsapp")
```

### Tempo e fluxo

```lua
sleep(1.5)          waitMs(300)        waitMsRandom(200, 800)
if shouldStop() then return end        -- respeita o botão PARAR
setStopMessage("Terminou a rodada")
scriptExit("acabou")
```

Toda função que espera é **interrompível**: o botão Parar funciona no mesmo instante, mesmo dentro de um `sleep(60)`.

### Objetos

```lua
local r = Region(0, 800, 1080, 600)
r:click("botao.png")        r:exists("x.png")     r:getCenter()

local p = Pattern("botao.png"):similar(0.92):targetOffset(0, -20)
click(p)

local m = getLastMatch()
print(m.score, m.scale, m.strategy, m.targetX, m.targetY)
```

### Settings (sempre no topo do script)

```lua
Settings:setScriptDimension(true, 1280)   -- resolução em que você recortou as imagens
Settings:setCompareDimension(true, 1280)  -- normaliza a tela antes de comparar
Settings:setSimilarity(0.85)
Settings:setAutoWaitTimeout(5)
Settings:setClickDelay(0.25)
Settings:setHumanize(true)                -- jitter de 3 px / 25 ms nos toques
Settings:setMaxRuntimeMinutes(30)         -- trava de segurança
```

As duas primeiras linhas são o que faz o mesmo script funcionar em telas de resolução diferente. Use-as.

### Saída

```lua
toast("oi")   log("info")   logWarn("cuidado")   logError("falhou")
print(x, y)                       -- vai para a aba Logs
saveScreenshot("debug/erro.png")
getScreenWidth()  getScreenHeight()
```

### Self-healing dentro do script

```lua
heal.on()                    heal.off()          heal.isOn()
heal.learn()                 heal.noLearn()
heal.remember("botao.png")   -- salva o recorte atual como variante boa
```

## 4. Como o bot funciona

**Aprendizado passivo** — enquanto o script roda, o app registra onde cada imagem costuma ser encontrada, com que score, em que escala e com que estratégia. Isso vira memória (`PatternMemory`) usada para procurar primeiro no lugar certo: buscas ficam mais rápidas e mais estáveis com o uso.

**Self-healing** — quando um `find` falha, entra uma cascata de táticas, na ordem:

1. `RELAX_THRESHOLD` — afrouxa o limiar até o mínimo histórico de sucesso (piso 0.55)
2. `LEARNED_VARIANT` — tenta os recortes que já funcionaram antes
3. `WIDE_MULTISCALE` — varre uma faixa de escalas bem maior
4. `ORB_FEATURES` — matching por pontos-chave, aguenta a UI mudar de aparência
5. `OCR_TEXT` — lê o texto do botão em vez da imagem
6. `A11Y_TREE` — procura o elemento na árvore de acessibilidade pelo rótulo

Achou? Registra o evento, salva a variante e segue. Não achou? Loga a falha com screenshot para você ver o que aconteceu na aba **Bot**.

Tudo isso é local. Nenhuma chamada de rede, nenhuma LLM na nuvem.

## 5. Abas do app

| Aba | Para quê |
|---|---|
| **Scripts** | Lista, cria, renomeia, duplica e executa. Painel de permissões no topo. |
| **Editor** | Código + galeria de imagens do script + barra com 15 snippets prontos. |
| **Logs** | Saída ao vivo do script (400 linhas), com nível e horário. |
| **Bot** | Eventos de self-healing e a memória aprendida. Dá para zerar tudo. |
| **Ajustes** | Liga/desliga healing, aprendizado, observador passivo, humanização, tempo máximo. |
| **Guia** | Referência da API dentro do próprio app. |

## 6. Estrutura

```
app/src/main/java/com/kaizen/auto/
├── core/
│   ├── capture/     MediaProjection, Accessibility screenshot, cache de frame (120 ms)
│   ├── input/       dispatchGesture: tap, swipe, drag, pinch, Bézier
│   └── vision/      TemplateMatcher (multi-escala + ORB), OcrEngine, VisionEngine
├── service/         AccessibilityService, AutomationService (foreground), ProjectionRequestActivity
├── data/            Room (memória, eventos, observações, logs) + ScriptRepository
├── healing/         HealingEngine (cascata) + PassiveObserver
├── runtime/         ScriptController (start/stop/pause) + ScriptRunner + lua/LuaApi
└── ui/              Compose: 6 telas, tema, componentes
```

## 7. Notas de segurança e limites

- O script roda em thread daemon com `debug.sethook` a cada 2000 instruções — **parada garantida** mesmo em `while true do end`.
- Sandbox Lua: `io`, `dofile` e `loadfile` são `nil`; de `os` sobram só `time`, `clock`, `date`, `difftime`.
- O serviço em foreground usa `START_NOT_STICKY`: se o sistema matar o app, ele **não** ressuscita sozinho.
- WakeLock com teto de 4 h e `setMaxRuntimeMinutes` como trava adicional.
- Automação por acessibilidade é detectável. Não use em apps cujos termos proíbam isso.
