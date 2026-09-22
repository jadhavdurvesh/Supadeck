package com.kestrane.supadeck.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kestrane.supadeck.data.UserRow
import kotlinx.coroutines.delay

private fun UserRow.title() = email.ifBlank { phone.ifBlank { id.take(8) } }
private fun UserRow.isBanned() = bannedUntil.isNotBlank()

@Composable
fun UsersScreen(vm: AdminViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf<UserRow?>(null) }

    LaunchedEffect(query) {
        if (query.isNotEmpty()) delay(350) // debounce typing
        vm.searchUsers(query)
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search by email or phone") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        )
        LoadView(vm.users, onRetry = { vm.searchUsers(query) }) { page ->
            if (page.users.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text("No users match", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(page.users, key = { it.id }) { u -> UserItem(u) { selected = u } }
                    if (page.hasMore) {
                        item {
                            TextButton(onClick = { vm.fetchUsers() }, modifier = Modifier.fillMaxWidth()) {
                                Text("Load more users")
                            }
                        }
                    }
                }
            }
        }
    }

    selected?.let { u ->
        UserDialog(
            user = u,
            onToggleBan = {
                vm.setBan(u.id, !u.isBanned())
                selected = null
            },
            onDismiss = { selected = null },
        )
    }
}

@Composable
private fun UserItem(u: UserRow, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(u.title().take(1).uppercase(), color = MaterialTheme.colorScheme.primary)
            }
        },
        headlineContent = { Text(u.title(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text("Joined ${shortDate(u.createdAt)}, last seen ${shortDate(u.lastSignIn)}") },
        trailingContent = {
            when {
                u.isBanned() -> Text("Banned", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                !u.confirmed -> Text("Unconfirmed", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            }
        },
    )
}

@Composable
private fun UserDialog(user: UserRow, onToggleBan: () -> Unit, onDismiss: () -> Unit) {
    val banned = user.isBanned()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(user.title(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Field("User ID", user.id) }
                item { Field("Phone", user.phone.ifBlank { "none" }) }
                item { Field("Sign-in method", user.provider.ifBlank { "unknown" }) }
                item { Field("Created", shortDate(user.createdAt)) }
                item { Field("Last sign-in", shortDate(user.lastSignIn)) }
                item { Field("Email confirmed", if (user.confirmed) "yes" else "no") }
                if (banned) item { Field("Banned until", user.bannedUntil.take(10)) }
                if (user.metadata.isNotBlank()) item { Field("User metadata", user.metadata) }
            }
        },
        confirmButton = {
            TextButton(onClick = onToggleBan) {
                Text(
                    if (banned) "Unban user" else "Ban user",
                    color = if (banned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun Field(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        SelectionContainer { Text(value, fontFamily = FontFamily.Monospace, fontSize = 13.sp) }
    }
}
