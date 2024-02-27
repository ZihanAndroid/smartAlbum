package com.example.image_multi_recognition.compose.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.image_multi_recognition.compose.view.AlbumComposable
import com.example.image_multi_recognition.compose.view.PhotoComposable
import com.example.image_multi_recognition.compose.view.SingleImageComposable

@Composable
fun NavigationGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        modifier = modifier,
        navController = navController,
        startDestination = Destination.PHOTO.navRoute
    ) {
        composable(
            route = Destination.PHOTO.navRoute
        ) {
            PhotoComposable(viewModel = hiltViewModel(), navController = navController)
        }
        composable(
            route = Destination.ALBUM.navRoute
        ) {
            AlbumComposable(viewModel = hiltViewModel())
        }
        composable(
            route = Destination.SEARCH.navRoute
        ) {
            PhotoComposable(viewModel = hiltViewModel(), navController = navController)
        }
        composable(
            route = Destination.LABEL.navRoute
        ) {
            AlbumComposable(viewModel = hiltViewModel())
        }
        composable(
            route = Destination.SINGLE_IMAGE.navRoute,
            arguments = Destination.SINGLE_IMAGE.arguments
        ){
            SingleImageComposable(viewModel = hiltViewModel())
        }
    }
}