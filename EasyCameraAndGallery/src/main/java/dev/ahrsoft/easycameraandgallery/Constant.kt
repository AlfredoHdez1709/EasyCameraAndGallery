package dev.ahrsoft.easycameraandgallery

import android.Manifest
import android.os.Build
import androidx.annotation.RequiresApi

object Constant {
    const val TAG = "CameraAndGallery"
    const val FILENAME = "yyyy-MM-dd-HH-mm-ss-SSS"
    const val PHOTO_EXTENSION = "jpeg"
    const val RATIO_4_3_VALUE = 4.0 / 3.0
    const val RATIO_16_9_VALUE = 16.0 / 9.0
    val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE
    )

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    val REQUIRED_PERMISSIONS_TIRAMISU = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.READ_MEDIA_IMAGES
    )

    const val REQUEST_CODE_PERMISSIONS = 10
}