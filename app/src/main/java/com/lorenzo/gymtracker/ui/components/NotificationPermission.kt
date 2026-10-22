package com.lorenzo.gymtracker.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.edit

/**
 * When [enabled], asks once for permission to show notifications (Android 13+), so the timer
 * notification can appear. Not asked again after an answer: it can be changed in system settings.
 */
@Composable
fun AskNotificationPermissionOnce(enabled: Boolean) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(enabled) {
        if (!enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val prefs = context.getSharedPreferences("permissions", Context.MODE_PRIVATE)
        if (granted || prefs.getBoolean(KEY_ASKED, false)) return@LaunchedEffect
        prefs.edit { putBoolean(KEY_ASKED, true) }
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

private const val KEY_ASKED = "askedNotifications"
