package com.lorenzo.gymtracker.ui.exercises

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.lorenzo.gymtracker.data.db.entity.Muscle
import com.lorenzo.gymtracker.ui.components.ConfirmDialog
import com.lorenzo.gymtracker.ui.components.NumberField
import com.lorenzo.gymtracker.ui.components.SegmentedChoice
import com.lorenzo.gymtracker.ui.formatClock
import com.lorenzo.gymtracker.ui.label

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseEditorScreen(viewModel: ExerciseEditorViewModel, onBack: () -> Unit, onSaved: (Long) -> Unit, onDeleted: () -> Unit) {
    val vm = viewModel
    var confirmDelete by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (vm.isEditing) "Edit exercise" else "New exercise") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = { TextButton(onClick = { vm.save(onSaved) }, enabled = vm.isValid) { Text("Save") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(vm.name, { vm.name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())

            Text("Main muscles", style = MaterialTheme.typography.titleSmall)
            MuscleChips(vm.primary, vm::togglePrimary)
            Text("Also works (optional)", style = MaterialTheme.typography.titleSmall)
            Text(
                "Muscles that only help count half a set in your muscle stats.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MuscleChips(vm.secondary, vm::toggleSecondary)

            Text("Track by", style = MaterialTheme.typography.titleSmall)
            SegmentedChoice(listOf(false, true), vm.timed, { if (it) "Time" else "Reps" }, vm::changeTimed)
            Row(
                Modifier.fillMaxWidth().toggleable(value = vm.weighted, role = Role.Switch, onValueChange = vm::changeWeighted),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Track weight", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (vm.weighted) "Each set records the weight used." else "Bodyweight: sets don't record a weight.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = vm.weighted, onCheckedChange = null)
            }
            Text(
                "Defaults when you add it to a workout or a routine. You can change them there.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(vm.sets, { vm.sets = it }, "Sets", isError = vm.setsValue == null, modifier = Modifier.weight(1f))
                if (vm.timed) {
                    NumberField(
                        vm.duration, { vm.duration = it }, "Time", suffix = "s", isError = vm.durationValue == null,
                        supportingText = vm.durationValue?.let(::formatClock), modifier = Modifier.weight(1f),
                    )
                } else {
                    NumberField(vm.reps, { vm.reps = it }, "Reps", isError = vm.repsValue == null, modifier = Modifier.weight(1f))
                }
                NumberField(
                    vm.rest, { vm.rest = it }, "Rest", suffix = "s", isError = vm.restValue == null,
                    supportingText = vm.restValue?.let(::formatClock), modifier = Modifier.weight(1f),
                )
            }

            Button(onClick = { vm.save(onSaved) }, enabled = vm.isValid, modifier = Modifier.fillMaxWidth()) { Text("Save") }
            if (vm.isEditing) {
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Delete exercise") }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete ${vm.name.ifBlank { "exercise" }}?",
            text = "It's removed from the library and your routines. Past workouts and progress keep it.",
            confirm = "Delete",
            destructive = true,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                vm.delete(onDeleted)
            },
        )
    }
}

@Composable
private fun MuscleChips(selected: List<Muscle>, onToggle: (Muscle) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Muscle.entries.forEach { m ->
            FilterChip(selected = m in selected, onClick = { onToggle(m) }, label = { Text(m.label) })
        }
    }
}
