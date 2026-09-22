package com.kestrane.supadeck.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.kestrane.supadeck.data.Overview

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
                StatCard("Users", o.usersTotal.toString(), "+${o.new24h} in 24h", Modifier.weight(1f))
                StatCard("Active, 7 days", o.active7d.toString(), null, Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Database size", formatBytes(o.dbBytes), null, Modifier.weight(1f))
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
            SectionCard("Sign-ups, last 14 days") { BarChart(o.signups) }
        }
        item {
            SectionCard("Largest tables") {
                if (o.topTables.isEmpty()) Muted("No tables yet")
                o.topTables.forEach { (name, bytes, rows) ->
                    KeyValue(name, "${formatBytes(bytes)}, ~${rows.toLong()} rows")
                }
            }
        }
        item {
            SectionCard("Storage") {
                if (o.storage.isEmpty()) Muted("No buckets")
                o.storage.forEach { (bucket, objects, bytes) ->
                    KeyValue(bucket, "${formatBytes(bytes)}, $objects files")
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

@Composable
private fun BarChart(points: List<Pair<String, Int>>) {
    val max = (points.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    val bar = MaterialTheme.colorScheme.primary
    androidx.compose.foundation.layout.Column {
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val n = points.size.coerceAtLeast(1)
            val gap = 6.dp.toPx()
            val minBar = 3.dp.toPx()
            val w = (size.width - gap * (n - 1)) / n
            points.forEachIndexed { i, (_, v) ->
                val h = (size.height * v / max).coerceAtLeast(minBar)
                drawRoundRect(
                    color = bar.copy(alpha = if (v == 0) 0.25f else 1f),
                    topLeft = Offset(i * (w + gap), size.height - h),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Muted(points.firstOrNull()?.first ?: "")
            Muted("peak $max")
            Muted(points.lastOrNull()?.first ?: "")
        }
    }
}
