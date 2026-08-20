package com.kaizen.auto.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kaizen.auto.ui.AppViewModel
import com.kaizen.auto.ui.components.ConfirmDialog
import com.kaizen.auto.ui.components.SectionHeader
import com.kaizen.auto.ui.theme.MonoStyle
import java.io.File

/**
 * Editor de script + gerenciador de imagens do script.
 *
 * Decisões de UX pensadas para celular:
 *  - Barra de trechos ("snippets") acima do teclado: escrever `click("x.png")`
 *    em teclado virtual é doloroso, então os comandos mais usados entram com
 *    um toque, já com o cursor posicionado.
 *  - Tocar numa imagem da galeria insere o nome dela no cursor. É o fluxo
 *    real: você recorta a imagem, importa, e usa no código.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: AppViewModel, onBack: () -> Unit) {
    val entry by vm.selected.collectAsState()
    val text by vm.editorText.collectAsState()
    val images by vm.images.collectAsState()

    var field by remember { mutableStateOf(TextFieldValue(text)) }
    var dirty by remember { mutableStateOf(false) }
    var imageToDelete by remember { mutableStateOf<File?>(null) }
    var showDiscard by remember { mutableStateOf(false) }

    // Sincroniza quando o texto vem do repositório (troca de script).
    LaunchedEffect(text) {
        if (field.text != text && !dirty) {
            field = TextFieldValue(text, TextRange(text.length))
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        uri?.let { vm.importImage(it, null) }
    }

    fun insert(snippet: String, caretBack: Int = 0) {
        val start = field.selection.start
        val end = field.selection.end
        val newText = field.text.replaceRange(start, end, snippet)
        val caret = (start + snippet.length - caretBack).coerceIn(0, newText.length)
        field = TextFieldValue(newText, TextRange(caret))
        vm.onEditorChange(newText)
        dirty = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(entry?.name ?: "Editor", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (dirty) "não salvo" else "salvo",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (dirty) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { if (dirty) showDiscard = true else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            vm.onEditorChange(field.text)
                            vm.saveCurrent { dirty = false }
                        },
                    ) { Icon(Icons.Default.Check, "Salvar") }
                    IconButton(
                        onClick = {
                            vm.onEditorChange(field.text)
                            vm.saveCurrent {
                                dirty = false
                                entry?.let { vm.run(it) }
                            }
                        },
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            "Salvar e rodar",
                            tint = MaterialTheme.colorScheme.primary,
                        )
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
            // ---------- Galeria de imagens do script ----------
            Column(Modifier.padding(horizontal = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader("Imagens (${images.size})", Modifier.weight(1f))
                    IconButton(onClick = { picker.launch("image/*") }) {
                        Icon(Icons.Default.Add, "Importar imagem")
                    }
                }
                if (images.isEmpty()) {
                    Text(
                        "Nenhuma imagem. Recorte prints da tela do app-alvo e importe aqui.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(images, key = { it.absolutePath }) { file ->
                            ImageThumb(
                                file = file,
                                onClick = { insert("\"${file.name}\"") },
                                onLongClick = { imageToDelete = file },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // ---------- Editor ----------
            OutlinedTextField(
                value = field,
                onValueChange = {
                    field = it
                    vm.onEditorChange(it.text)
                    dirty = true
                },
                textStyle = MonoStyle,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                ),
            )

            // ---------- Barra de trechos ----------
            SnippetBar(onInsert = ::insert)
        }
    }

    imageToDelete?.let { file ->
        ConfirmDialog(
            title = "Apagar ${file.name}?",
            message = "Se algum script usar essa imagem, ele vai falhar ao procurá-la.",
            onDismiss = { imageToDelete = null },
            onConfirm = {
                vm.deleteImage(file)
                imageToDelete = null
            },
        )
    }

    if (showDiscard) {
        ConfirmDialog(
            title = "Sair sem salvar?",
            message = "As alterações no script serão perdidas.",
            confirmLabel = "Descartar",
            onDismiss = { showDiscard = false },
            onConfirm = {
                showDiscard = false
                onBack()
            },
        )
    }
}

@Composable
private fun ImageThumb(file: File, onClick: () -> Unit, onLongClick: () -> Unit) {
    Card(Modifier.width(96.dp)) {
        Column(
            Modifier
                .clickable(onClick = onClick)
                .padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AsyncImage(
                model = file,
                contentDescription = file.name,
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                file.name,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
            Text(
                "remover",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.clickable(onClick = onLongClick),
            )
        }
    }
}

/** Comandos mais usados, um toque cada. O `caretBack` deixa o cursor no lugar certo. */
@Composable
private fun SnippetBar(onInsert: (String, Int) -> Unit) {
    val snippets = remember {
        listOf(
            Triple("click", "click(\"\")\n", 3),
            Triple("exists", "if exists(\"\", 0) then\n    \nend\n", 22),
            Triple("wait", "wait(\"\", 10)\n", 6),
            Triple("waitClick", "waitClick(\"\", 10)\n", 6),
            Triple("existsClick", "existsClick(\"\", 2)\n", 6),
            Triple("waitVanish", "waitVanish(\"\", 30)\n", 6),
            Triple("clickText", "clickText(\"\")\n", 3),
            Triple("swipe", "swipe(Location(500, 1500), Location(500, 500), 400)\n", 1),
            Triple("sleep", "sleep(1)\n", 1),
            Triple("loop", "while not shouldStop() do\n    \nend\n", 5),
            Triple("Region", "Region(0, 0, 720, 1280)", 0),
            Triple("log", "log(\"\")\n", 3),
            Triple("toast", "toast(\"\")\n", 3),
            Triple("exit", "scriptExit(\"fim\")\n", 1),
            Triple("Settings", "Settings:setScriptDimension(true, 1280)\n", 0),
        )
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        snippets.forEach { (label, code, back) ->
            AssistChip(
                onClick = { onInsert(code, back) },
                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
            )
        }
    }
}
