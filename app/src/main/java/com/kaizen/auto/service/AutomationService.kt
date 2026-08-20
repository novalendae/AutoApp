package com.kaizen.auto.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.kaizen.auto.KaizenApp
import com.kaizen.auto.R
import com.kaizen.auto.data.ScriptEntry
import com.kaizen.auto.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * Serviço em foreground que hospeda a execução.
 *
 * Por que foreground: o Android mata processos em background agressivamente, e
 * o MediaProjection EXIGE um serviço em foreground do tipo mediaProjection a
 * partir do Android 10. A notificação também é a forma mais rápida do usuário
 * matar o script — tem um botão PARAR direto nela.
 *
 * O serviço se auto-encerra quando o script termina. Nada fica rodando à toa.
 */
class AutomationService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wakeLock: PowerManager.WakeLock? = null
    private var observerJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_STOP -> handleStop()
            ACTION_PAUSE -> {
                app().scriptRunner.pause()
                updateNotification("Pausado", paused = true)
            }
            ACTION_RESUME -> {
                app().scriptRunner.resume()
                updateNotification("Executando", paused = false)
            }
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun handleStart(intent: Intent) {
        val scriptPath = intent.getStringExtra(EXTRA_SCRIPT_PATH)
        val scriptName = intent.getStringExtra(EXTRA_SCRIPT_NAME) ?: "script"

        startForegroundCompat(scriptName)

        // Anexa a projeção de tela, se o usuário concedeu.
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val data = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }

        if (resultCode != 0 && data != null) {
            attachProjection(resultCode, data)
        }

        if (scriptPath == null) {
            // Só ligamos a captura (modo "preparar"), sem rodar script.
            updateNotification("Captura de tela pronta", paused = false)
            return
        }

        val file = File(scriptPath)
        if (!file.exists()) {
            toast("Script não encontrado: $scriptPath")
            stopSelf()
            return
        }

        acquireWakeLock()

        val entry = ScriptEntry(
            name = scriptName,
            file = file,
            folder = file.parentFile ?: file,
            lastModified = file.lastModified(),
            sizeBytes = file.length(),
            imageCount = 0,
        )

        val runner = app().scriptRunner
        runner.onFinished = { success, message ->
            scope.launch {
                toast(if (success) "✔ $message" else "✖ $message")
                releaseWakeLock()
                stopSelf()
            }
        }
        runner.start(entry)
        updateNotification("Executando: $scriptName", paused = false)
    }

    private fun handleStop() {
        app().scriptRunner.stop()
        releaseWakeLock()
        stopSelf()
    }

    private fun attachProjection(resultCode: Int, data: Intent) {
        try {
            val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection: MediaProjection = mpm.getMediaProjection(resultCode, data)
            app().captureManager.attachProjection(projection)
            Log.i(TAG, "MediaProjection anexada.")
        } catch (t: Throwable) {
            Log.e(TAG, "Falha ao anexar projeção: ${t.message}")
            toast("Não consegui iniciar a captura de tela.")
        }
    }

    // ------------------------------------------------------------------

    private fun startForegroundCompat(scriptName: String) {
        val notification = buildNotification("Preparando: $scriptName", paused = false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(text: String, paused: Boolean): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, AutomationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val toggleIntent = PendingIntent.getService(
            this, 2,
            Intent(this, AutomationService::class.java)
                .setAction(if (paused) ACTION_RESUME else ACTION_PAUSE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("KaizenAuto")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                if (paused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                if (paused) "Continuar" else "Pausar",
                toggleIntent,
            )
            .addAction(android.R.drawable.ic_delete, "PARAR", stopIntent)
            .build()
    }

    private fun updateNotification(text: String, paused: Boolean) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(text, paused))
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_runtime),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Mostra o script em execução e permite pará-lo."
                setShowBadge(false)
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    /**
     * WakeLock parcial: mantém a CPU viva com a tela apagada.
     * Timeout obrigatório — nunca deixamos o lock aberto indefinidamente.
     */
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "KaizenAuto::run").apply {
            setReferenceCounted(false)
            acquire(MAX_WAKELOCK_MS)
        }
    }

    private fun releaseWakeLock() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
    }

    private fun toast(msg: String) {
        Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
    }

    private fun app() = applicationContext as KaizenApp

    override fun onDestroy() {
        observerJob?.cancel()
        releaseWakeLock()
        app().scriptRunner.stop(500)
        // Liberamos a projeção: sem isso o ícone de gravação fica preso na barra.
        app().captureManager.releaseProjection()
        instance = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AutomationService"
        private const val CHANNEL_ID = "kaizen_runtime"
        private const val NOTIFICATION_ID = 1001
        private const val MAX_WAKELOCK_MS = 4 * 60 * 60 * 1000L // 4h

        const val ACTION_START = "com.kaizen.auto.START"
        const val ACTION_STOP = "com.kaizen.auto.STOP"
        const val ACTION_PAUSE = "com.kaizen.auto.PAUSE"
        const val ACTION_RESUME = "com.kaizen.auto.RESUME"

        const val EXTRA_SCRIPT_PATH = "script_path"
        const val EXTRA_SCRIPT_NAME = "script_name"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"

        @Volatile
        var instance: AutomationService? = null
            private set

        fun start(context: Context, scriptPath: String?, scriptName: String, resultCode: Int = 0, data: Intent? = null) {
            val intent = Intent(context, AutomationService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SCRIPT_PATH, scriptPath)
                putExtra(EXTRA_SCRIPT_NAME, scriptName)
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, AutomationService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
