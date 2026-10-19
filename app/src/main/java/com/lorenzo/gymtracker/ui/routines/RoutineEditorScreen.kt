package com.lorenzo.gymtracker.ui.routines

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import com.lorenzo.gymtracker.ui.components.AppIcons
import com.lorenzo.gymtracker.ui.components.ConfirmDialog
import com.lorenzo.gymtracker.ui.components.NumberField
import com.lorenzo.gymtracker.ui.components.SegmentedChoice
import com.lorenzo.gymtracker.ui.formatClock
import com.lorenzo.gymtracker.ui.musclesLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineEditorScreen(viewModel: RoutineEditorViewModel, onBack: () -> Unit, onAddExercises: () -> Unit) {
    val vm = viewModel
    var confirmLeave by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val leave = { if (vm.dirty) confirmLeave = true else onBack() }
    BackHandler(enabled = vm.dirty) { confirmLeave = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (vm.isEditing) "Edit routine" else "New routine") },
                navigationIcon = { IconButton(onClick = leave) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = { TextButton(onClick = { vm.save(onBack) }, enabled = vm.isValid) { Text("Save") } },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(
                    vm.name, vm::changeName,
                    label = { Text("Name") },
                    placeholder = { Text("e.g. Push day") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Text(
                    "Each exercise is done by reps or for a time. Leave a weight empty to use what you lifted last time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            itemsIndexed(vm.items, key = { _, item -> item.key }) { index, item ->
                RoutineItemCard(
                    item = item,
                    canMoveUp = index > 0,
                    canMoveDown = index < vm.items.lastIndex,
                    onChange = vm::update,
                    onMove = { vm.move(item, it) },
                    onRemove = { vm.remove(item) },
                )
            }
            item {
                OutlinedButton(onClick = onAddExercises, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add exercise")
                }
            }
            item {
                Button(onClick = { vm.save(onBack) }, enabled = vm.isValid, modifier = Modifier.fillMaxWidth()) { Text("Save routine") }
            }
            if (vm.isEditing) {
                item {
                    OutlinedButton(
                        onClick = { confirmDelete = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Delete routine") }
                }
            }
        }
    }

    if (confirmLeave) {
        ConfirmDialog(
            title = "Discard changes?",
            text = "Your changes to this routine haven't been saved.",
            confirm = "Discard",
            dismiss = "Keep editing",
            onDismiss = { confirmLeave = false },
            onConfirm = {
                confirmLeave = false
                onBack()
            },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete routine?",
            text = "Workouts you did with it stay in your history.",
            confirm = "Delete",
            destructive = true,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                vm.delete(onBack)
            },
        )
    }
}

@Composable
private fun RoutineItemCard(
    item: RoutineItem,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onChange: (RoutineItem) -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.exercise.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Text(item.exercise.musclesLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { onMove(-1) }, enabled = canMoveUp) { Icon(AppIcons.ArrowUp, contentDescription = "Move up") }
                IconButton(onClick = { onMove(1) }, enabled = canMoveDown) { Icon(AppIcons.ArrowDown, contentDescription = "Move down") }
                IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, contentDescription = "Remove ${item.exercise.name}") }
            }
            Column(Modifier.padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SegmentedChoice(listOf(false, true), item.timed, { if (it) "Time" else "Reps" }, { onChange(item.copy(timed = it)) })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(item.sets, { onChange(item.copy(sets = it)) }, "Sets", isError = item.setsValue == null, modifier = Modifier.weight(1f))
                    if (item.timed) {
                        NumberField(
                            item.duration, { onChange(item.copy(duration = it)) }, "Time", suffix = "s",
                            isError = item.durationValue == null, supportingText = item.durationValue?.let(::formatClock),
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        NumberField(item.reps, { onChange(item.copy(reps = it)) }, "Reps", isError = item.repsValue == null, modifier = Modifier.weight(1f))
                    }
                    RestField(item, onChange, Modifier.weight(1f))
                }
                if (item.exercise.weighted) {
                    NumberField(
                        item.weight, { onChange(item.copy(weight = it)) }, "Weight", decimals = true, suffix = "kg",
                        placeholder = "Same as last time", isError = item.weightInvalid, modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun RestField(item: RoutineItem, onChange: (RoutineItem) -> Unit, modifier: Modifier) {
    NumberField(
        item.rest, { onChange(item.copy(rest = it)) }, "Rest", suffix = "s",
        isError = item.restValue == null, supportingText = item.restValue?.let(::formatClock),
        modifier = modifier,
    )
}
