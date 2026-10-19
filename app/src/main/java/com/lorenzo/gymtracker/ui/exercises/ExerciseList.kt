package com.lorenzo.gymtracker.ui.exercises

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.lorenzo.gymtracker.data.db.entity.Exercise
import com.lorenzo.gymtracker.data.db.entity.Muscle
import com.lorenzo.gymtracker.ui.components.EmptyText
import com.lorenzo.gymtracker.ui.label
import com.lorenzo.gymtracker.ui.musclesLabel
import com.lorenzo.gymtracker.ui.trackingLabel

/**
 * The exercise library with a search field and a filter by main muscle. Exercises in
 * [selectedIds] are ticked. [header] is shown between the filters and the list.
 */
@Composable
fun ExerciseList(
    exercises: List<Exercise>,
    onClick: (Exercise) -> Unit,
    modifier: Modifier = Modifier,
    selectedIds: Collection<Long> = emptyList(),
    header: @Composable () -> Unit = {},
) {
    var query by rememberSaveable { mutableStateOf("") }
    var muscle by rememberSaveable { mutableStateOf<Muscle?>(null) }
    val shown = exercises.filter { e ->
        (query.isBlank() || e.name.contains(query.trim(), ignoreCase = true)) && (muscle == null || muscle in e.primaryMuscles)
    }

    Column(modifier.fillMaxSize().imePadding()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search exercises") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, contentDescription = "Clear search") }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
        )
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(selected = muscle == null, onClick = { muscle = null }, label = { Text("All") }) }
            items(Muscle.entries) { m ->
                FilterChip(selected = muscle == m, onClick = { muscle = if (muscle == m) null else m }, label = { Text(m.label) })
            }
        }
        header()
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
            if (shown.isEmpty()) item { EmptyText("No exercise matches. Create it with \"New exercise\".") }
            items(shown, key = { it.id }) { e ->
                val selected = e.id in selectedIds
                ListItem(
                    headlineContent = { Text(e.name) },
                    supportingContent = { Text(e.musclesLabel) },
                    trailingContent = {
                        if (selected) {
                            Icon(Icons.Filled.Check, contentDescription = "Picked", tint = MaterialTheme.colorScheme.primary)
                        } else {
                            e.trackingLabel?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .then(if (selected) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier)
                        .clickable { onClick(e) },
                )
            }
        }
    }
}
