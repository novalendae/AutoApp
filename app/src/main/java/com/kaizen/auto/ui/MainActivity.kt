package com.kaizen.auto.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kaizen.auto.ui.screens.EditorScreen
import com.kaizen.auto.ui.screens.HelpScreen
import com.kaizen.auto.ui.screens.LearningScreen
import com.kaizen.auto.ui.screens.LogsScreen
import com.kaizen.auto.ui.screens.ScriptsScreen
import com.kaizen.auto.ui.screens.SettingsScreen
import com.kaizen.auto.ui.theme.KaizenTheme

class MainActivity : ComponentActivity() {

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* Sem notificação o serviço ainda roda; só perde o botão PARAR rápido. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        setContent {
            KaizenTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    KaizenNavigation()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Permissões podem ter mudado enquanto estávamos fora (o usuário foi
        // até as configurações do sistema e voltou).
        readinessRefresh?.invoke()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    companion object {
        /** Gancho simples para o onResume revalidar as permissões. */
        @Volatile
        var readinessRefresh: (() -> Unit)? = null
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("scripts", "Scripts", Icons.Outlined.PlayArrow),
    Tab("logs", "Logs", Icons.AutoMirrored.Filled.List),
    Tab("learning", "Bot", Icons.Default.Star),
    Tab("help", "Guia", Icons.AutoMirrored.Filled.HelpOutline),
    Tab("settings", "Ajustes", Icons.Default.Settings),
)

@Composable
fun KaizenNavigation(vm: AppViewModel = viewModel()) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val snackbar = remember { SnackbarHostState() }
    val message by vm.message.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        MainActivity.readinessRefresh = { vm.refreshReadiness() }
    }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            // O editor ocupa a tela inteira: sem barra embaixo atrapalhando.
            if (currentRoute != "editor") {
                NavigationBar {
                    TABS.forEach { tab ->
                        val selected = backStack?.destination?.hierarchy?.any {
                            it.route == tab.route
                        } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "scripts",
            modifier = Modifier.padding(padding),
        ) {
            composable("scripts") {
                ScriptsScreen(vm) { entry ->
                    vm.select(entry)
                    navController.navigate("editor")
                }
            }
            composable("editor") {
                EditorScreen(vm) { navController.popBackStack() }
            }
            composable("logs") { LogsScreen(vm) }
            composable("learning") { LearningScreen(vm) }
            composable("help") { HelpScreen() }
            composable("settings") { SettingsScreen(vm) }
        }
    }
}
