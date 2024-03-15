package com.example.image_multi_recognition.compose.navigation

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.image_multi_recognition.R
import com.example.image_multi_recognition.compose.statelessElements.crop
import com.example.image_multi_recognition.util.ExifHelper
import com.example.image_multi_recognition.util.getCallSiteInfoFunc
import com.example.image_multi_recognition.viewmodel.PhotoViewModel

@Composable
fun Home(
    modifier: Modifier = Modifier,
    photoViewModel: PhotoViewModel
) {

    val navController = rememberNavController()
    val snackBarHostState = remember { SnackbarHostState() }
    var contentShownBySnackBar by remember { mutableStateOf("") }

    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentDestination = currentBackStack?.destination
    Log.d(getCallSiteInfoFunc(), "current destination: ${currentDestination?.route?.split("/")?.get(0)}")

    // for Root destination, use Scaffold,
    // for non-root destination, avoid showing topBar and bottomBar here, (they are parts of destination composable, not part of Scaffold)
    if (Destination.buildDestinationFromNav(currentDestination)?.isRootDestination == true) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackBarHostState) },
            topBar = {
                // show different topBar based on current destination
                ScaffoldTopBar(
                    destination = currentDestination,
                    popUpItems = listOf(stringResource(R.string.NavPopUpItem_NotDone)),
                    onPopUpItemClick = listOf(
                        { contentShownBySnackBar = it }
                    )
                )
            },
            bottomBar = {
                ScaffoldBottomBar(
                    navController = navController,
                    items = Destination.entries.filter { it.isRootDestination },
                    currentDestination = currentDestination
                )
            },
            modifier = modifier
        ) { suggestedPadding ->
            NavigationGraph(
                navController = navController,
                modifier = Modifier.padding(suggestedPadding),
                photoViewModel = photoViewModel,
                rootSnackBarHostState = snackBarHostState
            )
        }
    } else {
        NavigationGraph(
            navController = navController,
            photoViewModel = photoViewModel
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
//            if (Destination.LABEL.sameRouteAs(destination)) {
//                IconButton(
//                    onClick = {}
//                ) {
//                    Icon(
//                        imageVector = ImageVector.vectorResource(R.drawable.baseline_new_label_24),
//                        contentDescription = "autoLabeling",
//                    )
//                }
//            }
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
                        if(destination.sameRouteAs(currentDestination)){
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
