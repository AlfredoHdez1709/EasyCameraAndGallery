package dev.ahrsoft.easycameraandgallery.camera

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Build.VERSION
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.view.Surface.ROTATION_0
import android.webkit.MimeTypeMap
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toFile
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import dev.ahrsoft.easycameraandgallery.Constant.FILENAME
import dev.ahrsoft.easycameraandgallery.Constant.PHOTO_EXTENSION
import dev.ahrsoft.easycameraandgallery.Constant.REQUEST_CODE_PERMISSIONS
import dev.ahrsoft.easycameraandgallery.Constant.REQUIRED_PERMISSIONS
import dev.ahrsoft.easycameraandgallery.Constant.REQUIRED_PERMISSIONS_TIRAMISU
import dev.ahrsoft.easycameraandgallery.EasyCamera.IMAGE_RESULTS
import dev.ahrsoft.easycameraandgallery.Flash
import dev.ahrsoft.easycameraandgallery.OptionsCamera
import dev.ahrsoft.easycameraandgallery.R
import dev.ahrsoft.easycameraandgallery.Utils
import dev.ahrsoft.easycameraandgallery.commons.CameraFlipperState
import dev.ahrsoft.easycameraandgallery.databinding.ActivityCameraBinding
import dev.ahrsoft.easycameraandgallery.gallery.GalleryAdapter
import dev.ahrsoft.easycameraandgallery.gallery.ImageModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale


class CameraActivity : AppCompatActivity(), GalleryAdapter.OnItemClickListener {

    private lateinit var binding: ActivityCameraBinding
    private lateinit var viewModel: CameraViewModel
    private lateinit var optionsCamera: OptionsCamera
    private lateinit var layoutManager: LinearLayoutManager
    private val imageList = arrayListOf<ImageModel>()
    private lateinit var adapter: GalleryAdapter
    private lateinit var resultGallery: ActivityResultLauncher<Intent>
    private lateinit var utils: Utils

