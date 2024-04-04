package com.example.image_multi_recognition.compose.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.res.stringResource
import androidx.navigation.*
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
        icon = R.drawable.baseline_photo_album_24,
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
        icon = R.drawable.baseline_add_photo_alternate_24,
        isRootDestination = true
    ),

    SINGLE_IMAGE(
        route = "SINGLE_IMAGE",
        compulsoryArguments = mapOf(
//            "album" to NavType.LongType,
//            "label" to NavType.StringType,
            // argumentType: 1 means "album", 2 means "label", 3 means "album with image not labeled"
            "argumentType" to NavType.IntType,
            "argumentValue" to NavType.StringType,
            "initialKey" to NavType.IntType
        ),
        isRootDestination = false
    ),
    ALBUM_PHOTO(
        route = "ALBUM_PHOTO",
        compulsoryArguments = mapOf("album" to NavType.LongType),
        isRootDestination = false
    ),
    LABEL_PHOTO(
        route = "LABEL_PHOTO",
        compulsoryArguments = mapOf("label" to NavType.StringType),
        isRootDestination = false
    ),
    ALBUM_PHOTO_LABELING(
        route = "ALBUM_PHOTO_LABELING",
        compulsoryArguments = mapOf("album" to NavType.LongType),
        isRootDestination = false
    ),
    SETTING(
        route = "SETTING",
        isRootDestination = false
    );

    val navRoute: String
        get() = "$route${
            compulsoryArguments?.keys?.fold("") { acc, s -> "$acc/{$s}" } ?: ""
        }${
            optionalArgument?.keys?.fold("") { acc, _ ->
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

    // the route of NavDestination contains arguments, return Destination without remove those arguments
    companion object {
        fun buildDestinationFromNav(navDestination: NavDestination?): Destination? =
            navDestination?.route?.let {
                Destination.valueOf(it.split("/")[0])
            }
    }

    fun sameRouteAs(navDestination: NavDestination?): Boolean {
        return buildDestinationFromNav(navDestination) == this
    }
}
fun List<Destination>.includeRouteAs(navDestination: NavDestination?): Boolean {
    forEach {
        if(it.sameRouteAs(navDestination)) return true
    }
    return false
}