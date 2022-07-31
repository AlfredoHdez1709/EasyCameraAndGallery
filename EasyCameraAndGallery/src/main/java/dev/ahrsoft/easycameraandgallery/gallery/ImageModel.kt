package dev.ahrsoft.easycameraandgallery.gallery

import android.net.Uri

data class ImageModel (
    var image : String?= null,
    var uri : Uri? = null,
    var isSelected : Boolean = false
)