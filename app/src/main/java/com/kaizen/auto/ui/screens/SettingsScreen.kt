package com.kaizen.auto.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kaizen.auto.ui.AppViewModel
import com.kaizen.auto.ui.components.RequirementRow
import com.kaizen.auto.ui.components.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel) {
    val readiness by vm.readiness.collectAsState()
    val healing by vm.healingEnabled.collectAsState()
    val learn by vm.learnVariants.collectAsState()
    val observer by vm.passiveObserver.collectAsState()
    val humanize by vm.humanize.collectAsState()
    val maxRuntime by vm.maxRuntime.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("Ajustes") }) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionHeader("Permissões")
            RequirementRow(
                title = "Acessibilidade",
                description = if (readiness.accessibilityOn) "Ativa — o robô pode tocar na tela." else "Sem ela, nada é clicado.",
                satisfied = readiness.accessibilityOn,
                actionLabel = "Ativar",
                onAction = { vm.openAccessibilitySettings() },
            )
            RequirementRow(
                title = "Captura de tela",
                description = if (readiness.captureOn) "Ativa — o robô enxerga." else "Sem ela, nenhuma imagem é encontrada.",
                satisfied = readiness.captureOn,
                actionLabel = "Permitir",
                onAction = { vm.prepareCapture() },
            )
            RequirementRow(
                title = "Sobrepor outros apps",
                description = "Opcional. Permite destaques visuais na tela (highlight).",
                satisfied = readiness.overlayOn,
                actionLabel = "Permitir",
                onAction = { vm.openOverlaySettings() },
            )

            SectionHeader("Execução")
            ToggleRow(
                title = "Humanizar toques",
                subtitle = "Adiciona pequenas variações de posição e tempo, deixando os gestos menos robóticos.",
                checked = humanize,
                onChange = vm::setHumanize,
            )

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("Tempo máximo de execução", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (maxRuntime == 0) {
                            "Sem limite — o script só para quando você mandar ou quando ele terminar."
                        } else {
                            "Para sozinho depois de $maxRuntime minuto(s)."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Slider(
                        value = maxRuntime.toFloat(),
                        onValueChange = { vm.setMaxRuntime(it.toInt()) },
                        valueRange = 0f..240f,
                        steps = 47,
                    )
                }
            }

            SectionHeader("Bot / Self-healing")
            ToggleRow(
                title = "Self-healing",
                subtitle = "Quando uma imagem não é achada, o bot tenta relaxar o limiar, outras escalas, ORB, OCR e a árvore de acessibilidade antes de desistir.",
                checked = healing,
                onChange = vm::setHealing,
            )
            ToggleRow(
                title = "Aprender variações",
                subtitle = "Salva um recorte da tela quando conseguir curar, para acertar de primeira da próxima vez.",
                checked = learn,
                onChange = vm::setLearnVariants,
                enabled = healing,
            )
            ToggleRow(
                title = "Observação passiva",
                subtitle = "Mapeia botões e textos das telas em segundo plano para ajudar a localizar elementos quando a imagem muda. Campos de senha são ignorados.",
                checked = observer,
                onChange = vm::setPassiveObserver,
            )

            SectionHeader("Sobre")
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("KaizenAuto 1.0", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Automação por imagem com scripts Lua, sem root. " +
                            "Toques via Acessibilidade, visão via MediaProjection + OpenCV, " +
                            "texto via ML Kit offline. Tudo roda no aparelho.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
        }
    }
}
