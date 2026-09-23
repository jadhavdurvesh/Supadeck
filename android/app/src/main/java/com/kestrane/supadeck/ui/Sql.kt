package com.kestrane.supadeck.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val SAMPLE = "select id, email, created_at\nfrom auth.users\norder by created_at desc\nlimit 20"

@Composable
fun SqlScreen(vm: AdminViewModel) {
    var query by rememberSaveable { mutableStateOf(SAMPLE) }
    var open by remember { mutableStateOf<Int?>(null) }

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 220.dp),
            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Read-only — enforced by Supabase, not just this app",
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = { vm.runSql(query) }, enabled = query.isNotBlank()) { Text("Run query") }
        }

        vm.sqlResult?.let { result ->
            Box(Modifier.weight(1f)) {
                LoadView(result, onRetry = { vm.runSql(query) }) { grid ->
                    Column(Modifier.fillMaxSize()) {
                        Text(
                            "${grid.rows.size} rows",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        DataGrid(grid, Modifier.weight(1f).fillMaxWidth(), onRowClick = { open = it })
                    }
                    open?.let { i ->
                        grid.rows.getOrNull(i)?.let { RowDialog(grid.columns, it) { open = null } }
                    }
                }
            }
        }
    }
}
