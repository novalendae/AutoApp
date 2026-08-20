# KaizenAuto (Web / React)

Automação por **visão computacional + scripts Lua**, reescrito em **React 18 + TypeScript + Tailwind CSS** no espírito do AnkuLua/Sikuli.

## Recursos Portados

- **Scripts Lua:** Execução de scripts Lua com editor integrado, suporte a snippets, galeria de imagens e controle em tempo real (Iniciar, Pausar, Parar).
- **Visão Computacional:** Matching de padrões, multiescala, regiões de interesse (`Region`), coordenadas e OCR para leitura e clique em textos.
- **Bot com Self-Healing:** Cascata adaptativa de recuperação (`RELAX_THRESHOLD`, `LEARNED_VARIANT`, `WIDE_MULTISCALE`, `ORB_FEATURES`, `OCR_TEXT`, `A11Y_TREE`) e registro de memória de padrões (`PatternMemory`).
- **Simulador de Dispositivo Virtual:** Visualização em tempo real da tela alvo, cliques, toques, swipes e detecção visual.
- **Logs e Métricas:** Console com filtros (`TUDO`, `ERROS`, `CURA`), estatísticas de confiabilidade e auditoria de eventos de cura.

## Executando o Projeto

```bash
npm install
npm run dev
```

Acesse em `http://localhost:3000`.
