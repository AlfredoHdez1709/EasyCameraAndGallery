package dev.ahrsoft.easycameraandgallery.camera

import android.app.Activity
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.ahrsoft.easycameraandgallery.Constant.TAG
import dev.ahrsoft.easycameraandgallery.OptionsCamera
import dev.ahrsoft.easycameraandgallery.Utils
import dev.ahrsoft.easycameraandgallery.gallery.ImageModel
import kotlinx.coroutines.launch
import java.io.File

class CameraViewModel : ViewModel() {

    val mutableListPhoto = MutableLiveData<List<ImageModel>>()
    private val listTempPhoto = arrayListOf<ImageModel>()
    private lateinit var utils: Utils

    fun getAllPhoto(context: Context) = viewModelScope.launch {
        val columns = arrayOf(MediaStore.Images.Media._ID)
        val orderBy = MediaStore.Images.Media.DATE_ADDED
        val cur =  context.contentResolver.query(imageCollection, columns, null, null, "$orderBy DESC")
        cur?.let { cursor ->
            val idColumn = cursor.getColumnIndex(MediaStore.Images.Media._ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val uri =
                    ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                listTempPhoto.add(ImageModel(uri = uri))
            }
        }
        cur?.close()
        updateMutableList()
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

    private fun updateMutableList(){
        mutableListPhoto.postValue(listTempPhoto)
    }

     fun addImage(uri: Uri?, file: File?) {
        val imageModel = ImageModel(uri = uri, isSelected = true, image = file?.path)
        listTempPhoto.add(0, imageModel)
        Log.d(TAG, "onImageSaved: ${imageModel.uri}")
         updateMutableList()
    }

    fun onClickImage(position: Int, isSelected : Boolean, optionsCamera: OptionsCamera) {
        val list = listTempPhoto.filter { it.isSelected }
        if (list.size == optionsCamera.count){
            if (listTempPhoto[position].isSelected){
                listTempPhoto[position].isSelected = false
            }
        }else{
            listTempPhoto[position].isSelected = isSelected
        }
        updateMutableList()
    }

    fun getListPaths(activity: Activity) : List<String>{
        utils = Utils(activity)
        val list = arrayListOf<String>()
        listTempPhoto.map {
            if (it.isSelected){
                if (!it.image.isNullOrEmpty()){
                   it.image?.let { image-> list.add(image) }
                }else{
                    it.uri?.let { path-> list.add(utils.getRealPathFromUri(path)) }
                }
            }
        }
        return list
    }
}