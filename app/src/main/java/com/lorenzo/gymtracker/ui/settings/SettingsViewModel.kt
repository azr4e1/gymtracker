package com.lorenzo.gymtracker.ui.settings

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lorenzo.gymtracker.data.backup.BackupRepository
import com.lorenzo.gymtracker.data.export.ExportRepository
import com.lorenzo.gymtracker.data.repository.SampleDataRepository
import com.lorenzo.gymtracker.data.repository.WorkoutTimers
import com.lorenzo.gymtracker.ui.appViewModelFactory
import com.lorenzo.gymtracker.ui.plural
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val backupRepository: BackupRepository,
    private val exportRepository: ExportRepository,
    private val sampleData: SampleDataRepository,
    private val timers: WorkoutTimers,
) : ViewModel() {
    /** One-off message to show in a snackbar (backup results). */
    var message by mutableStateOf<String?>(null)

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            message = runCatching { backupRepository.export(uri) }
                .fold({ "Backup saved (${plural(it, "workout", "workouts")})" }, { "Backup failed: ${it.message}" })
        }
    }

    fun exportExcel(uri: Uri) {
        viewModelScope.launch {
            message = runCatching { exportRepository.exportExcel(uri) }
                .fold({ "Excel file saved (${plural(it, "workout", "workouts")})" }, { "Export failed: ${it.message}" })
        }
    }

    /** Dev builds only: adds made-up routines and about three months of workouts. */
    fun addSampleData() {
        viewModelScope.launch {
            message = runCatching { sampleData.addSampleData() }
                .fold({ "Added ${plural(it, "sample workout", "sample workouts")}" }, { "Couldn't add sample data: ${it.message}" })
        }
    }

    fun importBackup(uri: Uri) {
        timers.cancelAll()
        viewModelScope.launch {
            message = runCatching { backupRepository.import(uri) }
                .fold({ "Restored ${plural(it, "workout", "workouts")}" }, { "Restore failed: ${it.message}" })
        }
    }

    fun deleteAllData() {
        timers.cancelAll()
        viewModelScope.launch {
            backupRepository.deleteAll()
            message = "All data deleted"
        }
    }

    companion object {
        val Factory = appViewModelFactory { SettingsViewModel(it.backupRepository, it.exportRepository, it.sampleDataRepository, it.timers) }
    }
}
