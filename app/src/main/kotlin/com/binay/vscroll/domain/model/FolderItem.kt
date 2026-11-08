package com.binay.vscroll.domain.model

data class FolderItem(
    val id: String,
    val name: String,
    val relativePath: String,
    val videoCount: Int,
    val coverVideoId: Long? = null,
    val coverVideoUri: String? = null
)
