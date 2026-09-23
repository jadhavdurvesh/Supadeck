package com.kestrane.supadeck.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Sources = listOf("edge_logs", "postgres_logs", "auth_logs", "function_logs")

@Composable
fun LogsScreen(vm: AdminViewModel) {
    LaunchedEffect(Unit) { vm.loadLogs() }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Sources.forEach { s ->
                FilterChip(
                    selected = vm.logSource == s,
                    onClick = { vm.selectLogSource(s) },
                    label = { Text(s.removeSuffix("_logs")) },
                )
            }
        }
        LoadView(vm.logs, onRetry = { vm.loadLogs(force = true) }) { r ->
            when {
                r.error != null -> Note(r.error, error = true)
                r.lines.isEmpty() -> Note("No log lines in the last hour.")
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(r.lines) { line ->
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(
                                shortDate(line.time),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                line.message,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun Note(text: String, error: Boolean = false) {
    Text(
        text,
        Modifier.padding(16.dp),
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
