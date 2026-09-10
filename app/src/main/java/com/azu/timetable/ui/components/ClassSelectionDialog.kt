package com.azu.timetable.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ClassSelectionDialog(
    initialSection: String = "",
    onDismiss: () -> Unit,
    onClassSelected: (String) -> Unit
) {
    var selectedYear by remember { mutableStateOf("II") }
    var selectedDept by remember { mutableStateOf("CSE") }
    var selectedSection by remember { mutableStateOf(initialSection.ifEmpty { "A" }) }

    val sections = listOf("A", "B", "C", "D", "E", "F", "G", "H", "I")
    val years = listOf("I", "II", "III", "IV")
    val depts = listOf("CSE", "IT", "ECE", "EEE", "MECH")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Select Your Class")
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Year Dropdown
                DropdownSelector(
                    label = "Year",
                    options = years,
                    selectedOption = selectedYear,
                    onOptionSelected = { selectedYear = it }
                )
                
                // Department Dropdown
                DropdownSelector(
                    label = "Department",
                    options = depts,
                    selectedOption = selectedDept,
                    onOptionSelected = { selectedDept = it }
                )
                
                // Section Dropdown
                DropdownSelector(
                    label = "Section",
                    options = sections,
                    selectedOption = selectedSection,
                    onOptionSelected = { selectedSection = it }
                )
                
                if (selectedYear != "II" || selectedDept != "CSE") {
                    Text(
                        text = "Currently, only II CSE schedules are available.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onClassSelected(selectedSection) },
                enabled = selectedSection.isNotEmpty() && selectedYear == "II" && selectedDept == "CSE"
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
        // Invisible clickable box over the text field
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { expanded = true }
        )
        DropdownMenu(
            expanded = expanded,
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
