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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kaizen.auto.data.db.HealingEvent
import com.kaizen.auto.data.db.PatternMemory
import com.kaizen.auto.ui.AppViewModel
import com.kaizen.auto.ui.components.Chip
import com.kaizen.auto.ui.components.ConfirmDialog
import com.kaizen.auto.ui.components.EmptyState
import com.kaizen.auto.ui.components.SectionHeader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Vitrine do "cérebro" do bot: o que ele aprendeu sobre cada imagem e quais
 * consertos ele fez sozinho. Serve tanto de diagnóstico quanto de prova de que
 * o self-healing está fazendo alguma coisa útil.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearningScreen(vm: AppViewModel) {
    val memory by vm.patternMemory.collectAsState()
    val events by vm.healingEvents.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Aprendizado") },
                actions = {
                    IconButton(onClick = { confirmClear = true }) {
                        Icon(Icons.Default.Delete, "Zerar memória")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { SummaryCard(memory, events) }

            item { SectionHeader("Memória por imagem") }
            if (memory.isEmpty()) {
                item {
                    EmptyState(
                        "Nada aprendido ainda",
                        "Assim que um script procurar imagens, o bot começa a anotar o que funciona.",
                    )
                }
            }
            items(memory, key = { it.id }) { MemoryCard(it) }

            item { SectionHeader("Consertos automáticos") }
            if (events.isEmpty()) {
                item {
                    EmptyState(
                        "Nenhum conserto registrado",
                        "Quando uma busca falhar, o bot tenta outras táticas e registra aqui.",
                    )
                }
            }
            items(events, key = { it.id }) { HealingCard(it) }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "Zerar tudo que o bot aprendeu?",
            message = "Limiares ajustados, regiões memorizadas e variações salvas serão perdidos. Os scripts continuam intactos.",
            confirmLabel = "Zerar",
            onDismiss = { confirmClear = false },
            onConfirm = {
                vm.clearLearning()
                confirmClear = false
            },
        )
    }
}

@Composable
private fun SummaryCard(memory: List<PatternMemory>, events: List<HealingEvent>) {
    val healed = events.count { it.succeeded }
    val totalHits = memory.sumOf { it.successCount }
    val totalMiss = memory.sumOf { it.failureCount }

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Metric("Imagens", memory.size.toString(), Modifier.weight(1f))
            Metric("Acertos", totalHits.toString(), Modifier.weight(1f))
            Metric("Falhas", totalMiss.toString(), Modifier.weight(1f))
            Metric("Curas", "$healed/${events.size}", Modifier.weight(1f))
        }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MemoryCard(m: PatternMemory) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    m.patternKey,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Chip(m.preferredStrategy, strategyColor(m.preferredStrategy))
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { m.reliability.toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = when {
                    m.reliability > 0.8 -> MaterialTheme.colorScheme.primary
                    m.reliability > 0.5 -> Color(0xFFFFB300)
                    else -> MaterialTheme.colorScheme.error
                },
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "confiança ${(m.reliability * 100).roundToInt()}% · " +
                    "${m.successCount} ok / ${m.failureCount} falhas · " +
                    "score médio ${"%.2f".format(m.avgScore)} · " +
                    "escala ${"%.2f".format(m.avgScale)}x",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (m.thresholdDelta != 0.0) {
                Text(
                    "limiar ajustado em ${"%+.2f".format(m.thresholdDelta)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            if (m.lastX >= 0) {
                Text(
                    "última posição: ${m.lastX},${m.lastY} (${m.lastW}×${m.lastH})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (m.learnedVariantPath != null) {
                Text(
                    "variação aprendida salva ✓",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun HealingCard(e: HealingEvent) {
    val fmt = remember { SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Chip(
                    if (e.succeeded) "CUROU" else "FALHOU",
                    if (e.succeeded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.width(8.dp))
                Text(e.patternKey, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "${e.tactic} · ${fmt.format(Date(e.createdAt))} · script ${e.scriptName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (e.details.isNotBlank()) {
                Text(e.details, style = MaterialTheme.typography.bodySmall)
            }
            if (e.succeeded) {
                Text(
                    "score ${"%.2f".format(e.scoreBefore)} → ${"%.2f".format(e.scoreAfter)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private fun strategyColor(strategy: String): Color = when (strategy) {
    "TEMPLATE" -> Color(0xFF00BFA5)
    "ORB" -> Color(0xFF7E57C2)
    "OCR" -> Color(0xFFFFB300)
    "A11Y_TREE" -> Color(0xFF42A5F5)
    else -> Color(0xFF90A4AE)
}
