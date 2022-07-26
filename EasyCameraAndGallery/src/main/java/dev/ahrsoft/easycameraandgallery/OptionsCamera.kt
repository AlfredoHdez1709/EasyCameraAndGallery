package dev.ahrsoft.easycameraandgallery

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.io.Serializable


data class OptionsCamera(
    var count : Int = 1,
    var path : String = "EasyCamera/",
    var flash: Flash = Flash.Auto,
    var isFrontFacing : Boolean = false,
    var ratio : Ratio = Ratio.RATIO_AUTO,
    var galleryCount : Int = 0
) : Serializable

@Parcelize
enum class Flash : Parcelable {
    On, Off, Auto
}

@Parcelize
enum class Ratio : Parcelable {
    RATIO_4_3, RATIO_16_9, RATIO_AUTO
}