package com.azu.timetable.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.azu.timetable.ui.ClassDirectory
import com.azu.timetable.ui.ServerClass

@Composable
fun ClassSelectionDialog(
    directory: ClassDirectory?,
    initialSection: String = "",
    onDismiss: () -> Unit,
    onClassSelected: (String) -> Unit
) {
    val classes = directory?.classes.orEmpty()

    var selectedYear by remember { mutableStateOf<String?>(null) }
    var selectedDept by remember { mutableStateOf<String?>(null) }
    var selectedSection by remember { mutableStateOf<String?>(initialSection.ifEmpty { null }) }

    // Initialise the defaults from the current selection if it exists in the
    // directory, otherwise fall back to the first department/year the server
    // reports. Selections made by the user are never overridden.
    LaunchedEffect(directory) {
        if (directory == null) return@LaunchedEffect
        val current = classes.firstOrNull { it.sectionId == initialSection }
        if (selectedYear == null) {
            selectedYear = current?.year
                ?.takeIf { it in directory.years }
                ?: directory.years.firstOrNull { it.isNotBlank() }
        }
        if (selectedDept == null) {
            selectedDept = current?.department
                ?.takeIf { it in directory.departments }
                ?: directory.departments.firstOrNull { it.isNotBlank() }
        }
        if (selectedSection == null) {
            selectedSection = current?.sectionId
        }
    }

    val filteredSections = remember(directory, selectedYear, selectedDept) {
        classes
            .filter { it.year == selectedYear && it.department == selectedDept }
            .sortedWith(compareBy { it.sectionName })
    }

    // Keep the section in sync when the year/department filter changes.
    LaunchedEffect(selectedYear, selectedDept, filteredSections, selectedSection) {
        if (selectedSection != null && filteredSections.none { it.sectionId == selectedSection }) {
            selectedSection = filteredSections.firstOrNull()?.sectionId
        }
    }

    val confirmed = filteredSections.firstOrNull { it.sectionId == selectedSection }
        ?: filteredSections.firstOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Your Class") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (directory == null) {
                    Text(
                        text = "Loading classes from server…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (directory.years.isEmpty() || directory.departments.isEmpty() || classes.isEmpty()) {
                    Text(
                        text = "No classes are available on the server right now. Pull to refresh and try again.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    DropdownSelector(
                        label = "Year",
                        options = directory.years,
                        selectedOption = selectedYear.orEmpty(),
                        onOptionSelected = { selectedYear = it }
                    )

                    DropdownSelector(
                        label = "Department",
                        options = directory.departments,
                        selectedOption = selectedDept.orEmpty(),
                        onOptionSelected = { selectedDept = it }
                    )

                    val sectionNames = filteredSections.map { it.sectionName }
                    val shownName = confirmed?.sectionName ?: selectedSection.orEmpty()
                    DropdownSelector(
                        label = "Section",
                        options = sectionNames,
                        selectedOption = shownName,
                        onOptionSelected = { name ->
                            filteredSections.firstOrNull { it.sectionName == name }?.sectionId
                                ?.let { selectedSection = it }
                        }
                    )

                    confirmed?.let { section ->
                        Text(
                            text = section.classroom.takeIf { it.isNotBlank() }?.let {
                                "${section.year} ${section.department} • Room $it"
                            } ?: "${section.year} ${section.department}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { confirmed?.sectionId?.let(onClassSelected) },
                enabled = confirmed != null
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun DropdownSelector(
    label: String,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selectedOption,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown") },
            modifier = Modifier.fillMaxWidth()
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(enabled = options.isNotEmpty()) { expanded = true }
        )
        DropdownMenu(
            expanded = expanded && options.isNotEmpty(),
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}