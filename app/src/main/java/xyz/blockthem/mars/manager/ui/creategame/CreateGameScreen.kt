package xyz.blockthem.mars.manager.ui.creategame

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.blockthem.mars.manager.api.Board
import xyz.blockthem.mars.manager.api.User
import xyz.blockthem.mars.manager.ui.common.ColorDot
import xyz.blockthem.mars.manager.ui.common.marsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGameScreen(onBack: () -> Unit, onCreated: () -> Unit) {
    val vm = marsViewModel { CreateGameViewModel(it.api) }
    val state by vm.state.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current

    LaunchedEffect(state.created) { if (state.created) onCreated() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New game") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            Button(
                onClick = {
                    focus.clearFocus()
                    vm.create()
                },
                enabled = state.canCreate,
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(16.dp)
                    .height(52.dp),
            ) {
                if (state.creating) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Text("Create game")
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle("Players")
            Text(
                "You are added automatically. Search opponents by nickname.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val duplicates = state.duplicateIds
            state.slots.forEachIndexed { index, slot ->
                PlayerField(
                    label = "Player ${index + 2}",
                    slot = slot,
                    isDuplicate = slot.id in duplicates,
                    onQueryChange = { vm.onQueryChange(slot.id, it) },
                    onFocus = { vm.onFocus(slot.id) },
                    onSelect = { vm.select(slot.id, it) },
                    onRemove = { vm.removePlayer(slot.id) },
                )
            }
            if (state.canAddPlayer) {
                OutlinedButton(onClick = vm::addPlayer) {
                    Icon(Icons.Filled.PersonAdd, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Add player")
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle("Board")
            BoardPicker(state.options.board) { b -> vm.updateOptions { it.copy(board = b) } }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle("Expansions")
            val o = state.options
            ToggleRow("Corporate Era", o.corporateEra) { v -> vm.updateOptions { it.copy(corporateEra = v) } }
            ToggleRow("Prelude", o.prelude) { v -> vm.updateOptions { it.copy(prelude = v) } }
            ToggleRow("Venus Next", o.venusNext) { v -> vm.updateOptions { it.copy(venusNext = v) } }
            ToggleRow("Solar Phase", o.solarPhase) { v -> vm.updateOptions { it.copy(solarPhase = v) } }
            ToggleRow("Colonies", o.colonies) { v -> vm.updateOptions { it.copy(colonies = v) } }
            Spacer(Modifier.height(8.dp))
        }
    }

    state.error?.let { err ->
        AlertDialog(
            onDismissRequest = vm::dismissError,
            title = { Text("Couldn't create the game") },
            text = { Text(err) },
            confirmButton = { TextButton(onClick = vm::dismissError) { Text("OK") } },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerField(
    label: String,
    slot: PlayerSlot,
    isDuplicate: Boolean,
    onQueryChange: (String) -> Unit,
    onFocus: () -> Unit,
    onSelect: (User) -> Unit,
    onRemove: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val showMenu = focused && slot.selected == null && slot.suggestions.isNotEmpty()
    val hint = when {
        isDuplicate -> "Already added"
        !focused && slot.query.isNotEmpty() && slot.selected == null -> "Pick a player from the list"
        else -> null
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        ExposedDropdownMenuBox(
            expanded = showMenu,
            onExpandedChange = {},
            modifier = Modifier.weight(1f),
        ) {
            OutlinedTextField(
                value = slot.query,
                onValueChange = onQueryChange,
                label = { Text(label) },
                singleLine = true,
                isError = hint != null,
                supportingText = if (hint != null) {
                    { Text(hint) }
                } else {
                    null
                },
                trailingIcon = {
                    if (slot.selected != null) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focus.clearFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryEditable)
                    .onFocusChanged {
                        focused = it.isFocused
                        if (it.isFocused) onFocus()
                    },
            )
            ExposedDropdownMenu(expanded = showMenu, onDismissRequest = { focus.clearFocus() }) {
                slot.suggestions.forEach { user ->
                    DropdownMenuItem(
                        text = { Text(user.nickname) },
                        leadingIcon = { ColorDot(user.color) },
                        trailingIcon = {
                            Text(
                                "${user.elo}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        onClick = {
                            onSelect(user)
                            focus.clearFocus()
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    )
                }
            }
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Close, contentDescription = "Remove $label")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoardPicker(board: Board, onSelect: (Board) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = board.label(),
            onValueChange = {},
            readOnly = true,
            label = { Text("Board") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Board.entries.forEach { b ->
                DropdownMenuItem(
                    text = { Text(b.label()) },
                    onClick = {
                        onSelect(b)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun Board.label() = when (this) {
    Board.RANDOM -> "Random"
    else -> name.lowercase().replaceFirstChar { it.uppercase() }
}
