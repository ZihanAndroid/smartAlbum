package com.example.image_multi_recognition.compose.navigation

import android.util.Log
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.image_multi_recognition.AppData
import com.example.image_multi_recognition.compose.view.*
import com.example.image_multi_recognition.compose.view.settingScreen.SettingScreen
import com.example.image_multi_recognition.util.getCallSiteInfoFunc

@Composable
fun NavigationGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    // Note the hiltViewModel() here does not within any destination,
    // it can be seen as the global ViewModel for the app which has the same lifecycle as the Activity does
    // photoViewModel: PhotoViewModel,
    rootSnackBarHostState: SnackbarHostState,
    onTopBottomBarHidden: (Boolean) -> Unit,
    provideInitialSetting: () -> AppData,
) {
    val coroutineScope = rememberCoroutineScope()

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
                onTopBottomBarHidden = onTopBottomBarHidden,
                rootSnackBarHostState = rootSnackBarHostState,
                provideInitialSetting = provideInitialSetting
            )
        }
        composable(
            route = Destination.ALBUM.navRoute,
        ) {
            AlbumComposable(viewModel = hiltViewModel()) { album ->
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
                provideInitialSetting = provideInitialSetting
            ) { label ->
                "${Destination.LABEL_PHOTO.route}/${label}".let { route ->
                    navController.navigate(route) {
                        // Not done yet!
                    }
                }
            }
        }
        composable(
            route = Destination.LABEL.navRoute,
        ) {
            LabelingComposable(
                viewModel = hiltViewModel(),
                rootSnackBarHostState = rootSnackBarHostState,
                provideInitialSetting = provideInitialSetting,
                onSettingClick = {
                    navController.navigate(Destination.SETTING.route)
                }
            ) { albumId ->
                "${Destination.ALBUM_PHOTO_LABELING}/${albumId}".let { route ->
                    navController.navigate(route) {
                        // Not done yet!
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
                    if(!Destination.entries.filter { it.isRootDestination }.containsNavRoute(navController.currentDestination)){
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
                customTopBar = true,
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
            arguments = Destination.ALBUM_PHOTO_LABELING.arguments
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
                onBack = {
                    navController.popBackStack()
                },
                provideInitialSetting = provideInitialSetting
            )
        }
    }
}