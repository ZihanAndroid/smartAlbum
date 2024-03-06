package com.example.image_multi_recognition.compose.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.res.stringResource
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavArgument
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.example.image_multi_recognition.R


enum class Destination(
    val route: String,
    val isRootDestination: Boolean = false, // the rootDestination shares the same top and bottom bar
    val compulsoryArguments: Map<String, NavType<*>>? = null,
    val optionalArgument: Map<String, NavType<*>>? = null,
    @StringRes val label: Int? = null,
    @DrawableRes val icon: Int? = null,
) {
    // no argument for Root destination
    PHOTO(
        route = "PHOTO",
        label = R.string.photos,
        icon = R.drawable.baseline_photo_24,
        isRootDestination = true,
    ),
    ALBUM(
        route = "ALBUM",
        label = R.string.albums,
        icon = R.drawable.baseline_album_24,
        isRootDestination = true
    ),
    SEARCH(
        route = "SEARCH",
        label = R.string.search,
        icon = R.drawable.baseline_image_search_24,
        isRootDestination = true
    ),
    LABEL(
        route = "LABEL",
        label = R.string.label,
        icon = R.drawable.baseline_new_label_24,
        isRootDestination = true
    ),

    SINGLE_IMAGE(
        route = "SINGLE_IMAGE",
        compulsoryArguments = mapOf("album" to NavType.LongType, "initialKey" to NavType.IntType),
        isRootDestination = false
    ),
    ALBUM_PHOTO(
        route = "ALBUM_PHOTO",
        compulsoryArguments = mapOf("album" to NavType.LongType),
        isRootDestination = false
    );



    val navRoute: String
        get() = "$route${
            compulsoryArguments?.keys?.fold("") { acc, s -> "$acc/{$s}" } ?: ""
        }${
            optionalArgument?.keys?.fold("") { acc, s ->
                if (acc.isEmpty()) "?$acc={$acc}"
                else "&$acc={$acc}"
            } ?: ""
        }"
    val arguments: List<NamedNavArgument>
        get() = mutableListOf<NamedNavArgument>().apply {
            compulsoryArguments?.let { arguments ->
                this.addAll(arguments.map { navArgument(name = it.key) { type = it.value } })
            }
            optionalArgument?.let { arguments ->
                this.addAll(arguments.map {
                    navArgument(
                        name = it.key
                    ) {
                        type = it.value
                        nullable = true
                    }
                })
            }
        }
}