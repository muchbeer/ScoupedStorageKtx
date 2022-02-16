package com.muchbeer.scoupedstoragektx.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintSet
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.muchbeer.scoupedstoragektx.databinding.ItemPhotoBinding
import com.muchbeer.scoupedstoragektx.model.InternalStorage

class InternalStorageAdapt(val onClickPhoto : (InternalStorage)->Unit) : ListAdapter<InternalStorage,
                                InternalStorageAdapt.InternalVM>(diffUtil) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InternalVM {
       val inflater = LayoutInflater.from(parent.context)
      val  view = ItemPhotoBinding.inflate(inflater, parent, false)
        return InternalVM(view)
    }

    override fun onBindViewHolder(holder: InternalVM, position: Int) {

        val photo = getItem(position)
       holder.binding.apply {
           ivPhoto.setImageBitmap(photo.bmp)

           val aspectRatio = photo.bmp.width.toFloat() / photo.bmp.height.toFloat()
           ConstraintSet().apply {
               clone(root)
               setDimensionRatio(ivPhoto.id, aspectRatio.toString())
               applyTo(root)
           }

           ivPhoto.setOnLongClickListener {
               onClickPhoto(photo)
               true
           }
       }
    }

  inner  class InternalVM(val binding: ItemPhotoBinding) : RecyclerView.ViewHolder(binding.root) {

    }

    companion object diffUtil : DiffUtil.ItemCallback<InternalStorage>() {
        override fun areItemsTheSame(oldItem: InternalStorage, newItem: InternalStorage): Boolean {
           return oldItem.name == newItem.name
        }

        override fun areContentsTheSame(
            oldItem: InternalStorage,
            newItem: InternalStorage
        ): Boolean {
           return oldItem == newItem
        }

    }

}