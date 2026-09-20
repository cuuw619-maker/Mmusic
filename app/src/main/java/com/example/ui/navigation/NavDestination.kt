package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector

enum class NavDestination(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    HOME("home", "Главная", Icons.Rounded.Home),
    SEARCH("search", "Поиск", Icons.Rounded.Search),
    LIBRARY("library", "Медиатека", Icons.Rounded.LibraryMusic),
    PLAYLISTS("playlists", "Плейлисты", Icons.Rounded.QueueMusic),
    FAVORITES("favorites", "Избранное", Icons.Rounded.Favorite),
    SETTINGS("settings", "Настройки", Icons.Rounded.Settings)
}
