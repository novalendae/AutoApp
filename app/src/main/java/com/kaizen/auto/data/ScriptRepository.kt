package com.kaizen.auto.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Um script = uma PASTA. Dentro dela ficam o .lua e a subpasta images/.
 * Assim o usuário pode exportar/compartilhar o projeto inteiro num zip, e as
 * imagens referenciadas por nome simples ("botao.png") sempre resolvem.
 *
 *   /Android/data/com.kaizen.auto/files/scripts/
 *       meu_bot/
 *           main.lua
 *           images/
 *               botao_jogar.png
 *               tela_inicial.png
 */
data class ScriptEntry(
    val name: String,
    val file: File,
    val folder: File,
    val lastModified: Long,
    val sizeBytes: Long,
    val imageCount: Int,
) {
    val imagesDir: File get() = File(folder, "images")
}

class ScriptRepository(private val context: Context) {

    /** Pasta raiz, visível ao usuário via MTP/gerenciador de arquivos. */
    val rootDir: File by lazy {
        val external = context.getExternalFilesDir(null)
        File(external ?: context.filesDir, "scripts").apply { mkdirs() }
    }

    private val _scripts = MutableStateFlow<List<ScriptEntry>>(emptyList())
    val scripts: StateFlow<List<ScriptEntry>> = _scripts

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val found = rootDir.listFiles { f -> f.isDirectory }
            ?.mapNotNull { folder -> folder.toEntry() }
            ?.sortedByDescending { it.lastModified }
            ?: emptyList()
        _scripts.value = found
    }

    private fun File.toEntry(): ScriptEntry? {
        val main = File(this, MAIN_FILE).takeIf { it.exists() }
            ?: listFiles { f -> f.extension == "lua" }?.firstOrNull()
            ?: return null
        val images = File(this, "images")
        return ScriptEntry(
            name = name,
            file = main,
            folder = this,
            lastModified = main.lastModified(),
            sizeBytes = main.length(),
            imageCount = images.listFiles { f -> f.isImage() }?.size ?: 0,
        )
    }

    suspend fun create(name: String, template: String = DEFAULT_TEMPLATE): ScriptEntry? =
        withContext(Dispatchers.IO) {
            val safe = name.sanitized()
            if (safe.isEmpty()) return@withContext null
            val folder = File(rootDir, safe)
            if (folder.exists()) return@withContext null

            folder.mkdirs()
            File(folder, "images").mkdirs()
            val main = File(folder, MAIN_FILE)
            main.writeText(template.replace("{{NAME}}", safe))
            refresh()
            folder.toEntry()
        }

    suspend fun save(entry: ScriptEntry, content: String): Boolean = withContext(Dispatchers.IO) {
        try {
            entry.file.writeText(content)
            refresh()
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Falha ao salvar: ${t.message}")
            false
        }
    }

    suspend fun read(entry: ScriptEntry): String = withContext(Dispatchers.IO) {
        runCatching { entry.file.readText() }.getOrDefault("")
    }

    suspend fun delete(entry: ScriptEntry): Boolean = withContext(Dispatchers.IO) {
        val ok = entry.folder.deleteRecursively()
        refresh()
        ok
    }

    suspend fun rename(entry: ScriptEntry, newName: String): Boolean = withContext(Dispatchers.IO) {
        val target = File(rootDir, newName.sanitized())
        if (target.exists()) return@withContext false
        val ok = entry.folder.renameTo(target)
        refresh()
        ok
    }

    suspend fun duplicate(entry: ScriptEntry): Boolean = withContext(Dispatchers.IO) {
        val target = File(rootDir, "${entry.name}_copia")
        if (target.exists()) return@withContext false
        val ok = runCatching { entry.folder.copyRecursively(target, overwrite = false) }.getOrDefault(false)
        refresh()
        ok
    }

    // ------------------------------------------------------------------
    // Imagens
    // ------------------------------------------------------------------

    fun listImages(entry: ScriptEntry): List<File> =
        entry.imagesDir.listFiles { f -> f.isImage() }?.sortedBy { it.name } ?: emptyList()

    /** Importa uma imagem da galeria para a pasta do script. */
    suspend fun importImage(entry: ScriptEntry, uri: Uri, fileName: String? = null): File? =
        withContext(Dispatchers.IO) {
            try {
                entry.imagesDir.mkdirs()
                val name = (fileName ?: "img_${System.currentTimeMillis()}.png").sanitizedFileName()
                val target = File(entry.imagesDir, name)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(target).use { output -> input.copyTo(output) }
                }
                target
            } catch (t: Throwable) {
                Log.e(TAG, "Falha ao importar imagem: ${t.message}")
                null
            }
        }

    /** Salva um recorte capturado pela ferramenta de seleção de área. */
    suspend fun saveCrop(entry: ScriptEntry, bitmap: Bitmap, name: String): File? =
        withContext(Dispatchers.IO) {
            try {
                entry.imagesDir.mkdirs()
                val fileName = if (name.endsWith(".png")) name else "$name.png"
                val target = File(entry.imagesDir, fileName.sanitizedFileName())
                FileOutputStream(target).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                target
            } catch (t: Throwable) {
                Log.e(TAG, "Falha ao salvar recorte: ${t.message}")
                null
            }
        }

    fun deleteImage(file: File): Boolean = file.delete()

    fun loadThumbnail(file: File, maxSize: Int = 256): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (bounds.outWidth / sample > maxSize || bounds.outHeight / sample > maxSize) sample *= 2
        BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample },
        )
    } catch (t: Throwable) {
        null
    }

    /** Cria os exemplos na primeira execução, para o usuário ter de onde partir. */
    suspend fun installSamplesIfNeeded() = withContext(Dispatchers.IO) {
        if (rootDir.listFiles()?.isNotEmpty() == true) return@withContext
        runCatching {
            val folder = File(rootDir, "exemplo_basico").apply { mkdirs() }
            File(folder, "images").mkdirs()
            File(folder, MAIN_FILE).writeText(SAMPLE_SCRIPT)
        }
        refresh()
    }

    // ------------------------------------------------------------------

    private fun File.isImage(): Boolean =
        extension.lowercase() in setOf("png", "jpg", "jpeg", "webp")

    private fun String.sanitized(): String =
        trim().replace(Regex("[^A-Za-z0-9 _-]"), "").replace(' ', '_').take(48)

    private fun String.sanitizedFileName(): String =
        trim().replace(Regex("[^A-Za-z0-9._-]"), "_").take(64)

    companion object {
        private const val TAG = "ScriptRepository"
        const val MAIN_FILE = "main.lua"

        val DEFAULT_TEMPLATE = """
            -- {{NAME}}
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

            log("Fim.")
        """.trimIndent()

        val SAMPLE_SCRIPT = """
            -- ============================================
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
            -- if existsClick("botao_jogar.png", 5) then
            --     log("Cliquei em jogar")
            -- end

            -- 3) Buscar dentro de uma região específica (mais rápido)
            -- local topo = Region(0, 0, getScreenWidth(), 300)
            -- if topo:exists("icone.png") then
            --     click(getLastMatch())
            -- end

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
        """.trimIndent()
    }
}
