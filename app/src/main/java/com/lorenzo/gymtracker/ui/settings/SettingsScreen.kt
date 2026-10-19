package com.lorenzo.gymtracker.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lorenzo.gymtracker.BuildConfig
import com.lorenzo.gymtracker.ui.components.ConfirmDialog
import java.time.LocalDate

private const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel.message) {
        viewModel.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.message = null
        }
    }
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(viewModel::exportBackup)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pendingRestore = uri }
    val excelLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(XLSX_MIME)) { uri ->
        uri?.let(viewModel::exportExcel)
    }
    var confirmSample by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = { TopAppBar(title = { Text("Settings") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Your data", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(
                "Save everything (exercises, routines and workouts) to a file, e.g. on Google Drive, to move it to a new phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { exportLauncher.launch("gymtracker-backup-${LocalDate.now()}.json") }, modifier = Modifier.weight(1f)) {
                    Text("Export backup")
                }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "application/octet-stream")) }, modifier = Modifier.weight(1f)) {
                    Text("Restore")
                }
            }
            Text(
                "Export your training log as a spreadsheet: every set, each workout, sets per muscle each week, your exercises and routines.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = { excelLauncher.launch("gymtracker-${LocalDate.now()}.xlsx") }, modifier = Modifier.fillMaxWidth()) {
                Text("Export to Excel")
            }
            if (BuildConfig.DEBUG) {
                Text("Developer", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Text(
                    "Only in the dev build. Adds routines and about three months of made-up workouts to try the app with.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = { confirmSample = true }, modifier = Modifier.fillMaxWidth()) { Text("Add sample data") }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = { confirmDelete = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Delete all data") }
        }
    }

    pendingRestore?.let { uri ->
        ConfirmDialog(
            title = "Restore backup?",
            text = "This replaces ALL data in the app (exercises, routines, workouts) with the contents of the backup.",
            confirm = "Replace everything",
            onDismiss = { pendingRestore = null },
            onConfirm = {
                viewModel.importBackup(uri)
                pendingRestore = null
            },
        )
    }
    if (confirmSample) {
        ConfirmDialog(
            title = "Add sample data?",
            text = "Adds Push, Pull, Legs and Core circuit routines and about three months of workouts, on top of what's already here. " +
                "Delete all data removes them again.",
            confirm = "Add",
            onDismiss = { confirmSample = false },
            onConfirm = {
                confirmSample = false
                viewModel.addSampleData()
            },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete all data?",
            text = "This permanently deletes your workouts, routines and exercises; the default exercises come back. " +
                "It can't be undone.\n\nTo keep a copy, cancel and use Export backup first.",
            confirm = "Delete everything",
            destructive = true,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                viewModel.deleteAllData()
            },
        )
    }
}
