package dev.ahrsoft.easycameraandgallery.gallery

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import dev.ahrsoft.easycameraandgallery.databinding.ImagePickerListCameraBinding

class GalleryAdapter(private val context : Context) : RecyclerView.Adapter<GalleryAdapter.GalleryHolder>() {

    private var imageList: List<ImageModel>? = null
    fun getGallery(imageList: List<ImageModel>){
        this.imageList = imageList
        notifyDataSetChanged()
    }

    class GalleryHolder(private val binding: ImagePickerListCameraBinding) : RecyclerView.ViewHolder(binding.root){
        fun bind(context: Context,imageModel: ImageModel){
            with(binding){
                Glide.with(context)
                    .load(imageModel.uri)
                    .into(imagePickerCamera)
                checkBoxPickerCamera.isChecked = imageModel.isSelected
            }
        }
    }

    override fun getItemCount(): Int = imageList?.size ?: 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GalleryHolder {
        val itembinding = ImagePickerListCameraBinding.inflate(LayoutInflater.from(parent.context),parent,false)
        return GalleryHolder(itembinding)
    }

    override fun onBindViewHolder(holder: GalleryHolder, position: Int) {
        val imageModel = imageList?.get(position)
        if (imageModel != null) {
            holder.bind(context, imageModel)
        }
        holder.itemView.setOnClickListener {
            onItemClickListener?.onItemClick(position)
        }
    }

    fun setOnItemClickListener(onItemClickListener: OnItemClickListener){
        Companion.onItemClickListener = onItemClickListener
    }

    interface OnItemClickListener{
        fun onItemClick(position: Int)
    }

    companion object{
        private var onItemClickListener : OnItemClickListener? = null
    }
}