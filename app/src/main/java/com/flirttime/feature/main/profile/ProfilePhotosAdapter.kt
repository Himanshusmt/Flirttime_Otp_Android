package com.flirttime.feature.main.profile

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.flirttime.R
import com.flirttime.core.common.FastImageHelper
import com.flirttime.databinding.ItemProfilePhotoBinding

data class ProfilePhotoItem(
    val id: String,
    val url: String? = null,
    val resId: Int? = null
)

class ProfilePhotosAdapter(
    private val onPhotoClick: ((ProfilePhotoItem) -> Unit)? = null
) : ListAdapter<ProfilePhotoItem, ProfilePhotosAdapter.ProfilePhotoViewHolder>(PhotoDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProfilePhotoViewHolder {
        val binding = ItemProfilePhotoBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ProfilePhotoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ProfilePhotoViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ProfilePhotoViewHolder(
        private val binding: ItemProfilePhotoBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(photo: ProfilePhotoItem) {
            FastImageHelper.loadDirect(
                draweeView = binding.ivUserPhoto,
                url = photo.url,
                fallbackRes = photo.resId?.takeIf { it != 0 } ?: R.drawable.img_login
            )

            binding.root.setOnClickListener {
                onPhotoClick?.invoke(photo)
            }
        }
    }

    class PhotoDiffCallback : DiffUtil.ItemCallback<ProfilePhotoItem>() {
        override fun areItemsTheSame(oldItem: ProfilePhotoItem, newItem: ProfilePhotoItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ProfilePhotoItem, newItem: ProfilePhotoItem): Boolean {
            return oldItem == newItem
        }
    }
}
