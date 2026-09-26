package com.kestrane.neondeck.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kestrane.neondeck.data.Grid
import com.kestrane.neondeck.data.Load

@Composable
fun <T> LoadView(state: Load<T>, onRetry: () -> Unit, content: @Composable (T) -> Unit) {
    when (state) {
        Load.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        is Load.Err -> Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(state.message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onRetry) { Text("Try again") }
        }
        is Load.Ok -> content(state.data)
    }
}

@Composable
fun StatCard(label: String, value: String, note: String?, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            if (note != null) {
                Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

/** Scrollable data grid: header stays pinned vertically, columns scroll sideways together. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DataGrid(grid: Grid, modifier: Modifier = Modifier, onRowClick: ((Int) -> Unit)? = null) {
    val cellWidth = 150.dp
    Box(modifier.horizontalScroll(rememberScrollState())) {
        LazyColumn(Modifier.width(cellWidth * grid.columns.size.coerceAtLeast(1))) {
            stickyHeader {
                Row(Modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
                    grid.columns.forEach { GridCell(it, cellWidth, header = true) }
                }
            }
            itemsIndexed(grid.rows) { i, row ->
                Row(Modifier.clickable(enabled = onRowClick != null) { onRowClick?.invoke(i) }) {
                    row.forEach { GridCell(it, cellWidth) }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            if (grid.rows.isEmpty()) {
                item {
                    Text(
                        "No rows",
                        Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun GridCell(text: String, width: Dp, header: Boolean = false) {
    Text(
        text,
        Modifier.width(width).padding(horizontal = 10.dp, vertical = 8.dp),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
        color = when {
            header -> MaterialTheme.colorScheme.primary
            text == "NULL" -> MaterialTheme.colorScheme.outline
            else -> MaterialTheme.colorScheme.onSurface
        },
    )
}

@Composable
fun RowDialog(columns: List<String>, row: List<String>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Row details") },
        text = {
            LazyColumn {
                itemsIndexed(columns) { i, name ->
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Text(name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        SelectionContainer {
                            Text(row.getOrElse(i) { "" }, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                        }
                    }
                }
            }
        },
    )
}

fun formatBytes(bytes: Double): String {
    if (bytes < 1024) return "${bytes.toLong()} B"
    val units = listOf("KB", "MB", "GB", "TB")
    var v = bytes
    var i = -1
    while (v >= 1024 && i < units.lastIndex) {
        v /= 1024
        i++
    }
    return "%.1f %s".format(v, units[i])
}
