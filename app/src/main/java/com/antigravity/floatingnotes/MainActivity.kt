package com.antigravity.floatingnotes

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentActivity
import androidx.core.content.ContextCompat
import com.antigravity.floatingnotes.service.ReminderScheduler
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

class MainActivity : AppCompatActivity() {

    private val viewModel: NotesViewModel by viewModels()

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Permisos gestionados
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)

        try {
            requestAppPermissions()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            ReminderScheduler.scheduleDailyBriefing(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        setContent {
            FloatingNotesTheme {
                MainAppScaffold(
                    viewModel = viewModel,
                    activity = this
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val appPkg = intent?.getStringExtra("FILTER_APP_PACKAGE")
        val appLabel = intent?.getStringExtra("FILTER_APP_LABEL")
        if (!appPkg.isNullOrBlank()) {
            viewModel.setAppFilter(appPkg, appLabel)
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            viewModel.checkBubbleServiceState()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun requestAppPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
}

@Composable
fun MainAppScaffold(
    viewModel: NotesViewModel,
    activity: FragmentActivity
) {
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
                activity = activity,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
            NavigationDestination.CALENDAR -> CalendarScreen(
                viewModel = viewModel,
                activity = activity,
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
