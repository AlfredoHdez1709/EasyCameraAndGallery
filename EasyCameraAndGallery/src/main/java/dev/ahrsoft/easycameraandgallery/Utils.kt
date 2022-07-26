package dev.ahrsoft.easycameraandgallery

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowInsets
import androidx.camera.core.AspectRatio
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class Utils(private val activity: Activity) {
    fun getRealPathFromUri(contentUri: Uri): String {
        var realPath = String()
        contentUri.path?.let { path ->
            val databaseUri: Uri
            val selection: String?
            val selectionArgs: Array<String>?
            if (path.contains("/document/image:")) {
                databaseUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                selection = "_id=?"
                selectionArgs = arrayOf(DocumentsContract.getDocumentId(contentUri).split(":")[1])
            } else {
                databaseUri = contentUri
                selection = null
                selectionArgs = null
            }
            try {
                val column = "_data"
                val projection = arrayOf(column)
                val cursor = activity.contentResolver.query(
                    databaseUri,
                    projection,
                    selection,
                    selectionArgs,
                    null
                )
                cursor?.let {
                    if (it.moveToFirst()) {
                        val columnIndex = cursor.getColumnIndexOrThrow(column)
                        realPath = cursor.getString(columnIndex)
                    }
                    cursor.close()
                }
            } catch (e: Exception) {
                println(e)
            }
        }
        return realPath
    }

    private fun rationAuto(width: Int, height: Int): Int {
        Log.d(Constant.TAG, "rationAuto: $width - $height")
        val previewRatio = max(width, height).toDouble() / min(width, height)
        if (abs(previewRatio - Constant.RATIO_4_3_VALUE) <= abs(previewRatio - Constant.RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }

    fun aspectRadio(ratio: Ratio): Int {
        return when (ratio) {
            Ratio.RATIO_4_3 -> AspectRatio.RATIO_4_3
            Ratio.RATIO_16_9 -> AspectRatio.RATIO_16_9
            Ratio.RATIO_AUTO -> rationAuto(getScreenWidth(), getScreenHeight())
        }
    }

    fun getScreenHeight(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = activity.windowManager.currentWindowMetrics
            val bounds: Rect = windowMetrics.bounds
            val insets: android.graphics.Insets =
                windowMetrics.windowInsets.getInsetsIgnoringVisibility(
                    WindowInsets.Type.systemBars()
                )
            if (activity.resources.configuration.orientation
                == Configuration.ORIENTATION_LANDSCAPE
                && activity.resources.configuration.smallestScreenWidthDp < 600
            ) { // landscape and phone
                bounds.height()
            } else { // portrait or tablet
                val navigationBarSize = insets.bottom
                bounds.height() - navigationBarSize
            }
        } else {
            val outMetrics = DisplayMetrics()
            activity.windowManager.defaultDisplay.getMetrics(outMetrics)
            outMetrics.heightPixels
        }
    }

    private fun getScreenWidth(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = activity.windowManager.currentWindowMetrics
            val bounds: Rect = windowMetrics.bounds
            val insets: android.graphics.Insets =
                windowMetrics.windowInsets.getInsetsIgnoringVisibility(
                    WindowInsets.Type.systemBars()
                )
            if (activity.resources.configuration.orientation
                == Configuration.ORIENTATION_LANDSCAPE
                && activity.resources.configuration.smallestScreenWidthDp < 600
            ) { // landscape and phone
                val navigationBarSize = insets.right + insets.left
                bounds.width() - navigationBarSize
            } else { // portrait or tablet
                bounds.width()
            }
        } else {
            val outMetrics = DisplayMetrics()
            activity.windowManager.defaultDisplay.getMetrics(outMetrics)
            outMetrics.widthPixels
        }
    }

}