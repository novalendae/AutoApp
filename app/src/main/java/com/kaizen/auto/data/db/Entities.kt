package com.kaizen.auto.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Memória de longo prazo de um padrão (imagem/texto procurado por um script).
 *
 * É aqui que mora o "aprendizado passivo": a cada busca bem-sucedida o app
 * atualiza a região onde o elemento costuma aparecer, o limiar que funciona e a
 * escala típica. Na próxima execução a busca já começa mais esperta.
 */
@Entity(tableName = "pattern_memory", indices = [Index(value = ["patternKey"], unique = true)])
data class PatternMemory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /** Nome do arquivo de imagem ou texto procurado. */
    val patternKey: String,

    /** Quantas vezes foi encontrado / não encontrado. */
    val successCount: Int = 0,
    val failureCount: Int = 0,

    /** Região média onde o elemento aparece (para busca dirigida). */
    val lastX: Int = -1,
    val lastY: Int = -1,
    val lastW: Int = 0,
    val lastH: Int = 0,

    /** Média móvel do score obtido nos acertos. */
    val avgScore: Double = 0.0,

    /** Menor score que ainda deu certo — base para relaxar o limiar. */
    val minSuccessScore: Double = 1.0,

    /** Escala típica em que o template casa. */
    val avgScale: Double = 1.0,

    /**
     * Ajuste de limiar aprendido. Negativo = o bot afrouxou porque o elemento
     * mudou visualmente mas continua sendo o certo.
     */
    val thresholdDelta: Double = 0.0,

    /** Caminho de uma variante da imagem capturada da tela real, se houver. */
    val learnedVariantPath: String? = null,

    /** Estratégia que mais funciona para este padrão. */
    val preferredStrategy: String = "TEMPLATE",

    val updatedAt: Long = System.currentTimeMillis(),
) {
    val reliability: Double
        get() {
            val total = successCount + failureCount
            return if (total == 0) 0.0 else successCount.toDouble() / total
        }
}

/**
 * Registro de um evento de cura: o que quebrou, o que o bot tentou e se deu certo.
 * Alimenta a tela "Aprendizado" para o usuário auditar o que o robô andou fazendo.
 */
@Entity(tableName = "healing_events")
data class HealingEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patternKey: String,
    val scriptName: String,
    /** THRESHOLD_RELAX, REGION_EXPAND, MULTI_SCALE, ORB, OCR, A11Y, GIVE_UP */
    val tactic: String,
    val succeeded: Boolean,
    val scoreBefore: Double,
    val scoreAfter: Double,
    val details: String,
    /** Screenshot do momento da falha, para o usuário inspecionar. */
    val screenshotPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * Observação passiva de uma tela: o app viu esta combinação de elementos.
 * Serve para o bot saber "quando a tela X aparece, o botão Y fica aqui".
 */
@Entity(tableName = "screen_observations", indices = [Index(value = ["signature"])])
data class ScreenObservation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Hash estável dos rótulos da tela — identifica a "tela" logicamente. */
    val signature: String,
    val packageName: String,
    /** JSON com a lista de elementos (rótulo + bounds). */
    val elementsJson: String,
    val seenCount: Int = 1,
    val lastSeenAt: Long = System.currentTimeMillis(),
)

/** Linha de log de execução, exibida na tela de Logs em tempo real. */
@Entity(tableName = "run_logs")
data class RunLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scriptName: String,
    val level: String,
    val message: String,
    val createdAt: Long = System.currentTimeMillis(),
)
