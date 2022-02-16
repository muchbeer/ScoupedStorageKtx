package com.muchbeer.scoupedstoragektx.model

import android.net.Uri

data class SharedStorage(
    val id: Long,
    val name: String,
    val width: Int,
    val height: Int,
    val contentUri: Uri
)
