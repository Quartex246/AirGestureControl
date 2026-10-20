package com.aperturelab.gesturecontrol

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.aperturelab.gesturecontrol.domain.GestureEvent
import com.aperturelab.gesturecontrol.runtime.GestureRuntime
import com.aperturelab.gesturecontrol.service.CameraForegroundService
import com.aperturelab.gesturecontrol.settings.SettingsRepository
import com.aperturelab.gesturecontrol.theme.GestureControlTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    handleCommands(intent)
    enableEdgeToEdge()
    setContent {
      GestureControlTheme { Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { MainNavigation() } }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    handleCommands(intent)
  }

  /**
   * Entry points used by the settings surface (and by `adb shell am start` during
   * verification) to arm the foreground service and mount the camera.
   */
  private fun handleCommands(intent: Intent?) {
    when (intent?.action) {
      ACTION_ARM -> {
        CameraForegroundService.ensureStarted(this)
      }
      ACTION_DISARM -> {
        CameraForegroundService.requestUnmount()
        CameraForegroundService.stop(this)
      }
      ACTION_SET_ENABLED -> {
        val packageName = intent.getStringExtra(EXTRA_PACKAGE) ?: return
        val enabled = intent.getBooleanExtra(EXTRA_ENABLED, true)
        lifecycleScope.launch { SettingsRepository.get(this@MainActivity).setEnabled(packageName, enabled) }
      }
      ACTION_SET_OVERRIDE -> {
        val packageName = intent.getStringExtra(EXTRA_PACKAGE) ?: return
        val event = runCatching { GestureEvent.valueOf(intent.getStringExtra(EXTRA_GESTURE).orEmpty()) }.getOrNull() ?: return
        val action = runCatching {
          com.aperturelab.gesturecontrol.domain.GestureAction.valueOf(intent.getStringExtra(EXTRA_ACTION).orEmpty())
        }.getOrNull() ?: return
        lifecycleScope.launch {
          SettingsRepository.get(this@MainActivity).setOverrideAction(packageName, event, action)
        }
      }
      ACTION_TEST_GESTURE -> {
        val name = intent.getStringExtra(EXTRA_GESTURE) ?: return
        val event = runCatching { GestureEvent.valueOf(name) }.getOrNull() ?: return
        val delayMs = intent.getLongExtra(EXTRA_DELAY_MS, 0L)
        if (delayMs > 0) {
          lifecycleScope.launch {
            kotlinx.coroutines.delay(delayMs)
            GestureRuntime.gestureEvents.tryEmit(event)
          }
        } else {
          GestureRuntime.gestureEvents.tryEmit(event)
        }
      }
    }
  }

  companion object {
    const val ACTION_ARM = "com.aperturelab.gesturecontrol.action.ARM"
    const val ACTION_DISARM = "com.aperturelab.gesturecontrol.action.DISARM"
    const val ACTION_SET_ENABLED = "com.aperturelab.gesturecontrol.action.SET_ENABLED"
    const val ACTION_SET_OVERRIDE = "com.aperturelab.gesturecontrol.action.SET_OVERRIDE"
    const val ACTION_TEST_GESTURE = "com.aperturelab.gesturecontrol.action.TEST_GESTURE"
    const val EXTRA_PACKAGE = "package"
    const val EXTRA_ENABLED = "enabled"
    const val EXTRA_GESTURE = "gesture"
    const val EXTRA_ACTION = "action"
    const val EXTRA_DELAY_MS = "delayMs"
  }
}
