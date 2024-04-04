package com.example.image_multi_recognition.compose.navigation

import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.image_multi_recognition.AppData
import com.example.image_multi_recognition.R
import com.example.image_multi_recognition.util.getCallSiteInfoFunc
import com.example.image_multi_recognition.viewmodel.PhotoViewModel

@Composable
fun Home(
    modifier: Modifier = Modifier,
    refreshAllImages: () -> Unit,
    provideInitialSetting: ()->AppData,
    // photoViewModel: PhotoViewModel,
) {

    val navController = rememberNavController()
    val snackBarHostState = remember { SnackbarHostState() }
    var contentShownBySnackBar by remember { mutableStateOf("") }

    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentDestination = currentBackStack?.destination
    Log.d(getCallSiteInfoFunc(), "current destination: ${currentDestination?.route?.split("/")?.get(0)}")
    // the child destination may want to hide the top and bottom bar
    var topBottomBarHidden by rememberSaveable { mutableStateOf(false) }

    // for Root destination, use Scaffold,
    // for non-root destination, avoid showing topBar and bottomBar here, (they are parts of destination composable, not part of Scaffold)
    if (Destination.buildDestinationFromNav(currentDestination)?.isRootDestination == true) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackBarHostState) },
            topBar = {
                // show different topBar based on current destination
                if (!topBottomBarHidden && !Destination.LABEL.sameRouteAs(currentDestination)) {
                    ScaffoldTopBar(
                        destination = currentDestination,
                        popUpItems = listOf(stringResource(R.string.NavPopUpItem_NotDone)),
                        onPopUpItemClick = listOf(
                            { contentShownBySnackBar = it }
                        ),
                        refreshAllImages = refreshAllImages,
                        onSettingClick = {
                            navController.navigate(Destination.SETTING.route)
                        }
                    )
                }
            },
            bottomBar = {
                if (!topBottomBarHidden) {
                    ScaffoldBottomBar(
                        navController = navController,
                        items = Destination.entries.filter { it.isRootDestination },
                        currentDestination = currentDestination
                    )
                }
            },
            modifier = modifier
        ) { suggestedPadding ->
            NavigationGraph(
                navController = navController,
                modifier = Modifier.padding(suggestedPadding),
                // photoViewModel = photoViewModel,
                rootSnackBarHostState = snackBarHostState,
                onTopBottomBarHidden = { topBottomBarHidden = it },
                provideInitialSetting = provideInitialSetting
            )
        }
    } else {
        NavigationGraph(
            navController = navController,
            // photoViewModel = photoViewModel,
            onTopBottomBarHidden = { topBottomBarHidden = it },
            rootSnackBarHostState = snackBarHostState,
            provideInitialSetting = provideInitialSetting
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
    destination: NavDestination?,
    refreshAllImages: () -> Unit,
    popUpItems: List<String> = emptyList(),
    onPopUpItemClick: List<(String) -> Unit> = emptyList(),
    onSettingClick: () -> Unit = {},
) {
    var menuOpened by rememberSaveable { mutableStateOf(false) }

    TopAppBar(
        modifier = Modifier.wrapContentHeight(),
        title = {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
            )
        },
        actions = {
            // the refresh is done automatically, do not need to refresh manually
            // if (listOf(Destination.PHOTO, Destination.ALBUM, Destination.LABEL).includeRouteAs(destination)) {
            //     IconButton(
            //         onClick = refreshAllImages
            //     ) {
            //         Icon(
            //             imageVector = Icons.Default.Refresh,
            //             contentDescription = "topBarMoreOptions",
            //         )
            //     }
            // }
            IconButton(
                onClick = onSettingClick
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "topBarSettings",
                )
            }
            DropdownMenu(
                expanded = menuOpened,
                onDismissRequest = { menuOpened = false }
            ) {
                popUpItems.forEachIndexed { index, item ->
                    if (index > 0) HorizontalDivider()
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
    currentDestination: NavDestination?,
) {
    BottomAppBar(
        // modifier = Modifier.crop(vertical = 10.dp)
    ) {
        items.forEach { destination ->
            NavigationBarItem(
                icon = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = ImageVector.vectorResource(destination.icon!!),
                            contentDescription = destination.name,
                            modifier = Modifier.size(28.dp)
                        )
                        if (destination.sameRouteAs(currentDestination)) {
                            Text(
                                text = stringResource(destination.label!!),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                },
                selected = destination.sameRouteAs(currentDestination),
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
