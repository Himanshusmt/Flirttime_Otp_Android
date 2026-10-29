package com.flirttime.feature.profile.otherUser

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.flirttime.R
import com.flirttime.databinding.ItemOtherUserPhotoBinding

class OtherUserPhotosAdapter(
    private var photos: List<String> = emptyList(),
    private val onPhotoClick: ((String, Int) -> Unit)? = null
) : RecyclerView.Adapter<OtherUserPhotosAdapter.PhotoViewHolder>() {

    fun submitList(newPhotos: List<String>) {
        photos = newPhotos
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val binding = ItemOtherUserPhotoBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PhotoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        holder.bind(photos[position], position)
    }

    override fun getItemCount(): Int = photos.size

    inner class PhotoViewHolder(private val binding: ItemOtherUserPhotoBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(url: String, position: Int) {
            if (url.isNotBlank()) {
                binding.ivUserPhoto.setImageURI(url)
            } else {
                binding.ivUserPhoto.setActualImageResource(R.drawable.img_login)
            }
            binding.root.setOnClickListener {
                onPhotoClick?.invoke(url, position)
            }
        }
    }
}
