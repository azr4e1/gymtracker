package com.lorenzo.gymtracker.ui.components

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import com.lorenzo.gymtracker.ui.toDecimalOrNull

/**
 * Text and selection of a string-backed field that selects its whole text when focused, so typing
 * replaces it. Pass [field] and [onValueChange] to the text field and add [modifier] to it.
 */
class SelectAllOnFocus(initial: String) {
    var field by mutableStateOf(TextFieldValue(initial))
        private set

    // Set on focus: the tap that focuses the field also moves the cursor, which would undo the selection.
    private var selectAllOnTap = false

    var isFocused = false
        private set

    // Texts sent by typing that the owner may not have stored yet. When it stores them a moment
    // later (e.g. through a flow), they mustn't overwrite what was typed since.
    private val sent = ArrayDeque<String>()

    /**
     * Follows changes made outside the field, such as clearing it. Values [same] as the text
     * (e.g. "62" stored for "62." being typed) leave the text alone.
     */
    fun sync(value: String, same: (String, String) -> Boolean = { a, b -> a == b }) {
        if (same(value, field.text)) {
            sent.clear()
        } else if (value in sent) {
            while (sent.firstOrNull() != value) sent.removeFirst()
        } else {
            sent.clear()
            field = TextFieldValue(value, TextRange(value.length))
        }
    }

    /** Applies an edit; [onTextChange] is called when the text (not just the selection) changed. */
    fun onValueChange(new: TextFieldValue, onTextChange: (String) -> Unit) {
        if (new.text == field.text && selectAllOnTap) {
            selectAllOnTap = false
            field = new.copy(selection = TextRange(0, new.text.length))
        } else {
            selectAllOnTap = false
            val changed = new.text != field.text
            field = new
            if (changed) {
                sent.addLast(new.text)
                onTextChange(new.text)
            }
        }
    }

    val modifier: Modifier = Modifier.onFocusChanged { state ->
        isFocused = state.isFocused
        if (state.isFocused && !selectAllOnTap) {
            field = field.copy(selection = TextRange(0, field.text.length))
            selectAllOnTap = true
        } else if (!state.isFocused) {
            selectAllOnTap = false
        }
    }
}

/**
 * Also drops the focus when the keyboard is closed (e.g. with Back), so tapping the field again
 * selects its text instead of just moving the cursor.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun rememberSelectAllOnFocus(value: String, same: (String, String) -> Boolean = { a, b -> a == b }): SelectAllOnFocus {
    val state = remember { SelectAllOnFocus(value) }.also { it.sync(value, same) }
    val keyboardOpen = WindowInsets.isImeVisible
    val focusManager = LocalFocusManager.current
    LaunchedEffect(keyboardOpen) {
        if (!keyboardOpen && state.isFocused) focusManager.clearFocus()
    }
    return state
}

/**
 * Text field with a numeric keyboard. The value stays a string so partial input like "1," is allowed.
 * Focusing it selects the whole value, so typing replaces it. [label] null makes a compact field
 * for table rows, with [placeholder] shown when empty.
 */
@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String?,
    modifier: Modifier = Modifier,
    decimals: Boolean = false,
    suffix: String? = null,
    placeholder: String? = null,
    isError: Boolean = false,
    supportingText: String? = null,
) {
    // The owner may store the number and give it back formatted ("62." comes back as "62").
    val state = rememberSelectAllOnFocus(value) { a, b -> a == b || (a.isNotEmpty() && a.toDecimalOrNull() == b.toDecimalOrNull()) }
    OutlinedTextField(
        value = state.field,
        onValueChange = { new ->
            if (new.text.all { it.isDigit() || (decimals && (it == '.' || it == ',')) }) state.onValueChange(new, onValueChange)
        },
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let {
            { Text(it, textAlign = if (label == null) TextAlign.Center else TextAlign.Start, modifier = Modifier.fillMaxWidth()) }
        },
        suffix = suffix?.let { { Text(it) } },
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        singleLine = true,
        textStyle = if (label == null) LocalTextStyle.current.copy(textAlign = TextAlign.Center) else LocalTextStyle.current,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimals) KeyboardType.Decimal else KeyboardType.Number,
            imeAction = ImeAction.Next,
        ),
        modifier = modifier.then(state.modifier),
    )
}
