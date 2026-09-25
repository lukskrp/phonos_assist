package com.phonosassist.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.phonosassist.R
import com.phonosassist.domain.AssistantLanguages

/**
 * Persistent language bar under the header: choose the language the user records
 * in and the language the assistant replies (and speaks) in. Setting these
 * explicitly avoids multi-language confusion.
 */
@Composable
fun AssistantLanguageBar(
    inputLanguage: String,
    responseLanguage: String,
    onInputLanguageChange: (String) -> Unit,
    onResponseLanguageChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LanguagePicker(
            icon = {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            },
            label = stringResource(R.string.lang_input),
            currentText = AssistantLanguages.label(inputLanguage),
            includeAuto = false,
            onSelect = onInputLanguageChange,
            modifier = Modifier.weight(1f),
        )
        LanguagePicker(
            icon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            },
            label = stringResource(R.string.lang_response),
            currentText = if (responseLanguage == AssistantLanguages.AUTO) {
                stringResource(R.string.lang_same_as_input)
            } else {
                AssistantLanguages.label(responseLanguage)
            },
            includeAuto = true,
            onSelect = onResponseLanguageChange,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun LanguagePicker(
    icon: @Composable () -> Unit,
    label: String,
    currentText: String,
    includeAuto: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val sameAsInput = stringResource(R.string.lang_same_as_input)

    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box {
            TextButton(onClick = { expanded = true }) {
                icon()
                Spacer(Modifier.width(6.dp))
                Text(
                    text = currentText,
                    maxLines = 1,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                if (includeAuto) {
                    DropdownMenuItem(
                        text = { Text(sameAsInput) },
                        onClick = {
                            onSelect(AssistantLanguages.AUTO)
                            expanded = false
                        },
                    )
                }
                AssistantLanguages.all.forEach { language ->
                    DropdownMenuItem(
                        text = { Text("${language.endonym} (${language.label})") },
                        onClick = {
                            onSelect(language.code)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}
