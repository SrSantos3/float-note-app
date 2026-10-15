package com.antigravity.floatingnotes

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import com.antigravity.floatingnotes.ui.screens.CalendarScreen
import com.antigravity.floatingnotes.ui.screens.NotesListScreen
import com.antigravity.floatingnotes.ui.screens.SettingsScreen
import com.antigravity.floatingnotes.ui.theme.DarkBackground
import com.antigravity.floatingnotes.ui.theme.DarkSurface
import com.antigravity.floatingnotes.ui.theme.FloatingNotesTheme
import com.antigravity.floatingnotes.ui.theme.PrimaryIndigo
import com.antigravity.floatingnotes.ui.theme.TextMuted
import com.antigravity.floatingnotes.ui.theme.TextPrimary
import com.antigravity.floatingnotes.ui.viewmodel.NotesViewModel

enum class NavigationDestination(val title: String, val icon: ImageVector) {
    NOTES("Notas", Icons.Default.FormatListBulleted),
    CALENDAR("Calendario", Icons.Default.CalendarMonth),
    SETTINGS("Ajustes", Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {

    private val viewModel: NotesViewModel by viewModels()

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Permiso de notificaciones gestionado
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermission()

        setContent {
            FloatingNotesTheme {
                MainAppScaffold(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkBubbleServiceState()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

@Composable
fun MainAppScaffold(viewModel: NotesViewModel) {
    var currentDestination by remember { mutableStateOf(NavigationDestination.NOTES) }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = DarkSurface,
                contentColor = PrimaryIndigo
            ) {
                NavigationDestination.values().forEach { destination ->
                    NavigationBarItem(
                        selected = currentDestination == destination,
                        onClick = { currentDestination = destination },
                        icon = {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = destination.title
                            )
                        },
                        label = {
                            Text(
                                text = destination.title,
                                color = if (currentDestination == destination) TextPrimary else TextMuted
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = TextPrimary,
                            indicatorColor = PrimaryIndigo,
                            unselectedIconColor = TextMuted
                        )
                    )
                }
            }
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        when (currentDestination) {
            NavigationDestination.NOTES -> NotesListScreen(
                viewModel = viewModel,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
            NavigationDestination.CALENDAR -> CalendarScreen(
                viewModel = viewModel,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
            NavigationDestination.SETTINGS -> SettingsScreen(
                viewModel = viewModel,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        }
    }
}
