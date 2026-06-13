package com.tars.assistant.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.tars.assistant.model.AiProvider
import com.tars.assistant.ui.theme.TarsColors
import com.tars.assistant.ui.theme.TarsTheme
import com.tars.assistant.viewmodel.TarsViewModel
import java.io.File

class MainActivity : ComponentActivity() {

    private val viewModel: TarsViewModel by viewModels()
    private lateinit var volumeShortcut: com.tars.assistant.utils.VolumeButtonShortcut

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        com.tars.assistant.service.TarsProactiveReceiver.schedule(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // FIX: crash logger — orice excepție necapturată ajunge în crash.txt
        // Locație pe telefon: Android/data/com.tars.assistant/files/crash.txt
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            try {
                val crashFile = File(getExternalFilesDir(null), "crash.txt")
                crashFile.writeText(
                    "TARS CRASH ${java.util.Date()}\n\n" +
                    android.util.Log.getStackTraceString(throwable)
                )
            } catch (_: Exception) { }
            android.os.Process.killProcess(android.os.Process.myPid())
        }

        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Create notification channels
        com.tars.assistant.service.TarsNotificationManager.createChannels(this)

        // Double-press volume down → activate TARS mic
        volumeShortcut = com.tars.assistant.utils.VolumeButtonShortcut(
            context = this,
            onDoublePressVolDown = {
                viewModel.startListening()
                com.tars.assistant.service.TarsNotificationManager.showOngoingListening(this)
            }
        )

        val autoListen  = intent.getBooleanExtra("auto_start_listening", false)
        val proactiveMsg = intent.getStringExtra("tars_proactive_msg")

        requestPermissions()

        setContent {
            TarsTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = TarsColors.Background) {
                    TarsApp(
                        viewModel        = viewModel,
                        autoStartListening = autoListen,
                        proactiveMessage = proactiveMsg
                    )
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (volumeShortcut.onKeyDown(keyCode)) return true
        return super.onKeyDown(keyCode, event)
    }

    private fun requestPermissions() {
        val toRequest = mutableListOf<String>()
        arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR,
            Manifest.permission.POST_NOTIFICATIONS
        ).forEach {
            if (ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED)
                toRequest.add(it)
        }
        if (toRequest.isNotEmpty()) permissionLauncher.launch(toRequest.toTypedArray())
        else com.tars.assistant.service.TarsProactiveReceiver.schedule(this)
    }
}

// ── App Navigation ────────────────────────────────────────────
@Composable
fun TarsApp(
    viewModel: TarsViewModel,
    autoStartListening: Boolean = false,
    proactiveMessage: String? = null
) {
    val isOnboarded by viewModel.isOnboarded.collectAsState()
    var bootDone by remember { mutableStateOf(false) }

    // Inject proactive message into chat on launch
    LaunchedEffect(proactiveMessage) {
        if (!proactiveMessage.isNullOrBlank() && isOnboarded) {
            viewModel.injectMessage(proactiveMessage)
        }
    }

    // Auto-start listening if launched from widget/tile
    LaunchedEffect(autoStartListening, isOnboarded, bootDone) {
        if (autoStartListening && isOnboarded && bootDone) {
            viewModel.startListening()
        }
    }

    if (isOnboarded && !bootDone) {
        BootSequence(onComplete = { bootDone = true })
        return
    }

    AnimatedContent(
        targetState = isOnboarded,
        transitionSpec = {
            if (targetState) fadeIn() + slideInHorizontally { it } togetherWith
                fadeOut() + slideOutHorizontally { -it }
            else fadeIn() togetherWith fadeOut()
        },
        label = "nav"
    ) { onboarded ->
        if (onboarded) ChatScreen(viewModel = viewModel)
        else OnboardingScreen(onComplete = { provider, apiKey, name -> viewModel.completeOnboarding(provider, apiKey, name) })
    }
}
