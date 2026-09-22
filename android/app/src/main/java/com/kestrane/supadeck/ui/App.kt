package com.kestrane.supadeck.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel

enum class Tab(val label: String, val icon: ImageVector) {
    Overview("Overview", Icons.Outlined.Dashboard),
    Tables("Tables", Icons.Outlined.TableChart),
    Users("Users", Icons.Outlined.People),
    Sql("SQL", Icons.Outlined.Code),
    Logs("Logs", Icons.Outlined.Description),
}

@Composable
fun SupaDeckApp(vm: AdminViewModel = viewModel()) {
    if (vm.signedIn) MainScaffold(vm) else LoginScreen(vm)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold(vm: AdminViewModel) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    val tab = Tab.entries[index]

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tab.label) },
                actions = {
                    IconButton(onClick = { vm.refresh(tab) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { vm.signOut() }) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign out")
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = i == index,
                        onClick = { index = i },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                Tab.Overview -> OverviewScreen(vm)
                Tab.Tables -> TablesScreen(vm)
                Tab.Users -> UsersScreen(vm)
                Tab.Sql -> SqlScreen(vm)
                Tab.Logs -> LogsScreen(vm)
            }
        }
    }
}
