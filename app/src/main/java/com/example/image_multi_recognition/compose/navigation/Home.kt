package com.example.image_multi_recognition.compose.navigation

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.image_multi_recognition.AppData
import com.example.image_multi_recognition.DefaultConfiguration
import com.example.image_multi_recognition.R
import com.example.image_multi_recognition.compose.statelessElements.crop
import com.example.image_multi_recognition.util.getCallSiteInfoFunc

@Composable
fun Home(provideInitialSetting: () -> AppData) {
    val navController = rememberNavController()
    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentDestination = currentBackStack?.destination
    Log.d(getCallSiteInfoFunc(), "current destination: ${currentDestination?.route?.split("/")?.get(0)}")

    NavigationGraph(
        navController = navController,
        // photoViewModel = photoViewModel,
        // onTopBottomBarHidden = { topBottomBarHidden = it },
        // rootSnackBarHostState = snackBarHostState,
        provideInitialSetting = provideInitialSetting,
        navTopAppBar = {
            ScaffoldTopBar(onSettingClick = { navController.navigate(Destination.SETTING.route) })
        },
        navBottomAppBar = {
            ScaffoldBottomBar(
                navController = navController,
                items = Destination.entries.filter { it.isRootDestination },
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScaffoldTopBar(
    refreshAllImages: () -> Unit = {},
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
    items: List<Destination>
) {
    BottomAppBar(
        modifier = Modifier.crop(vertical = DefaultConfiguration.NAV_BOTTOM_APP_BAR_CROPPED.dp)
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
                        if (destination.sameRouteAs(navController.currentDestination)) {
                            Text(
                                text = stringResource(destination.label!!),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                },
                selected = destination.sameRouteAs(navController.currentDestination),
                onClick = {
                    navController.navigate(destination.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                alwaysShowLabel = false,
                modifier = Modifier.graphicsLayer {
                    // It seems that Modifier.align(CenterVertically) has no effect to NavigationBarItem here,
                    // manually set the NavigationBarItem at the center height of BottomAppBar
                    translationY = -(10.dp).toPx()
                }
            )
        }
    }
}
