package app.motioncoach

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.motioncoach.ui.MotionCoachApp

class MainActivity : ComponentActivity() {
    private val model: CoachViewModel by viewModels()
    private var pendingExport = ""
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { model.foreground() }
    private val export = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runCatching { contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(pendingExport) } }
        pendingExport = ""
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            val requested = arrayOf("android.car.permission.CAR_SPEED", "android.car.permission.CAR_POWERTRAIN")
                .filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
            if (requested.isNotEmpty()) permissions.launch(requested.toTypedArray())
        }
        setContent {
            val state by model.state.collectAsState()
            MotionCoachApp(state, model) {
                pendingExport = model.exportJson()
                export.launch("motioncoach-trip.json")
            }
        }
    }
    override fun onStart() { super.onStart(); model.foreground() }
    override fun onStop() { model.background(); super.onStop() }
}
