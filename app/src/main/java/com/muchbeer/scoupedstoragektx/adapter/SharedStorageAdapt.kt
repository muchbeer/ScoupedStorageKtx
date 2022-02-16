package com.muchbeer.scoupedstoragektx.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintSet
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.muchbeer.scoupedstoragektx.databinding.ItemPhotoBinding
import com.muchbeer.scoupedstoragektx.model.SharedStorage

class SharedStorageAdapt(val onPhotoClick : (SharedStorage)->Unit) : ListAdapter<SharedStorage,
                        SharedStorageAdapt.SharedStorageVH>(diffUtil)    {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SharedStorageVH {
       val inflater = LayoutInflater.from(parent.context)
        val view = ItemPhotoBinding.inflate(inflater, parent, false)
        return SharedStorageVH(view)
    }

    override fun onBindViewHolder(holder: SharedStorageVH, position: Int) {
       val photo = getItem(position)
        holder.binding.apply {
            ivPhoto.setImageURI(photo.contentUri)

            val aspectRatio = photo.width.toFloat() / photo.height.toFloat()
            ConstraintSet().apply {
                clone(root)
                setDimensionRatio(ivPhoto.id, aspectRatio.toString())
                applyTo(root)
            }

            ivPhoto.setOnLongClickListener {
                onPhotoClick(photo)
                true
            }
        }
    }

    companion object diffUtil : DiffUtil.ItemCallback<SharedStorage>() {
        override fun areItemsTheSame(oldItem: SharedStorage, newItem: SharedStorage): Boolean {
               return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: SharedStorage, newItem: SharedStorage): Boolean {
           return oldItem == newItem
        }

    }
    inner class SharedStorageVH(val binding : ItemPhotoBinding) :
            RecyclerView.ViewHolder(binding.root){

    }

}