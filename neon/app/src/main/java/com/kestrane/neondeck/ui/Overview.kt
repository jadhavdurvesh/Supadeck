package com.kestrane.neondeck.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kestrane.neondeck.data.Overview

@Composable
fun OverviewScreen(vm: AdminViewModel) {
    LaunchedEffect(Unit) { vm.loadOverview() }
    LoadView(vm.overview, onRetry = { vm.loadOverview(force = true) }) { o -> OverviewContent(o) }
}

@Composable
private fun OverviewContent(o: Overview) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("This database", formatBytes(o.dbBytes), null, Modifier.weight(1f))
                StatCard("Connections", o.connections.toString(), null, Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Public tables", o.tables.toString(), null, Modifier.weight(1f))
                StatCard("Cache hit", o.cacheHit?.let { "%.1f%%".format(it) } ?: "n/a", null, Modifier.weight(1f))
            }
        }
        item {
            SectionCard("Databases on this branch") {
                if (o.databases.isEmpty()) Muted("No databases found")
                o.databases.forEach { (name, bytes) -> KeyValue(name, formatBytes(bytes)) }
            }
        }
        item {
            SectionCard("Largest tables") {
                if (o.topTables.isEmpty()) Muted("No tables yet")
                o.topTables.forEach { (name, bytes, rows) ->
                    KeyValue(name, "${formatBytes(bytes)}, ~${rows.toLong()} rows")
                }
            }
        }
        item { Muted(o.version) }
    }
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun KeyValue(key: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(key, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
