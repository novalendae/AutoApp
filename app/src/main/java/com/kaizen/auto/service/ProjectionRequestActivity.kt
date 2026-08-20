package com.kaizen.auto.service

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Activity transparente cuja única função é pedir o consentimento do
 * MediaProjection e repassar o resultado ao [AutomationService].
 *
 * Existe porque o diálogo do sistema só pode ser disparado de uma Activity,
 * mas quem precisa do token é o serviço. O usuário vê apenas o diálogo padrão
 * do Android — nenhuma tela nossa pisca no meio.
 */
class ProjectionRequestActivity : ComponentActivity() {

    private var pendingScriptPath: String? = null
    private var pendingScriptName: String = "script"

    private val launcher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            AutomationService.start(
                context = this,
                scriptPath = pendingScriptPath,
                scriptName = pendingScriptName,
                resultCode = result.resultCode,
                data = result.data,
            )
        } else {
            Toast.makeText(
                this,
                "Sem permissão de captura o robô fica cego. Você pode tentar de novo.",
                Toast.LENGTH_LONG,
            ).show()
        }
        finish()
        overridePendingTransition(0, 0)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)

        pendingScriptPath = intent.getStringExtra(EXTRA_SCRIPT_PATH)
        pendingScriptName = intent.getStringExtra(EXTRA_SCRIPT_NAME) ?: "script"

        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        launcher.launch(mpm.createScreenCaptureIntent())
    }

    companion object {
        const val EXTRA_SCRIPT_PATH = "script_path"
        const val EXTRA_SCRIPT_NAME = "script_name"

        fun request(context: Context, scriptPath: String?, scriptName: String) {
            val intent = Intent(context, ProjectionRequestActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_SCRIPT_PATH, scriptPath)
                .putExtra(EXTRA_SCRIPT_NAME, scriptName)
            context.startActivity(intent)
        }
    }
}
