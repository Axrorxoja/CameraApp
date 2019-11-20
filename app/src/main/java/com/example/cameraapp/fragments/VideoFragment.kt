package com.example.cameraapp.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.cameraapp.R
import kotlinx.android.synthetic.main.video_item.*
import java.io.File


/** Fragment used for each individual page showing a video inside of [GalleryFragment] */
class VideoFragment internal constructor() : Fragment(R.layout.video_item) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val args = arguments ?: return
        val path = args.getString(FILE_NAME_KEY)
        val resource = path?.let { File(it) } ?: R.drawable.ic_photo
        Glide.with(view).load(resource).into(video_poster)
        video_icon.setOnClickListener {
            if (path != null) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(path))
                intent.setDataAndType(Uri.parse(path), "video/mp4")
                startActivity(intent)
            }
        }
    }

    companion object {
        private const val FILE_NAME_KEY = "file_name"

        fun create(image: File) = VideoFragment().apply {
            arguments = Bundle().apply {
                putString(FILE_NAME_KEY, image.absolutePath)
            }
        }
    }
}