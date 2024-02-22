package com.example.image_multi_recognition.compose.navigation

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.image_multi_recognition.R
import com.example.image_multi_recognition.util.ExifHelper
import com.example.image_multi_recognition.util.getCallSiteInfoFunc

@Composable
fun Home(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val snackBarHostState = remember { SnackbarHostState() }
    var contentShownBySnackBar by remember { mutableStateOf("") }

    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentDestination = currentBackStack?.destination
    val destination = ExifHelper.buildDestinationFromRoute(currentDestination?.route)
    Log.d(getCallSiteInfoFunc(), "current destination: ${currentDestination?.route?.split("/")?.get(0)}")

    // for Root destination, use Scaffold,
    // for non-root destination, avoid showing topBar and bottomBar here, (they are parts of destination composable, not part of Scaffold)
    if (destination?.isRootDestination == true) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackBarHostState) },
            topBar = {
                // show different topBar based on current destination
                ScaffoldTopBar(
                    popUpItems = listOf(stringResource(R.string.NavPopUpItem_NotDone)),
                    onPopUpItemClick = listOf(
                        { contentShownBySnackBar = it }
                    )
                )
            },
            bottomBar = {
                ScaffoldBottomBar(
                    navController = navController,
                    items = Destination.entries.filter { it.isRootDestination }
                )
            }
        ) { suggestedPadding ->
            NavigationGraph(
                navController = navController,
                modifier = Modifier.padding(suggestedPadding)
            )
        }
    } else {
        NavigationGraph(
            navController = navController,
        )
    }

    LaunchedEffect(contentShownBySnackBar) {
        if (contentShownBySnackBar.isNotEmpty()) {
            snackBarHostState.showSnackbar(message = contentShownBySnackBar)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScaffoldTopBar(
    popUpItems: List<String> = emptyList(),
    onPopUpItemClick: List<(String) -> Unit> = emptyList(),
    onSettingClick: () -> Unit = {}
) {
    var menuOpened by rememberSaveable { mutableStateOf(false) }

    TopAppBar(
        modifier = Modifier.wrapContentHeight(),
        title = {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp),
            )
        },
        actions = {
            IconButton(
                onClick = onSettingClick
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "topBarSettings",
                )
            }
            IconButton(
                onClick = { menuOpened = true }
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "topBarMoreOptions",
                )
            }
            DropdownMenu(
                expanded = menuOpened,
                onDismissRequest = { menuOpened = false }
            ) {
                popUpItems.forEachIndexed { index, item ->
                    if (index > 0) Divider()
                    DropdownMenuItem(
                        onClick = {
                            menuOpened = false
                            onPopUpItemClick[index](item)
                        },
                        text = {
                            Text(
                                text = item,
                                style = MaterialTheme.typography.titleSmall
                            )
                        },
                        modifier = Modifier.wrapContentHeight()
                    )
                }
            }
        }
    )
}

@Composable
fun ScaffoldBottomBar(
    navController: NavController,
    items: List<Destination>,
) {
    BottomAppBar {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination
        items.forEach { destination ->
            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = ImageVector.vectorResource(destination.icon!!),
                        contentDescription = destination.name,
                        modifier = Modifier.size(28.dp)
                    )
                },
                label = {
                    Text(
                        text = stringResource(destination.label!!),
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                selected = currentDestination?.hierarchy?.any {
                    Log.d(
                        "ScaffoldBottomBar",
                        "route: ${it.route}, destination route: ${destination.route}, destination navRoute: ${destination.navRoute}"
                    )
                    it.route == destination.route
                } ?: false,
                onClick = {
                    navController.navigate(destination.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                alwaysShowLabel = false
            )
        }
    }
}
