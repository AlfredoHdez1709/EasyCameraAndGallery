package dev.ahrsoft.easycameraandgallery.gallery

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import dev.ahrsoft.easycameraandgallery.databinding.ImagePickerListBinding

class GalleryAdapter(private val context : Context, private val imageList : List<ImageModel>) : RecyclerView.Adapter<GalleryAdapter.GalleryHolder>() {

    class GalleryHolder(private val binding: ImagePickerListBinding) : RecyclerView.ViewHolder(binding.root){
        fun bind(context: Context,imageModel: ImageModel){
            with(binding){
                Glide.with(context)
                    .load(imageModel.image)
                    .into(image)
                checkBox.isChecked = imageModel.isSelected
            }
        }
    }

    override fun getItemCount(): Int = imageList.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GalleryHolder {
        val itembinding = ImagePickerListBinding.inflate(LayoutInflater.from(parent.context),parent,false)
        return GalleryHolder(itembinding)
    }

    override fun onBindViewHolder(holder: GalleryHolder, position: Int) {
        val imageModel = imageList[position]
        holder.bind(context, imageModel)
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