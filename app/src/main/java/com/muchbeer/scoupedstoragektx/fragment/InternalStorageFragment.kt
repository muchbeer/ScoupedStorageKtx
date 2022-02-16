package com.muchbeer.scoupedstoragektx.fragment

import android.Manifest
import android.app.Activity.RESULT_OK
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context.MODE_PRIVATE
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.muchbeer.scoupedstoragektx.adapter.InternalStorageAdapt
import com.muchbeer.scoupedstoragektx.adapter.SharedStorageAdapt
import com.muchbeer.scoupedstoragektx.databinding.FragmentFirstBinding
import com.muchbeer.scoupedstoragektx.model.InternalStorage
import com.muchbeer.scoupedstoragektx.model.SharedStorage
import com.muchbeer.scoupedstoragektx.util.sdk29AndUp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.*

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class InternalStorageFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null
    private lateinit var internalStorageAdapter : InternalStorageAdapt
    private lateinit var externalStorageAdapter : SharedStorageAdapt

    private var readPermissionGranted = false
    private var writePermissionGranted = false
    private lateinit var permissionLauncher : ActivityResultLauncher<Array<String>>

    private lateinit var contentObserver: ContentObserver

    private lateinit var intentSenderLauncher: ActivityResultLauncher<IntentSenderRequest>
    //This is only applicable for API 29 so unique and stupid
    private var deletedImageUri: Uri? = null


    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        _binding = FragmentFirstBinding.inflate(inflater, container, false)

        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        internalStorageAdapter = InternalStorageAdapt {
            lifecycleScope.launch {
                val isDeleted = deletePhotoFromInternalStorage(it.name)

                if (isDeleted) {
                    loadPhotosFromInternalStorageIntoRecy()
                    Toast.makeText(requireContext(), "Deleted successful", Toast.LENGTH_LONG).show()
                } else Toast.makeText(requireContext(), "DeleteFailed Saved", Toast.LENGTH_LONG).show()
            }
        }

        externalStorageAdapter = SharedStorageAdapt {
            lifecycleScope.launch {
                deleteFromExternalStorage(it.contentUri)
                deletedImageUri = it.contentUri
            }
        }

        setupExternalStorageRecyclerVW()
        initContentObserver()

        permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions->
            readPermissionGranted = permissions[Manifest.permission.READ_EXTERNAL_STORAGE] ?: readPermissionGranted
            writePermissionGranted = permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE] ?: writePermissionGranted

            if(readPermissionGranted) {
                loadPhotosFromExternalStorageIntoRecy()
            } else {
                Toast.makeText(requireContext(), "Can't read files without permission", Toast.LENGTH_LONG).show()
            }
        }

        updateOrRequestPermission()

        intentSenderLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            if(it.resultCode == RESULT_OK) {
                if(Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                    lifecycleScope.launch {
                        deleteFromExternalStorage(deletedImageUri ?: return@launch)
                    }
                }
                Toast.makeText(requireContext(), "Photo deleted successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Photo couldn't be deleted", Toast.LENGTH_SHORT).show()
            }
        }


        val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) {

            lifecycleScope.launch {
                val isPrivateChecked = binding.switchPrivate.isChecked
                val isSuccessfulSaved = when {
                    isPrivateChecked -> {
                        saveToInternalStorage(UUID.randomUUID().toString(), it)
                    }
                    writePermissionGranted -> savePhotoToExternalStorage(
                        UUID.randomUUID().toString(),
                        it
                    )

                    else -> false
                }

                if (isPrivateChecked) {
                    loadPhotosFromInternalStorageIntoRecy()
                }

                if (isSuccessfulSaved) {
                    loadPhotosFromInternalStorageIntoRecy()
                    Toast.makeText(requireContext(), "Saved successful", Toast.LENGTH_LONG).show()
                } else Toast.makeText(requireContext(), "Failed Saved", Toast.LENGTH_LONG).show()

            }
        }

        binding.btnTakePhoto.setOnClickListener {
            takePhoto.launch(null)
        }
        setupInternalStorageRecyclerVW()
        loadPhotosFromInternalStorageIntoRecy()
        loadPhotosFromExternalStorageIntoRecy()
