package com.kaizen.auto.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kaizen.auto.data.db.RunLog
import com.kaizen.auto.ui.AppViewModel
import com.kaizen.auto.ui.components.Chip
import com.kaizen.auto.ui.components.EmptyState
import com.kaizen.auto.ui.theme.MonoStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(vm: AppViewModel) {
    val logs by vm.logs.collectAsState()
    var filter by remember { mutableStateOf("TUDO") }
    val listState = rememberLazyListState()

    val filtered = remember(logs, filter) {
        when (filter) {
            "TUDO" -> logs
            "ERROS" -> logs.filter { it.level == "ERROR" || it.level == "WARN" }
            "CURA" -> logs.filter { it.level == "HEAL" }
            else -> logs
        }
    }

    // Lista vem em ordem decrescente (id DESC), então o topo já é o mais novo.
    LaunchedEffect(filtered.size) {
        if (filtered.isNotEmpty()) listState.scrollToItem(0)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Logs") },
                actions = {
                    IconButton(onClick = { vm.clearLogs() }) {
                        Icon(Icons.Default.Delete, "Limpar")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("TUDO", "ERROS", "CURA").forEach { option ->
                    FilterChip(
                        selected = filter == option,
                        onClick = { filter = option },
                        label = { Text(option, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }

            if (filtered.isEmpty()) {
                EmptyState("Sem registros", "Rode um script para ver o que aconteceu, linha por linha.")
                return@Column
            }

            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(filtered, key = { it.id }) { log ->
                    LogRow(log)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                }
            }
        }
    }
}

@Composable
private fun LogRow(log: RunLog) {
    val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val color = when (log.level) {
        "ERROR" -> MaterialTheme.colorScheme.error
        "WARN" -> Color(0xFFFFB300)
        "HEAL" -> Color(0xFF7E57C2)
        "INFO" -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            fmt.format(Date(log.createdAt)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Chip(log.level, color)
        Spacer(Modifier.width(8.dp))
        Text(log.message, style = MonoStyle, modifier = Modifier.weight(1f))
    }
}
