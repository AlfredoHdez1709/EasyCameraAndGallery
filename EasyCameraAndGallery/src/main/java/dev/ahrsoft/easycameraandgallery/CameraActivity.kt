package dev.ahrsoft.easycameraandgallery

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.database.Cursor
import android.graphics.Rect
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.util.Log
import android.view.Surface.ROTATION_0
import android.view.View
import android.view.WindowInsets
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toFile
import androidx.recyclerview.widget.LinearLayoutManager
import dev.ahrsoft.easycameraandgallery.Constant.FILENAME
import dev.ahrsoft.easycameraandgallery.Constant.PHOTO_EXTENSION
import dev.ahrsoft.easycameraandgallery.Constant.RATIO_16_9_VALUE
import dev.ahrsoft.easycameraandgallery.Constant.RATIO_4_3_VALUE
import dev.ahrsoft.easycameraandgallery.Constant.REQUEST_CODE_PERMISSIONS
import dev.ahrsoft.easycameraandgallery.Constant.REQUIRED_PERMISSIONS
import dev.ahrsoft.easycameraandgallery.Constant.TAG
import dev.ahrsoft.easycameraandgallery.EasyCamera.IMAGE_RESULTS
import dev.ahrsoft.easycameraandgallery.databinding.ActivityCameraBinding
import dev.ahrsoft.easycameraandgallery.gallery.GalleryAdapter
import dev.ahrsoft.easycameraandgallery.gallery.ImageModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min


class CameraActivity : AppCompatActivity() {

    private var cameraProvider: ProcessCameraProvider? = null
    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    private var flashMode: Int = ImageCapture.FLASH_MODE_OFF
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private lateinit var outputDirectory: File
    private lateinit var binding: ActivityCameraBinding
    private val listSelectImages = ArrayList<String>()
    private lateinit var optionsCamera: OptionsCamera
    private val imageList = arrayListOf<ImageModel>()
    private val imageSelected = arrayListOf<ImageModel>()
    private lateinit var adapter: GalleryAdapter
    private lateinit var resultGallery: ActivityResultLauncher<Intent>


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        binding = ActivityCameraBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        initUI()
        if (allPermissionsGranted()) {
            startCamera()
            getAllImageFromGallery()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }

