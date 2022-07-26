package dev.ahrsoft.cameralibrary

import android.app.Activity
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import dev.ahrsoft.cameralibrary.databinding.ActivityMainBinding
import dev.ahrsoft.easycameraandgallery.*

class MainActivity : AppCompatActivity() {

    lateinit var binding: ActivityMainBinding
    private lateinit var resultScan: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initCall()

        val optionsCamera = OptionsCamera(
            ratio = Ratio.RATIO_16_9,
            path = "Evidences",
            flash = Flash.Off,
            galleryCount = 4
        )

        binding.textInput.setOnClickListener {
            EasyCamera.start(this, optionsCamera = optionsCamera, resultScan = resultScan)
        }
    }

    private fun initCall() {
        resultScan = registerForActivityResult(androidx.activity.result.contract.
        ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val list = result.data?.getStringArrayListExtra(EasyCamera.IMAGE_RESULTS)
                print(list)
            }
        }
    }
}