    //camera
    private var cameraProvider: ProcessCameraProvider? = null
    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    private var flashMode: Int = ImageCapture.FLASH_MODE_OFF
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private lateinit var outputDirectory: File

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()
        optionsCamera = if (VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra("options", OptionsCamera::class.java) as OptionsCamera
        }else{
            intent.getSerializableExtra("options") as OptionsCamera
        }
        viewModel = ViewModelProvider(this)[CameraViewModel::class.java]
        initCameraUI()
        initObserver()
        openCallback()
    }

    private fun initCameraUI() {
        if (allPermissionsGranted()){
            binding.cameraVfState.displayedChild = CameraFlipperState.SHOW_CAMERA.state
            initialSettingsCamera()
            startCamera()
            viewModel.getAllPhoto(this)
        }else{
            ActivityCompat.requestPermissions(this, if (VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) REQUIRED_PERMISSIONS_TIRAMISU else REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun initObserver() {
        viewModel.mutableListPhoto.observe(this){
            if (it.isNotEmpty()){
                imageList.clear()
                imageList.addAll(it)
                adapter.getGallery(it)
                isFullSelection()
                adapter.notifyDataSetChanged()
            }
        }
    }

    private fun isFullSelection() {
        val listTemp = imageList.filter {
            it.isSelected
        }
        val isFull = listTemp.size == optionsCamera.count
        with(binding){
            ibFrontCamera.isVisible = !isFull
            cameraCaptureButtonCamera.isVisible = !isFull
            galleryCaptureButtonCamera.isVisible = !isFull
            fabSendData.isVisible = isFull
        }
    }

    private fun initialSettingsCamera() {
        utils = Utils(this)
        flashModeOptions(optionsCamera.flash)
        with(binding) {

            adapter = GalleryAdapter(this@CameraActivity)
            layoutManager = LinearLayoutManager(this@CameraActivity,LinearLayoutManager.HORIZONTAL, false)
            rvGalleryCamera.layoutManager = layoutManager
            rvGalleryCamera.adapter = adapter
            adapter.setOnItemClickListener(this@CameraActivity)

            galleryCaptureButtonCamera.setOnClickListener {
                getPickImageIntent()
            }
            cameraCaptureButtonCamera.setOnClickListener {
                takePhoto()
            }
            ibFrontCamera.setOnClickListener {
                if (CameraSelector.LENS_FACING_FRONT == lensFacing) {
                    lensFacing = CameraSelector.LENS_FACING_BACK
                    enableFrontCamera(false)
                } else {
                    lensFacing = CameraSelector.LENS_FACING_FRONT
                    enableFrontCamera(true)
                }
                bindCameraUseCases()
            }
            ibFlashCamera.setOnClickListener {
                when (flashMode) {
                    ImageCapture.FLASH_MODE_OFF -> {
                        flashMode = ImageCapture.FLASH_MODE_ON
                        caseFlashMode()
                    }
                    ImageCapture.FLASH_MODE_ON -> {
                        flashMode = ImageCapture.FLASH_MODE_AUTO
                        caseFlashMode()
                    }
                    ImageCapture.FLASH_MODE_AUTO -> {
                        flashMode = ImageCapture.FLASH_MODE_OFF
                        caseFlashMode()
                    }
                }
                bindCameraUseCases()
            }

            fabSendData.setOnClickListener {
                getListPath(viewModel.getListPaths(this@CameraActivity) as ArrayList<String>)
            }
        }

    }

    private fun openCallback() {
        resultGallery =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val data = result.data
                    if (data != null) {
                        if (data.clipData != null) {
                            val mClipData = data.clipData
                            for (i in 0 until mClipData!!.itemCount) {
                                val item = mClipData.getItemAt(i)
                                val uri = item.uri
                                viewModel.addImage(uri,null)
                            }
                        } else if (data.data != null) {
                            val uri = data.data
                            if (uri != null) {
                                viewModel.addImage(uri,null)
                            }
                        }
                    }
                }
            }
    }

    private fun caseFlashMode() {
        when (flashMode) {
            ImageCapture.FLASH_MODE_OFF -> {
                binding.ibFlashCamera.setImageResource(R.drawable.ic_flash_off_camera)
            }
            ImageCapture.FLASH_MODE_ON -> {
                binding.ibFlashCamera.setImageResource(R.drawable.ic_flash_on_camera)

            }
            ImageCapture.FLASH_MODE_AUTO -> {
                binding.ibFlashCamera.setImageResource(R.drawable.ic_flash_auto_camera)
            }
        }
    }

    private fun enableFrontCamera(isFront: Boolean) {
        if (isFront) {
            binding.ibFrontCamera.setImageResource(R.drawable.ic_camera_back)
        } else {
            binding.ibFrontCamera.setImageResource(R.drawable.ic_front_camera)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            if (optionsCamera.isFrontFacing) {
                enableFrontCamera(true)
                lensFacing = CameraSelector.LENS_FACING_FRONT
                hasBackCamera()

            } else {
                enableFrontCamera(false)
                lensFacing = CameraSelector.LENS_FACING_BACK
                hasFrontCamera()
            }
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        outputDirectory = getOutputDirectory()
        var photoFile: File? = null
        val outputOptions: ImageCapture.OutputFileOptions =
            if (VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(
                        MediaStore.MediaColumns.DISPLAY_NAME, SimpleDateFormat(
                            FILENAME, Locale.US
                        ).format(System.currentTimeMillis())
                    )
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/$PHOTO_EXTENSION")
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + File.separator + optionsCamera.path
                    )
                }
                ImageCapture.OutputFileOptions.Builder(
                    contentResolver,
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues
                ).build()
            } else {
                photoFile = File(
                    outputDirectory,
                    SimpleDateFormat(
                        FILENAME, Locale.US
                    ).format(System.currentTimeMillis()) + ".$PHOTO_EXTENSION"
                )
                ImageCapture.OutputFileOptions.Builder(photoFile).build()
            }
        imageCapture?.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    if (VERSION.SDK_INT < Build.VERSION_CODES.Q){
                        outputFileResults.savedUri?.let { mediaScanner(it) }
                        viewModel.addImage(outputFileResults.savedUri, photoFile)
                    }else{
                        outputFileResults.savedUri.let { viewModel.addImage(outputFileResults.savedUri, null) }
                    }
                }
                override fun onError(exception: ImageCaptureException) {
                    throw ImageCaptureException(exception.imageCaptureError, exception.message ?: getString(R.string.unknown_error), exception)
                }
            })
    }

    private fun mediaScanner(uri: Uri) {
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(uri.toFile().extension)
        MediaScannerConnection.scanFile(
            this,
            arrayOf(uri.toFile().absolutePath),
            arrayOf(mimeType)
        ) { _, _ -> }
    }

    private fun allPermissionsGranted() : Boolean {
        return if (VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            REQUIRED_PERMISSIONS_TIRAMISU.all {
                ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
            }
        }else {
            REQUIRED_PERMISSIONS.all {
                ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                initCameraUI()
            } else {
               dialogPermission()
            }
        }
    }

    private fun dialogPermission() {
        val builder = AlertDialog.Builder(this).apply {
            setTitle(getString(R.string.action_permission))
            setMessage(getString(R.string.message_permissions))
            setCancelable(false)
        }.setPositiveButton(getString(android.R.string.ok)) { dialog, id ->
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package",baseContext.packageName,null)
            })
        }

        builder.show()
    }

    private fun hasBackCamera(): Boolean {
        return cameraProvider?.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ?: false
    }

    private fun hasFrontCamera(): Boolean {
        return cameraProvider?.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ?: false
    }

    private fun flashModeOptions(flash: Flash) {
        flashMode = when (flash) {
            Flash.On -> {
                ImageCapture.FLASH_MODE_ON
            }
            Flash.Off -> {
                ImageCapture.FLASH_MODE_OFF
            }
            Flash.Auto -> {
                ImageCapture.FLASH_MODE_AUTO
            }
        }
        caseFlashMode()
    }

    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: throw IllegalStateException(getString(R.string.error_start_camera))
        val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        preview = Preview.Builder()
            .setTargetAspectRatio(utils.aspectRadio(optionsCamera.ratio))
            .setTargetRotation(ROTATION_0)
            .build()

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setTargetAspectRatio(utils.aspectRadio(optionsCamera.ratio))
            .setTargetRotation(ROTATION_0)
            .setFlashMode(flashMode)
            .build()
        cameraProvider.unbindAll()
        try {
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            preview?.setSurfaceProvider(binding.viewFinderCamera.surfaceProvider)
        } catch (exc: Exception) {
            throw Exception(exc.message ?: getString(R.string.unknown_error))
        }
    }

    private fun getOutputDirectory(): File {
        val path =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).let {
                File(it, optionsCamera.path).apply { mkdirs() }
            }
        return if (path.exists()) path else filesDir
    }

    private fun getPickImageIntent() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "image/*"
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
        resultGallery.launch(intent)
    }

    private fun getListPath(list: ArrayList<String>) {
        val result = Intent()
        result.putExtra(IMAGE_RESULTS, list)
        setResult(RESULT_OK, result)
        finish()
    }

    override fun onItemClick(position: Int) {
        viewModel.onClickImage(
            position = position,
            isSelected = !imageList[position].isSelected,
            optionsCamera = optionsCamera
        )
    }
}