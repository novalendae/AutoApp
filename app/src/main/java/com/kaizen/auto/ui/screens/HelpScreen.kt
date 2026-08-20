package com.kaizen.auto.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kaizen.auto.ui.theme.MonoStyle

private data class ApiGroup(val title: String, val entries: List<Pair<String, String>>)

private val API_REFERENCE = listOf(
    ApiGroup(
        "Começando",
        listOf(
            "Settings:setScriptDimension(true, 1280)" to
                "Diz em que resolução as imagens foram recortadas. Ponha no topo do script — é isso que faz ele funcionar em telas diferentes.",
            "Settings:setSimilarity(0.8)" to "Similaridade padrão (0 a 1). 0.8 é um bom começo.",
            "Settings:setAutoWaitTimeout(3)" to "Segundos que click()/find() esperam a imagem aparecer.",
            "Settings:setMaxRuntimeMinutes(30)" to "Trava de segurança: para o script sozinho.",
        ),
    ),
    ApiGroup(
        "Procurar",
        listOf(
            "find(\"img.png\")" to "Procura e devolve o Match. Erro se não achar.",
            "exists(\"img.png\", 0)" to "Devolve o Match ou nil. Timeout 0 = olha uma vez só, ideal em loops.",
            "wait(\"img.png\", 10)" to "Espera até 10s pela imagem. Erro se estourar.",
            "waitVanish(\"loading.png\", 30)" to "Espera a imagem sumir.",
            "findAll(\"item.png\")" to "Lista com todos os matches na tela.",
            "getLastMatch()" to "Último match encontrado.",
        ),
    ),
    ApiGroup(
        "Tocar",
        listOf(
            "click(\"botao.png\")" to "Espera a imagem e toca nela.",
            "click(Location(500, 900))" to "Toca em coordenada exata.",
            "doubleClick(\"img.png\")" to "Toque duplo.",
            "longClick(\"img.png\", 800)" to "Toque longo de 800ms.",
            "waitClick(\"img.png\", 10)" to "Espera até 10s e clica.",
            "existsClick(\"img.png\", 2)" to "Clica só se aparecer em 2s; devolve true/false. Não dá erro.",
            "tap(x, y)" to "Toque cru em pixels da tela.",
        ),
    ),
    ApiGroup(
        "Gestos",
        listOf(
            "swipe(Location(500,1500), Location(500,500), 400)" to "Arrasta em 400ms.",
            "humanSwipe(a, b, 500)" to "Arrasto com curva e velocidade irregular — parece humano.",
            "dragDrop(origem, destino)" to "Segura, arrasta e solta.",
            "pinch(cx, cy, deIni, paraFim)" to "Pinça para zoom.",
            "back() / home() / recents()" to "Botões do sistema.",
        ),
    ),
    ApiGroup(
        "Texto (OCR, sem recortar imagem)",
        listOf(
            "findText(\"Continuar\")" to "Acha o texto na tela e devolve o Match.",
            "clickText(\"Continuar\")" to "Acha e toca no texto.",
            "readText()" to "Devolve tudo que está escrito na tela.",
        ),
    ),
    ApiGroup(
        "Regiões e padrões",
        listOf(
            "Region(0, 0, 720, 400)" to "Recorte da tela. Buscar dentro dele é muito mais rápido.",
            "r:exists(\"img.png\", 0)" to "Procura só dentro da região.",
            "r:click(\"img.png\")" to "Clica dentro da região.",
            "Pattern(\"img.png\"):similar(0.9)" to "Ajusta a exigência dessa imagem específica.",
            "Pattern(\"img.png\"):targetOffset(0, 40)" to "Clica 40px abaixo do centro da imagem.",
            "Location(x, y)" to "Um ponto na tela.",
        ),
    ),
    ApiGroup(
        "Controle e tempo",
        listOf(
            "sleep(1.5)" to "Pausa interrompível. O botão PARAR sempre funciona.",
            "waitMsRandom(500, 1500)" to "Pausa aleatória — evita padrão robótico.",
            "shouldStop()" to "true quando você pediu para parar. Use em while: while not shouldStop() do ... end",
            "scriptExit(\"acabou\")" to "Encerra o script na hora, com mensagem.",
            "setStopMessage(\"msg\")" to "Mensagem exibida ao parar.",
        ),
    ),
    ApiGroup(
        "Diagnóstico",
        listOf(
            "log(\"texto\") / logWarn / logError" to "Escreve na aba Logs.",
            "toast(\"texto\")" to "Balãozinho na tela.",
            "saveScreenshot(\"nome\")" to "Salva um print para você conferir depois.",
            "getScreenWidth() / getScreenHeight()" to "Tamanho real da tela.",
        ),
    ),
    ApiGroup(
        "Self-healing dentro do script",
        listOf(
            "heal.off()" to "Desliga a cura automática num trecho crítico onde clique errado é caro.",
            "heal.on()" to "Liga de volta.",
            "heal.noLearn()" to "Continua curando, mas sem salvar variações.",
            "heal.remember(\"img.png\")" to "Mostra o que o bot já aprendeu sobre a imagem.",
        ),
    ),
)

private val EXAMPLE = """
-- Recorte as imagens numa tela de 1280 de lado maior:
Settings:setScriptDimension(true, 1280)
Settings:setSimilarity(0.80)

log("Iniciando")

while not shouldStop() do
    -- Fecha popups que aparecem do nada
    if existsClick("fechar.png", 0) then
        log("popup fechado")
    end

    -- Ação principal
    if existsClick("botao_jogar.png", 1) then
        wait("tela_partida.png", 15)
        log("partida iniciada")
        waitVanish("carregando.png", 60)
    else
        logWarn("botao nao apareceu, tentando de novo")
        back()
    end

    waitMsRandom(800, 1600)
end

log("Encerrado pelo usuario")
""".trimIndent()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen() {
    Scaffold(topBar = { TopAppBar(title = { Text("Guia da API Lua") }) }) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Como funciona", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Você recorta pedacinhos da tela do app-alvo (um botão, um ícone), " +
                                "importa como imagem no script e manda clicar neles. " +
                                "A sintaxe é a mesma ideia do AnkuLua/Sikuli.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            items(API_REFERENCE) { group -> ApiGroupCard(group) }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Exemplo completo", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            EXAMPLE,
                            style = MonoStyle,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(6.dp),
                                )
                                .padding(10.dp),
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ApiGroupCard(group: ApiGroup) {
    var expanded by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .clickable { expanded = !expanded }
                .padding(14.dp),
        ) {
            Row {
                Text(
                    group.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(if (expanded) "−" else "+", style = MaterialTheme.typography.titleMedium)
            }
            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    group.entries.forEach { (code, desc) ->
                        Text(
                            code,
                            style = MonoStyle,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Text(
                            desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