/*        binding.buttonFirst.setOnClickListener {
            findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment)
        }*/
    }

    private suspend fun saveToInternalStorage(fileName : String, bmp: Bitmap) : Boolean {
          return withContext(Dispatchers.IO) {
              try {
                  requireContext().openFileOutput("$fileName.jpg", MODE_PRIVATE).use { stream ->
                      if(!bmp.compress(Bitmap.CompressFormat.JPEG, 95, stream)) {
                          throw IOException("Couldn't save bitmap")
                      }
                  }
                  true
              } catch(e : IOException) {
                  e.printStackTrace()
                  false
              }
          }
    }
    private fun setupInternalStorageRecyclerVW() = binding.rvPrivatePhotos.apply {
        adapter = internalStorageAdapter
        layoutManager = StaggeredGridLayoutManager(3, RecyclerView.VERTICAL)
    }

    private fun setupExternalStorageRecyclerVW() = binding.rvPublicPhotos.apply {
        adapter = externalStorageAdapter
        layoutManager = StaggeredGridLayoutManager(3, RecyclerView.VERTICAL)
    }

    private fun loadPhotosFromInternalStorageIntoRecy() {
        lifecycleScope.launch {
            val photos = loadPhotosFromInternalStorage()
            internalStorageAdapter.submitList(photos)
        }
    }

    private fun loadPhotosFromExternalStorageIntoRecy() {
        lifecycleScope.launch {
            val photos = loadPhotoFromExternal()
            externalStorageAdapter.submitList(photos)
        }
    }

    private suspend fun loadPhotosFromInternalStorage() : List<InternalStorage> {
        return withContext(Dispatchers.IO) {
            val files = requireContext().filesDir.listFiles()

            files?.filter { it.canRead() && it.isFile && it.name.endsWith(".jpg")}?.map{
                val bytes = it.readBytes()
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                InternalStorage(it.name, bmp)
            } ?: listOf()
        }
    }

    private suspend fun deletePhotoFromInternalStorage(filename : String) : Boolean {
        return withContext(Dispatchers.IO) {
            try {
                requireContext().deleteFile(filename)
            } catch (e : Exception) {
                e.printStackTrace()
                false
            }
        }
    }


    private fun initContentObserver() {
        contentObserver = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                if(readPermissionGranted) {
                    loadPhotosFromExternalStorageIntoRecy()
                }
            }
        }
        requireContext().contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            contentObserver
        )
    }

    private suspend fun loadPhotoFromExternal() : List<SharedStorage>{

        return withContext(Dispatchers.IO) {
            val collection = sdk29AndUp {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } ?: MediaStore.Images.Media.EXTERNAL_CONTENT_URI

            val projection = arrayOf(MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.WIDTH,
                MediaStore.Images.Media.HEIGHT)

            val photosExternal = mutableListOf<SharedStorage>()

            requireContext().contentResolver.query(
                collection,
                projection,
                null,
                null,
                "${MediaStore.Images.Media.DISPLAY_NAME} ASC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val displayName = cursor.getString(displayNameColumn)
                    val width = cursor.getInt(widthColumn)
                    val height = cursor.getInt(heightColumn)

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                    )
                    photosExternal.add(SharedStorage(id, displayName, width, height, contentUri))
                }
                photosExternal.toList()
            } ?: listOf()
        }
    }

    private fun updateOrRequestPermission() {
        val hasReadPermission = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED

        val hasWritePermission = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED

        val minSdk29 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

        readPermissionGranted = hasReadPermission
        writePermissionGranted = hasWritePermission || minSdk29

        val permissionsToRequest = mutableListOf<String>()
        if(!writePermissionGranted) {
            permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if(!readPermissionGranted) {
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if(permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    private suspend fun savePhotoToExternalStorage(displayName : String, bmp : Bitmap) : Boolean {
        return withContext(Dispatchers.IO) {
             val photoExternalUri = sdk29AndUp {
                 MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
             } ?: MediaStore.Images.Media.EXTERNAL_CONTENT_URI

             val contentValues = ContentValues().apply {
                 put(MediaStore.Images.Media.DISPLAY_NAME, "$displayName.jpg")
                 put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                 put(MediaStore.Images.Media.WIDTH, "${bmp.width}")
                 put(MediaStore.Images.Media.HEIGHT, "${bmp.height}")
             }

              try {
                 requireContext().contentResolver.insert(photoExternalUri, contentValues)?.also { uri->
                     requireContext().contentResolver.openOutputStream(uri).use { outputStream->
                         if(!bmp.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)) {
                             throw IOException("Couldn't save bitmap")
                         }
                     }
                 } ?: throw IOException("Couldn't create MediaStore Entry")
                 true
             } catch (e: IOException) {
                 e.printStackTrace()
                 false
             }
         }
    }

    /*
        Please note delete from api 29 and above request you to have permission
     */
    private suspend fun deleteFromExternalStorage(photoUri : Uri) {
        withContext(Dispatchers.IO) {
            try {

                //This will succeed with api 29 and above if the photo exist to this app folder
                    //If it is the photo out of our folder then it will not work
               requireContext().contentResolver.delete(photoUri, null, null)
            } catch (e: SecurityException)
            {
                val intentSender = when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                        MediaStore.createDeleteRequest(requireContext().contentResolver, listOf(photoUri)).intentSender
                    }
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                        val recoverableSecurityException = e as? RecoverableSecurityException
                        recoverableSecurityException?.userAction?.actionIntent?.intentSender
                    }
                    else -> null
                }
                intentSender?.let { sender ->
                    intentSenderLauncher.launch(
                        IntentSenderRequest.Builder(sender).build()
                    )
                }
            }

        }
    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        requireContext().contentResolver.unregisterContentObserver(contentObserver)

    }
}