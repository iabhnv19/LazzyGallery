package com.lazzy.gallery.ui.navigation

import java.net.URLEncoder

sealed class Screen(val route: String) {
    object Recent : Screen("recent")
    object Albums : Screen("albums")
    object Bin : Screen("bin")
    object Search : Screen("search")
    object Favorites : Screen("favorites")
    object Settings : Screen("settings")
    object AlbumDetail : Screen("albumDetail/{albumName}") {
        fun createRoute(albumName: String) = "albumDetail/${URLEncoder.encode(albumName, "UTF-8")}"
    }
    object Detail : Screen("detail/{mediaId}?albumName={albumName}&source={source}") {
        fun createRoute(mediaId: Long, albumName: String? = null, source: String? = null): String {
            val encodedAlbum = albumName?.let { URLEncoder.encode(it, "UTF-8") } ?: ""
            val src = source ?: ""
            return "detail/$mediaId?albumName=$encodedAlbum&source=$src"
        }
    }
}
