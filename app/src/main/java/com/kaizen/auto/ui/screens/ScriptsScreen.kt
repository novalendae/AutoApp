package com.kaizen.auto.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kaizen.auto.data.ScriptEntry
import com.kaizen.auto.runtime.RunState
import com.kaizen.auto.ui.AppViewModel
import com.kaizen.auto.ui.components.ConfirmDialog
import com.kaizen.auto.ui.components.EmptyState
import com.kaizen.auto.ui.components.RequirementRow
import com.kaizen.auto.ui.components.TextInputDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ScriptsScreen(
    vm: AppViewModel,
    onOpenEditor: (ScriptEntry) -> Unit,
) {
    val scripts by vm.scripts.collectAsState()
    val readiness by vm.readiness.collectAsState()
    val runState by vm.runState.collectAsState()
    val running by vm.currentScript.collectAsState()

    var showCreate by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<ScriptEntry?>(null) }
    var toRename by remember { mutableStateOf<ScriptEntry?>(null) }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreate = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Novo script") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("KaizenAuto", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Default.Refresh, "Atualizar")
                    }
                }
            }

            // Pré-requisitos só aparecem enquanto faltam — depois somem do caminho.
            if (!readiness.accessibilityOn) {
                item {
                    RequirementRow(
                        title = "Acessibilidade",
                        description = "Necessária para o app tocar na tela por você.",
                        satisfied = false,
                        actionLabel = "Ativar",
                        onAction = { vm.openAccessibilitySettings() },
                    )
                }
            }
            if (!readiness.captureOn) {
                item {
                    RequirementRow(
                        title = "Captura de tela",
                        description = "Necessária para o app enxergar as imagens.",
                        satisfied = false,
                        actionLabel = "Permitir",
                        onAction = { vm.prepareCapture() },
                    )
                }
            }

            if (runState != RunState.IDLE) {
                item { RunningBanner(runState, running, vm) }
            }

            if (scripts.isEmpty()) {
                item {
                    EmptyState(
                        "Nenhum script ainda",
                        "Toque em NOVO SCRIPT para começar. Cada script tem sua própria pasta de imagens.",
                    )
                }
            }

            items(scripts, key = { it.name }) { entry ->
                ScriptCard(
                    entry = entry,
                    isRunning = running == entry.name && runState != RunState.IDLE,
                    onOpen = { onOpenEditor(entry) },
                    onRun = { vm.run(entry) },
                    onStop = { vm.stop() },
                    onDelete = { toDelete = entry },
                    onDuplicate = { vm.duplicate(entry) },
                    onRename = { toRename = entry },
                )
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (showCreate) {
        TextInputDialog(
            title = "Novo script",
            label = "Nome",
            confirmLabel = "Criar",
            onDismiss = { showCreate = false },
            onConfirm = {
                vm.createScript(it)
                showCreate = false
            },
        )
    }

    toRename?.let { entry ->
        TextInputDialog(
            title = "Renomear",
            label = "Novo nome",
            initial = entry.name,
            confirmLabel = "Salvar",
            onDismiss = { toRename = null },
            onConfirm = {
                vm.rename(entry, it)
                toRename = null
            },
        )
    }

    toDelete?.let { entry ->
        ConfirmDialog(
            title = "Apagar '${entry.name}'?",
            message = "A pasta inteira, incluindo as imagens, será removida. Não dá pra desfazer.",
            onDismiss = { toDelete = null },
            onConfirm = {
                vm.delete(entry)
                toDelete = null
            },
        )
    }
}

@Composable
private fun RunningBanner(state: RunState, name: String?, vm: AppViewModel) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    when (state) {
                        RunState.RUNNING -> "Executando"
                        RunState.PAUSED -> "Pausado"
                        RunState.STOPPING -> "Parando…"
                        RunState.ERROR -> "Erro"
                        RunState.IDLE -> "Parado"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(name ?: "-", style = MaterialTheme.typography.bodySmall)
            }
            if (state == RunState.PAUSED) {
                IconButton(onClick = { vm.resumeRun() }) { Icon(Icons.Default.PlayArrow, "Continuar") }
            } else if (state == RunState.RUNNING) {
                IconButton(onClick = { vm.pause() }) { Text("⏸") }
            }
            IconButton(onClick = { vm.stop() }) {
                Text("⏹", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ScriptCard(
    entry: ScriptEntry,
    isRunning: Boolean,
    onOpen: () -> Unit,
    onRun: () -> Unit,
    onStop: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    onRename: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val fmt = remember { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Row(
            Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(entry.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${entry.imageCount} imagem(ns) · ${entry.sizeBytes} B · ${fmt.format(Date(entry.lastModified))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isRunning) {
                IconButton(onClick = onStop) {
                    Text("⏹", color = MaterialTheme.colorScheme.error)
                }
            } else {
                IconButton(onClick = onRun) {
                    Icon(
                        Icons.Default.PlayArrow,
                        "Rodar",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "Mais") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Editar") },
                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                        onClick = { menuOpen = false; onOpen() },
                    )
                    DropdownMenuItem(
                        text = { Text("Renomear") },
                        onClick = { menuOpen = false; onRename() },
                    )
                    DropdownMenuItem(
                        text = { Text("Duplicar") },
                        onClick = { menuOpen = false; onDuplicate() },
                    )
                    DropdownMenuItem(
                        text = { Text("Apagar") },
                        leadingIcon = { Icon(Icons.Default.Delete, null) },
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
        }
    }
}
