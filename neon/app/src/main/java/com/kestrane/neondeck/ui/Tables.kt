package com.kestrane.neondeck.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kestrane.neondeck.data.TableInfo

@Composable
fun TablesScreen(vm: AdminViewModel) {
    val selected = vm.selected
    BackHandler(enabled = selected != null) { vm.closeTable() }
    if (selected == null) TableList(vm) else TableDetail(vm, selected)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TableList(vm: AdminViewModel) {
    LaunchedEffect(Unit) { vm.loadTables() }
    var filter by rememberSaveable { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = filter,
            onValueChange = { filter = it },
            placeholder = { Text("Filter tables") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        )
        LoadView(vm.tables, onRetry = { vm.loadTables(force = true) }) { all ->
            val grouped = all.filter { it.name.contains(filter, ignoreCase = true) }.groupBy { it.schema }
            LazyColumn(Modifier.fillMaxSize()) {
                grouped.forEach { (schema, list) ->
                    stickyHeader {
                        Text(
                            schema,
                            Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    items(list, key = { "${it.schema}.${it.name}" }) { t ->
                        TableRow(t) { vm.openTable(t) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TableRow(t: TableInfo, onClick: () -> Unit) {
    val rlsOff = t.kind == "table" && !t.rls
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(t.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            val rows = if (t.estRows >= 0) ", about ${t.estRows} rows" else ""
            val rls = when {
                t.kind == "table" && t.rls -> ", RLS on"
                rlsOff -> ", RLS off"
                else -> ""
            }
            Text(t.kind + rows + rls, color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
    )
}

@Composable
private fun TableDetail(vm: AdminViewModel, t: TableInfo) {
    var mode by rememberSaveable { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { vm.closeTable() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to tables")
            }
            Column(Modifier.weight(1f)) {
                Text(t.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(t.schema, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilterChip(selected = mode == 0, onClick = { mode = 0 }, label = { Text("Data") })
            Spacer(Modifier.width(6.dp))
            FilterChip(selected = mode == 1, onClick = { mode = 1 }, label = { Text("Schema") })
        }

        if (mode == 0) DataTab(vm) else SchemaTab(vm)
    }
}

@Composable
private fun DataTab(vm: AdminViewModel) {
    var open by remember { mutableStateOf<Int?>(null) }
    LoadView(vm.rows, onRetry = { vm.loadRows(reset = true) }) { grid ->
        Column(Modifier.fillMaxSize()) {
            DataGrid(grid, Modifier.weight(1f).fillMaxWidth(), onRowClick = { open = it })
            if (grid.hasMore) {
                TextButton(
                    onClick = { vm.loadRows() },
                    enabled = !vm.loadingMore,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    if (vm.loadingMore) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text("Load more rows")
                }
            }
        }
        open?.let { i ->
            grid.rows.getOrNull(i)?.let { RowDialog(grid.columns, it) { open = null } }
        }
    }
}

@Composable
private fun SchemaTab(vm: AdminViewModel) {
    LoadView(vm.columns, onRetry = {}) { cols ->
        LazyColumn(Modifier.fillMaxSize()) {
            items(cols) { c ->
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text(c.name, fontFamily = FontFamily.Monospace) },
                    supportingContent = { Text(if (c.nullable) c.type else "${c.type}, required") },
                )
            }
        }
    }
}
