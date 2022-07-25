package dev.ahrsoft.easycameraandgallery

import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity

object EasyCamera {
    const val IMAGE_RESULTS = "image_results"
    fun start(context: FragmentActivity, optionsCamera: OptionsCamera, resultScan: ActivityResultLauncher<Intent>){
        val intent = Intent(context, CameraActivity::class.java)
        intent.putExtra("options",optionsCamera)
        resultScan.launch(intent)
    }

    fun start(context: Fragment, optionsCamera: OptionsCamera, resultScan: ActivityResultLauncher<Intent>){
        val intent = Intent(context.context, CameraActivity::class.java)
        intent.putExtra("options",optionsCamera)
        resultScan.launch(intent)
    }
}