    private fun initUI() {
        optionsCamera  = (intent.getSerializableExtra("options") as? OptionsCamera)!!
        flashModeOptions(optionsCamera.flash)

        with(binding) {
            galleryCaptureButtonCamera.setOnClickListener {
                getPickImageIntent()
            }
            cameraCaptureButtonCamera.setOnClickListener {
                takePhoto()
            }
            ibFrontCamera.setOnClickListener {
                if (CameraSelector.LENS_FACING_FRONT == lensFacing){
                    lensFacing =  CameraSelector.LENS_FACING_BACK
                    enableFrontCamera(false)
                }else{
                    lensFacing = CameraSelector.LENS_FACING_FRONT
                    enableFrontCamera(true)
                }
                bindCameraUseCases()
            }
            ibFlashCamera.setOnClickListener {
                when(flashMode){
                    ImageCapture.FLASH_MODE_OFF ->{
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
                val list = imageSelected.map {
                    it.image
                }
                getListPath(list as ArrayList<String>)
            }
        }
        openCallback()
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
                                getPathFromURI(uri)
                            }
                        } else if (data.data != null) {
                            val uri = data.data
                            if (uri != null) {
                               getPathFromURI(uri)
                            }
                        }
                    }
                }
            }
    }

    private fun caseFlashMode(){
        when(flashMode){
            ImageCapture.FLASH_MODE_OFF ->{
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

    private fun enableFrontCamera(isFront : Boolean) {
        if (isFront){
            binding.ibFrontCamera.setImageResource(R.drawable.ic_camera_back)
        }else{
            binding.ibFrontCamera.setImageResource(R.drawable.ic_front_camera)
        }
    }


    private fun getPathFromURI(uri: Uri) {
        var realPath = String()
        uri.path?.let { path ->
            val databaseUri: Uri
            val selection: String?
            val selectionArgs: Array<String>?
            if (path.contains("/document/image:")) {
                databaseUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                selection = "_id=?"
                selectionArgs = arrayOf(DocumentsContract.getDocumentId(uri).split(":")[1])
            } else {
                databaseUri = uri
                selection = null
                selectionArgs = null
            }
            try {
                val column = "_data"
                val projection = arrayOf(column)
                val cursor = contentResolver.query(
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
        addImage(realPath)
    }


    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
             if (optionsCamera.isFrontFacing){
                 enableFrontCamera(true)
                 lensFacing = CameraSelector.LENS_FACING_FRONT
                 hasBackCamera()

            }else{
                 enableFrontCamera(false)
                 lensFacing = CameraSelector.LENS_FACING_BACK
                 hasFrontCamera()
             }
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun hideUI(isCompleteSelect : Boolean){
        if (isCompleteSelect){
            binding.fabSendData.visibility = View.VISIBLE
            binding.cameraCaptureButtonCamera.visibility = View.GONE
            binding.galleryCaptureButtonCamera.visibility = View.GONE
        }else{
            binding.fabSendData.visibility = View.GONE
            binding.cameraCaptureButtonCamera.visibility = View.VISIBLE
            binding.galleryCaptureButtonCamera.visibility = View.VISIBLE
        }
    }

    private val imageCollection: Uri by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(
                MediaStore.VOLUME_EXTERNAL
            )
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
    }

    private fun getAllImageFromGallery(){
        imageList.clear()
        val columns = arrayOf(MediaStore.Images.Media._ID)
        val orderBy = MediaStore.Images.Media.DATE_ADDED

        val cursor : Cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val bundle = Bundle()
            bundle.putInt(
                ContentResolver.QUERY_ARG_SORT_DIRECTION,
                ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
            )
            if (optionsCamera.galleryCount > 0)
                bundle.putInt(ContentResolver.QUERY_ARG_LIMIT,optionsCamera.galleryCount)

            contentResolver.query(imageCollection,columns,bundle,null)!!
        }else{
            val filter = if (optionsCamera.galleryCount > 0) {
                "$orderBy DESC LIMIT ${optionsCamera.galleryCount}"
            }else{
                orderBy
            }
            contentResolver.query(imageCollection,columns,null,null, filter)!!
        }

        cursor.let { cursor ->
            val idColumn = cursor.getColumnIndex(MediaStore.Images.Media._ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                val imageModel = ImageModel(image = getRealPathFromUri(uri))
                getRealPathFromUri(uri)?.let { imageList.add(imageModel) }
            }
        }
        cursor.close()
        print(imageList)
        setImageList()
    }

    private fun setImageList(){
        adapter = GalleryAdapter(this,imageList)
        binding.rvGalleryCamera.layoutManager = LinearLayoutManager(this,LinearLayoutManager.HORIZONTAL, false)
        binding.rvGalleryCamera.adapter = adapter
        adapter.setOnItemClickListener(object : GalleryAdapter.OnItemClickListener{
            override fun onItemClick(position: Int) {
                val imageModel = imageList[position]
                if (imageModel.isSelected){
                    unSelectImage(imageModel, position)
                }else{
                    selectImage(imageModel, position)
                }
            }
        })
    }

    private fun selectImage(imageModel: ImageModel, position: Int) {
        if (isCompletedSelect()){
            hideUI(true)
        }else{
            imageSelected.add(imageModel)
            imageList[position].isSelected = true
            adapter.notifyItemChanged(position)
            if (isCompletedSelect()) hideUI(true)
        }
    }

    private fun isCompletedSelect() : Boolean {
       return imageSelected.size >= optionsCamera.count
    }

    private fun unSelectImage(imageModel: ImageModel, position: Int) {
        imageSelected.remove(imageModel)
        imageList[position].isSelected = false
        adapter.notifyItemChanged(position)
        hideUI(false)
    }

    private fun takePhoto() {
        outputDirectory = getOutputDirectory()
        var photoFile : File? = null
        val outputOptions: ImageCapture.OutputFileOptions =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, SimpleDateFormat(
                        FILENAME, Locale.US
                    ).format(System.currentTimeMillis()))
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/$PHOTO_EXTENSION")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + File.separator + optionsCamera.path)
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
                @SuppressLint("NotifyDataSetChanged")
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {

                    val path: String? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        getRealPathFromUri(outputFileResults.savedUri!!)
                    } else {
                        outputFileResults.savedUri?.let { mediaScanner(it) }
                        photoFile?.path.toString()
                    }
                    if (path != null) {
                        listSelectImages.add(path)
                    }
                    if (path != null) {
                        addImage(path)
                    }
                }
                override fun onError(exception: ImageCaptureException) {
                    Log.d(TAG, "onError: ${exception.message}")
                }
            })
    }

    private fun addImage(path : String){
        if (isCompletedSelect()){
            hideUI(true)
        }else{
            val imageModel = ImageModel(image = path, isSelected = true)
            imageList.add(0,imageModel)
            imageSelected.add(imageModel)
            adapter.notifyDataSetChanged()
            Log.d(TAG, "onImageSaved: $path")
            if (isCompletedSelect()) hideUI(true)
        }
    }

    private fun mediaScanner(uri: Uri) {
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(uri.toFile().extension)
        MediaScannerConnection.scanFile(
            this,
            arrayOf(uri.toFile().absolutePath),
            arrayOf(mimeType)
        ) {_,uri ->
            Log.d(TAG, "Imagen capturada fue guardada: $uri")
        }
    }

    @Deprecated("delete function")
    fun getRealPathFromUri(contentUri: Uri): String? {

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
                val cursor = contentResolver.query(
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



    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
                getAllImageFromGallery()
            } else {
                Toast.makeText(
                    this,
                    "Debe proporcionar permisos para accesar al dispositivo.",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    private fun hasBackCamera(): Boolean {
        return cameraProvider?.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ?: false
    }

    private fun hasFrontCamera(): Boolean {
        return cameraProvider?.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ?: false
    }

   private fun rationAuto(width: Int, height: Int): Int {
       Log.d(TAG, "rationAuto: $width - $height")
        val previewRatio = max(width, height).toDouble() / min(width, height)
        if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }

    private fun aspectRadio(ratio: Ratio): Int {
        return when(ratio){
            Ratio.RATIO_4_3 -> AspectRatio.RATIO_4_3
            Ratio.RATIO_16_9 -> AspectRatio.RATIO_16_9
            Ratio.RATIO_AUTO -> rationAuto(getScreenWidth(),getScreenHeight())
        }
    }

    private fun flashModeOptions(flash: Flash) {
        flashMode = when(flash){
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


    private fun getScreenWidth(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = windowManager.currentWindowMetrics
            val bounds: Rect = windowMetrics.bounds
            val insets: android.graphics.Insets = windowMetrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars()
            )
            if (resources.configuration.orientation
                == Configuration.ORIENTATION_LANDSCAPE
                && resources.configuration.smallestScreenWidthDp < 600
            ) { // landscape and phone
                val navigationBarSize = insets.right + insets.left
                bounds.width() - navigationBarSize
            } else { // portrait or tablet
                bounds.width()
            }
        } else {
            val outMetrics = DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(outMetrics)
            outMetrics.widthPixels
        }
    }

    fun getScreenHeight(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = windowManager.currentWindowMetrics
            val bounds: Rect = windowMetrics.bounds
            val insets: android.graphics.Insets = windowMetrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars()
            )
            if (resources.configuration.orientation
                == Configuration.ORIENTATION_LANDSCAPE
                && resources.configuration.smallestScreenWidthDp < 600
            ) { // landscape and phone
                bounds.height()
            } else { // portrait or tablet
                val navigationBarSize = insets.bottom
                bounds.height() - navigationBarSize
            }
        } else {
            val outMetrics = DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(outMetrics)
            outMetrics.heightPixels
        }
    }


    private fun bindCameraUseCases() {
                  val cameraProvider = cameraProvider
                      ?: throw IllegalStateException("Error al iniciar la camara.")
                  val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                  preview = Preview.Builder()
                      .setTargetAspectRatio(aspectRadio(optionsCamera.ratio))
                      .setTargetRotation(ROTATION_0)
                      .build()

                  imageCapture = ImageCapture.Builder()
                      .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                      .setTargetAspectRatio(aspectRadio(optionsCamera.ratio))
                      .setTargetRotation(ROTATION_0)
                      .setFlashMode(flashMode)
                      .build()
                  cameraProvider.unbindAll()

                  try {
                      cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
                      preview?.setSurfaceProvider(binding.viewFinderCamera.surfaceProvider)
                  } catch (exc: Exception) {
                      throw Exception("Use case fallo", exc)
                  }
    }

    private fun getOutputDirectory(): File {
        val path =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).let {
                File(it, optionsCamera.path).apply { mkdirs() }
            }
        return if (path.exists()) path else filesDir
    }

    fun getPickImageIntent() {
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
}