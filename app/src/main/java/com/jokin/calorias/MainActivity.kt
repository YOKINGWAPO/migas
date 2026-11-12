package com.jokin.calorias

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jokin.calorias.data.settings.AppSettings
import com.jokin.calorias.ui.AppRoot
import com.jokin.calorias.ui.theme.CaloriasTheme
import kotlinx.coroutines.flow.map

class MainActivity : ComponentActivity() {
    private var startScan by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        startScan = savedInstanceState == null && intent?.action == ACTION_SCAN
        val c = (application as CaloriasApp).container
        // Loading state is null, so the goal wizard only opens once we know there is no goal.
        val needsGoal = c.energy.observeGoal().map { it == null }
        setContent {
            val settings by c.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            val missingGoal by needsGoal.collectAsStateWithLifecycle(initialValue = false)
            CaloriasTheme(settings) {
                AppRoot(startScan = startScan, onScanHandled = { startScan = false }, needsGoal = missingGoal)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == ACTION_SCAN) startScan = true
    }

    companion object {
        const val ACTION_SCAN = "com.jokin.calorias.SCAN"
    }
}
