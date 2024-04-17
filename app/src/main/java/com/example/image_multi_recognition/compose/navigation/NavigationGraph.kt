package com.example.image_multi_recognition.compose.navigation

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import com.example.image_multi_recognition.AppData
import com.example.image_multi_recognition.DefaultConfiguration
import com.example.image_multi_recognition.compose.view.*
import com.example.image_multi_recognition.compose.view.settingScreen.FullScreenSelectionView
import com.example.image_multi_recognition.compose.view.settingScreen.SettingScreen
import com.example.image_multi_recognition.util.getCallSiteInfoFunc

@Composable
fun NavigationGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    navTopAppBar: @Composable () -> Unit,
    navBottomAppBar: @Composable () -> Unit,
    provideInitialSetting: () -> AppData,
) {
    NavHost(
        modifier = modifier,
        navController = navController,
        startDestination = Destination.PHOTO.navRoute
    ) {
        composable(
            route = Destination.PHOTO.navRoute,
        ) {
            PhotoComposable(
                viewModel = hiltViewModel(),
                onImageClick = { album, originalIndex ->
                    // set argument "label" to empty whitespace String
                    "${Destination.SINGLE_IMAGE.route}/1/${album}/$originalIndex".let { route ->
                        Log.d(getCallSiteInfoFunc(), "Navigation route: $route")
                        navController.navigate(route) {
                            // launchSingleTop = true
                            // popUpTo(navController.graph.findStartDestination().id) {
                            //     saveState = true
                            // }
                            // restoreState = true
                        }
                    }
                },
                // onTopBottomBarHidden = onTopBottomBarHidden,
                // rootSnackBarHostState = rootSnackBarHostState,
                provideInitialSetting = provideInitialSetting,
                navTopAppBar = navTopAppBar,
                navBottomAppBar = navBottomAppBar,
            )
        }
        composable(
            route = Destination.ALBUM.navRoute,
        ) {
            AlbumComposable(
                viewModel = hiltViewModel(),
                navBottomAppBar = navBottomAppBar,
                onSettingClick = { navController.navigate(Destination.SETTING.route) },
                moveToFavorites = {
                    "${Destination.ALBUM_PHOTO.route}/${DefaultConfiguration.FAVORITES_ALBUM_ID}".let { route ->
                        navController.navigate(route)
                    }
                }
            ) { album ->
                "${Destination.ALBUM_PHOTO.route}/${album}".let { route ->
                    navController.navigate(route) {

                    }
                }
            }
        }
        composable(
            route = Destination.SEARCH.navRoute
        ) {
            // PhotoComposable(navController = navController, viewModel = photoViewModel)
            PhotoSearchComposable(
                viewModel = hiltViewModel(),
                navTopAppBar = navTopAppBar,
                navBottomAppBar = navBottomAppBar,
                provideInitialSetting = provideInitialSetting
            ) { label ->
                "${Destination.LABEL_PHOTO.route}/${label}".let { route ->
                    navController.navigate(route) {
                    }
                }
            }
        }
        composable(
            route = Destination.LABEL.navRoute,
            deepLinks = listOf(navDeepLink { uriPattern = DefaultConfiguration.ML_DEEP_LINK })
        ) {
            LabelingComposable(
                viewModel = hiltViewModel(),
                navBottomAppBar = navBottomAppBar,
                provideInitialSetting = provideInitialSetting,
                onSettingClick = { navController.navigate(Destination.SETTING.route) }
            ) { albumId ->
                "${Destination.ALBUM_PHOTO_LABELING}/${albumId}".let { route ->
                    navController.navigate(route) {
                    }
                }
            }
        }

        composable(
            route = Destination.SINGLE_IMAGE.navRoute,
            arguments = Destination.SINGLE_IMAGE.arguments
        ) {
            // use hiltViewModel() to get a ViewModel whose lifecycle is scoped to the destination
            SingleImageComposable(
                viewModel = hiltViewModel(),
                onBackClick = { navController.popBackStack() },
                onAlbumEmptyBack = {
                    navController.popBackStack()
                    if (!Destination.entries.filter { it.isRootDestination }
                            .containsNavRoute(navController.currentDestination)) {
                        navController.popBackStack()
                    }
                }
            )
        }
        // The same PhotoComposable for different route with different PhotoViewModel instance of different lifecycle
        composable(
            route = Destination.ALBUM_PHOTO.navRoute,
            // do not forget arguments!
            arguments = Destination.ALBUM_PHOTO.arguments
        ) {
            // set all the parameters related to navigation in NavigationGraph
            PhotoComposable(
                viewModel = hiltViewModel(),
                onBack = {
                    navController.popBackStack()
                },
                onImageClick = { album, originalIndex ->
                    "${Destination.SINGLE_IMAGE.route}/1/${album}/$originalIndex".let { route ->
                        navController.navigate(route) {
//                            launchSingleTop = true
//                            popUpTo(navController.graph.findStartDestination().id) {
//                                saveState = true
//                            }
//                            restoreState = true
                        }
                    }
                },
                customAppBar = true,
                navTopAppBar = navTopAppBar,
                navBottomAppBar = navBottomAppBar,
                provideInitialSetting = provideInitialSetting
            )
        }
        composable(
            route = Destination.LABEL_PHOTO.navRoute,
            arguments = Destination.LABEL_PHOTO.arguments
        ) {
            LabelPhotoComposable(
                viewModel = hiltViewModel(),
                onImageClick = { originalIndex, label ->
                    "${Destination.SINGLE_IMAGE.route}/2/${label}/$originalIndex".let { route ->
                        navController.navigate(route) {
                            // Not Done yet
                        }
                    }
                },
                onBack = {
                    navController.popBackStack()
                },
                provideInitialSetting = provideInitialSetting
            )
        }
        composable(
            route = Destination.ALBUM_PHOTO_LABELING.navRoute,
            arguments = Destination.ALBUM_PHOTO_LABELING.arguments,
            // "album" is a parameter similar to the Compose Navigation
            deepLinks = listOf(navDeepLink { uriPattern = "${DefaultConfiguration.ML_ALBUM_DEEP_LINK}/{album}" })
        ) {
            AlbumPhotoLabelingComposable(
                viewModel = hiltViewModel(),
                onImageClick = { album, originalIndex ->
                    "${Destination.SINGLE_IMAGE.route}/3/${album}/$originalIndex".let { route ->
                        navController.navigate(route) {
                            // Not Done yet
                        }
                    }
                },
                onBack = {
                    navController.popBackStack()
                },
                provideInitialSetting = provideInitialSetting
            )
        }
        composable(
            route = Destination.SETTING.navRoute
        ) {
            SettingScreen(
                viewModel = hiltViewModel(),
                onBack = { navController.popBackStack() },
                provideInitialSetting = provideInitialSetting,
                moveToFullScreenSelectionView = {
                    navController.navigate(Destination.EXCLUDED_LABELS_SETTING.route)
                }
            )
        }
        composable(
            route = Destination.EXCLUDED_LABELS_SETTING.navRoute
        ) {
            FullScreenSelectionView(
                viewModel = hiltViewModel(),
                onDismiss = { navController.popBackStack() },
                provideInitialSetting = provideInitialSetting,
            )
        }
    }
}