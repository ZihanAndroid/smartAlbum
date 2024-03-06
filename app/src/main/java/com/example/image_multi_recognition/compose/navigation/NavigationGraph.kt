package com.example.image_multi_recognition.compose.navigation

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.image_multi_recognition.DefaultConfiguration
import com.example.image_multi_recognition.compose.view.AlbumComposable
import com.example.image_multi_recognition.compose.view.AlbumPhotoComposable
import com.example.image_multi_recognition.compose.view.PhotoComposable
import com.example.image_multi_recognition.compose.view.SingleImageComposable
import com.example.image_multi_recognition.util.getCallSiteInfoFunc
import com.example.image_multi_recognition.viewmodel.PhotoViewModel
import com.example.image_multi_recognition.viewmodel.SingleImageViewModel

@Composable
fun NavigationGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    // Note the hiltViewModel() here does not within any destination,
    // it can be seen as the global ViewModel for the app which has the same lifecycle as the Activity does
    photoViewModel: PhotoViewModel
) {
    NavHost(
        modifier = modifier,
        navController = navController,
        startDestination = Destination.PHOTO.navRoute
    ) {
        composable(
            route = Destination.PHOTO.navRoute,
        ) {
            PhotoComposable(viewModel = photoViewModel) { originalIndex ->
                Log.d(getCallSiteInfoFunc(), "Navigating to: ${Destination.SINGLE_IMAGE.navRoute}")
                "${Destination.SINGLE_IMAGE.route}/${photoViewModel.currentAlbum}/$originalIndex".let { route ->
                    Log.d(getCallSiteInfoFunc(), "Navigation route: $route")
                    navController.navigate(route) {
                        launchSingleTop = true
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        restoreState = true
                    }
                }
            }
        }
        // The same PhotoComposable for different route with different PhotoViewModel instance of different lifecycle
        composable(
            route = Destination.ALBUM_PHOTO.navRoute,
            // do not forget arguments!
            arguments = Destination.ALBUM_PHOTO.arguments
        ) {
            // set all the parameters related to navigation in NavigationGraph
            AlbumPhotoComposable(
                viewModel = hiltViewModel(),
                onBack = {
                    navController.popBackStack()
                },
                onImageClick = { route ->
                    Log.d(getCallSiteInfoFunc(), "Navigating to: ${Destination.SINGLE_IMAGE.navRoute}")
                    Log.d(getCallSiteInfoFunc(), "Navigation route: $route")
                    navController.navigate(route) {
                        launchSingleTop = true
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        restoreState = true
                    }
                }
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
        }
        composable(
            route = Destination.LABEL.navRoute
        ) {
            AlbumComposable(viewModel = hiltViewModel()) { album ->

            }
        }
        composable(
            route = Destination.SINGLE_IMAGE.navRoute,
            arguments = Destination.SINGLE_IMAGE.arguments
        ) {
            // use hiltViewModel() to get a ViewModel whose lifecycle is scoped to the destination
            SingleImageComposable(viewModel = hiltViewModel()) {
                navController.popBackStack()
            }
        }
    }
}