package com.binay.vscroll.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.ui.graphics.vector.ImageVector

enum class NavDestination(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    SHORTS("shorts", "Shorts", Icons.Filled.PlayCircle, Icons.Outlined.PlayCircleOutline),
    LIKED("liked", "Liked", Icons.Filled.Favorite, Icons.Outlined.FavoriteBorder),
    ACTIVITY("activity", "Activity", Icons.AutoMirrored.Filled.TrendingUp, Icons.AutoMirrored.Outlined.TrendingUp),
    LIBRARY("library", "Library", Icons.Filled.VideoLibrary, Icons.Outlined.VideoLibrary),
    SETTINGS("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings);

    val icon: ImageVector get() = selectedIcon
}
