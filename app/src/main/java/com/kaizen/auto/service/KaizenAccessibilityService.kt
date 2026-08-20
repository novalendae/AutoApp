package com.kaizen.auto.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.kaizen.auto.healing.PassiveObserver

/**
 * Serviço de acessibilidade — as mãos e parte dos olhos do app.
 *
 * Além de despachar gestos, ele alimenta o [PassiveObserver] com a árvore de
 * nós da tela atual. Esse é o coração do "aprendizado passivo": mesmo quando
 * nenhum script está rodando, o app vai registrando quais telas existem, quais
 * botões aparecem nelas e onde ficam — e depois usa esse histórico para
 * consertar scripts quebrados.
 */
class KaizenAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "Serviço de acessibilidade conectado.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        // Só nos importam mudanças de tela/conteúdo — o resto é ruído.
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            -> {
                val pkg = event.packageName?.toString() ?: return
                PassiveObserver.onScreenChanged(pkg) { collectClickables() }
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Serviço interrompido.")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        Log.i(TAG, "Serviço desconectado.")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    /**
     * Varre a árvore de acessibilidade e devolve os elementos interagíveis.
     * Limitamos a profundidade para não travar em telas com listas gigantes.
     */
    private fun collectClickables(): List<UiElement> {
        val root = rootInActiveWindow ?: return emptyList()
        val out = ArrayList<UiElement>(64)
        walk(root, 0, out)
        return out
    }

    private fun walk(node: AccessibilityNodeInfo?, depth: Int, out: MutableList<UiElement>) {
        node ?: return
        if (depth > MAX_DEPTH || out.size >= MAX_NODES) return

        val text = node.text?.toString()
        val desc = node.contentDescription?.toString()
        val id = node.viewIdResourceName

        val interesting = node.isClickable || node.isCheckable || node.isEditable ||
            !TextUtils.isEmpty(text) || !TextUtils.isEmpty(desc)

        if (interesting) {
            val bounds = android.graphics.Rect()
            node.getBoundsInScreen(bounds)
            if (bounds.width() > 0 && bounds.height() > 0) {
                out += UiElement(
                    text = text.orEmpty(),
                    contentDescription = desc.orEmpty(),
                    viewId = id.orEmpty(),
                    className = node.className?.toString().orEmpty(),
                    clickable = node.isClickable,
                    left = bounds.left,
                    top = bounds.top,
                    right = bounds.right,
                    bottom = bounds.bottom,
                )
            }
        }

        for (i in 0 until node.childCount) {
            walk(node.getChild(i), depth + 1, out)
        }
    }

    /** Snapshot serializável de um elemento de UI. */
    data class UiElement(
        val text: String,
        val contentDescription: String,
        val viewId: String,
        val className: String,
        val clickable: Boolean,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    ) {
        val centerX: Int get() = (left + right) / 2
        val centerY: Int get() = (top + bottom) / 2
        /** Rótulo mais útil disponível, para casar com o que o script procura. */
        val label: String
            get() = when {
                text.isNotBlank() -> text
                contentDescription.isNotBlank() -> contentDescription
                else -> viewId.substringAfterLast('/')
            }
    }

    companion object {
        private const val TAG = "KaizenA11y"
        private const val MAX_DEPTH = 40
        private const val MAX_NODES = 300

        /** Instância viva do serviço, ou null se o usuário não habilitou. */
        @Volatile
        var instance: KaizenAccessibilityService? = null
            internal set

        fun isEnabled(context: Context): Boolean {
            if (instance != null) return true
            // Fallback: consulta as configurações do sistema (o serviço pode estar
            // habilitado mas ainda não conectado após um reboot).
            val expected = "${context.packageName}/${KaizenAccessibilityService::class.java.name}"
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ).orEmpty()
            return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
        }

        fun openSettings(context: Context) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}
