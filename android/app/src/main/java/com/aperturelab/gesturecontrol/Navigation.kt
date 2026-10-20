package com.aperturelab.gesturecontrol

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.aperturelab.gesturecontrol.ui.settings.MappingScreen
import com.aperturelab.gesturecontrol.ui.settings.PermissionsScreen
import com.aperturelab.gesturecontrol.ui.settings.SettingsScreen
import com.aperturelab.gesturecontrol.ui.settings.TuningScreen

@Composable
fun MainNavigation() {
  val backStack = rememberNavBackStack(Main)
  val contentModifier = Modifier.safeDrawingPadding().padding(0.dp)

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<Main> {
          SettingsScreen(
            onOpenPermissions = { backStack.add(Permissions) },
            onOpenMapping = { packageName -> backStack.add(Mapping(packageName)) },
            onOpenTuning = { backStack.add(Tuning) },
            modifier = contentModifier,
          )
        }
        entry<Permissions> {
          PermissionsScreen(onBack = { backStack.removeLastOrNull() }, modifier = contentModifier)
        }
        entry<Tuning> {
          TuningScreen(onBack = { backStack.removeLastOrNull() }, modifier = contentModifier)
        }
        entry<Mapping> { key ->
          MappingScreen(
            packageName = key.packageName,
            onBack = { backStack.removeLastOrNull() },
            modifier = contentModifier,
          )
        }
      },
  )
}
