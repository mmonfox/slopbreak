package lt.neko.slopbreak.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import lt.neko.slopbreak.Prefs
import lt.neko.slopbreak.dev.Recordings
import lt.neko.slopbreak.limit.Usage
import lt.neko.slopbreak.protect.Admin
import lt.neko.slopbreak.service.WatchService
import lt.neko.slopbreak.ui.theme.AccentButton
import lt.neko.slopbreak.ui.theme.Haptics
import lt.neko.slopbreak.ui.theme.LocalHaptics
import lt.neko.slopbreak.ui.theme.SectionLabel
import lt.neko.slopbreak.ui.theme.SlopbreakTheme
import lt.neko.slopbreak.ui.theme.frostBehind

class MainActivity : ComponentActivity() {
    private var adminActive by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = Prefs.of(this)
        Usage.load(this)
        setContent {
            val askAdmin = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                adminActive = Admin.active(this)
                if (adminActive) prefs.protect(this)
            }
            LaunchedEffect(Unit) {
                while (true) {
                    prefs.settle(this@MainActivity)
                    delay(SETTLE_MS)
                }
            }
            SlopbreakTheme(isSystemInDarkTheme()) {
                CompositionLocalProvider(
                    LocalContentColor provides MaterialTheme.colorScheme.onBackground,
                    LocalHaptics provides Haptics(LocalHapticFeedback.current),
                ) {
                    val developer by prefs.developer.collectAsStateWithLifecycle()
                    Screen(
                        watching = WatchService.running.collectAsStateWithLifecycle().value,
                        onTitleTapped = { prefs.setDeveloper(!developer) },
                        onTurnOn = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                    ) {
                        LimitCard(prefs.limit.collectAsStateWithLifecycle().value, prefs::setLimit)
                        ProtectCard(
                            protection = prefs.protection.collectAsStateWithLifecycle().value,
                            adminActive = adminActive,
                            onProtect = {
                                if (Admin.active(this@MainActivity)) {
                                    prefs.protect(this@MainActivity)
                                } else {
                                    prefs.askingAdmin()
                                    askAdmin.launch(Admin.ask(this@MainActivity))
                                }
                            },
                            onTurnOff = { prefs.turnOff(this@MainActivity) },
                            onCancel = prefs::cancelTurnOff,
                        )
                        if (developer) {
                            Text("DEVELOPER", style = SectionLabel, modifier = Modifier.padding(start = 4.dp, top = 14.dp))
                            DeveloperCard(
                                recording = prefs.recording.collectAsStateWithLifecycle().value,
                                recordings = Recordings.all.collectAsStateWithLifecycle().value,
                                onRecording = prefs::setRecording,
                                onClear = { Recordings.clear(this@MainActivity) },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Recordings.load(this)
        adminActive = Admin.active(this)
    }
}

@Composable
private fun Screen(
    watching: Boolean,
    onTitleTapped: () -> Unit,
    onTurnOn: () -> Unit,
    cards: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHaptics.current
    var taps by remember { mutableIntStateOf(0) }
    var firstTap by remember { mutableLongStateOf(0L) }
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .frostBehind()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(
            Modifier
                .clickable(interactionSource = null, indication = null) {
                    val now = System.currentTimeMillis()
                    if (now - firstTap > TAPS_WITHIN_MS) {
                        firstTap = now
                        taps = 0
                    }
                    if (++taps == DEVELOPER_TAPS) {
                        taps = 0
                        haptics.tick()
                        onTitleTapped()
                    }
                }
                .padding(horizontal = 4.dp, vertical = 8.dp),
        ) {
            Text("Slopbreak", style = MaterialTheme.typography.headlineSmall)
            Text(
                if (watching) "On" else "Off",
                style = MaterialTheme.typography.bodyMedium,
                color = if (watching) colors.tertiary else colors.onSurfaceVariant,
            )
        }
        if (!watching) {
            Card {
                Text("Let it see the screen", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Turn Slopbreak on under Accessibility, to see which app is open. Nothing leaves the phone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
                AccentButton("Open Accessibility", onTurnOn, Modifier.padding(top = 4.dp))
            }
        }
        cards()
    }
}

private const val SETTLE_MS = 15_000L
private const val DEVELOPER_TAPS = 7
private const val TAPS_WITHIN_MS = 3_000L
