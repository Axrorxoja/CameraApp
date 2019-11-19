package com.example.cameraapp.fragments

import ANIMATION_FAST_MILLIS
import ANIMATION_SLOW_MILLIS
import AutoFitPreviewBuilder
import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.camera.core.*
import androidx.camera.core.ImageCapture.CaptureMode
import androidx.camera.core.ImageCapture.Metadata
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.example.cameraapp.MainActivity
import com.example.cameraapp.R
import kotlinx.android.synthetic.main.camera_ui_container.*
import kotlinx.android.synthetic.main.fragment_camera.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

/**
 * Main fragment for this app. Implements all camera operations including:
 * - Viewfinder
 * - Photo taking
 */
private const val FILENAME = "yyyy-MM-dd-HH-mm-ss-SSS"
private const val PHOTO_EXTENSION = ".jpg"

class CameraFragment : Fragment(R.layout.fragment_camera) {

    private lateinit var outputDirectory: File
    private val executor by lazy(LazyThreadSafetyMode.NONE) { Executors.newSingleThreadExecutor() }

    private var lensFacing = CameraX.LensFacing.BACK
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null

    override fun onResume() {
        super.onResume()
        // Make sure that all permissions are still present, since user could have removed them
        //  while the app was on paused state
        if (!PermissionsFragment.hasPermissions(requireContext())) {
            Navigation.findNavController(requireActivity(), R.id.fragment_container).navigate(
                CameraFragmentDirections.actionCameraToPermissions()
            )

        }
    }

    /** Define callback that will be triggered after a photo has been taken and saved to disk */
    private val imageSavedListener = object : ImageCapture.OnImageSavedListener {
        override fun onError(
            error: ImageCapture.ImageCaptureError, message: String, exc: Throwable?
        ) {
            exc?.printStackTrace()
        }

        override fun onImageSaved(photoFile: File) {
            setGalleryThumbnail(photoFile)
        }
    }

    @SuppressLint("MissingPermission", "ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Determine the output directory
        outputDirectory = MainActivity.getOutputDirectory(requireContext())

        // Wait for the views to be properly laid out
        view_finder.post {

            // Build UI controls and bind all camera use cases
            updateCameraUi()
            bindCameraUseCases()

        }
//        viewFinder.setOnTouchListener { v, motionEvent ->
//            val point = TextureViewMeteringPointFactory(viewFinder)
//                .createPoint(motionEvent.x, motionEvent.y)
//            val action = FocusMeteringAction.Builder
//                .from(point)
//                .build()
//            CameraX.getCameraControl(CameraX.LensFacing.BACK).startFocusAndMetering(action)
//            false
//        }
    }

    /** Declare and bind preview, capture and analysis use cases */
    private fun bindCameraUseCases() {

        // Set up the view finder use case to display camera preview
        val viewFinderConfig = PreviewConfig.Builder().apply {
            setLensFacing(lensFacing)
            // We request aspect ratio but no resolution to let CameraX optimize our use cases
            setTargetAspectRatio(AspectRatio.RATIO_4_3)
            // Set initial target rotation, we will have to call this again if rotation changes
            // during the lifecycle of this use case
            setTargetRotation(view_finder.display.rotation)
        }.build()

        // Use the auto-fit preview builder to automatically handle size and orientation changes
        preview = AutoFitPreviewBuilder.build(viewFinderConfig, view_finder)

        // Set up the capture use case to allow users to take photos
        val imageCaptureConfig = ImageCaptureConfig.Builder().apply {
            setLensFacing(lensFacing)
            setCaptureMode(CaptureMode.MAX_QUALITY)
            // We request aspect ratio but no resolution to match preview config but letting
            // CameraX optimize for whatever specific resolution best fits requested capture mode
            setTargetAspectRatio(AspectRatio.RATIO_4_3)
            // Set initial target rotation, we will have to call this again if rotation changes
            // during the lifecycle of this use case
            setTargetRotation(view_finder.display.rotation)
        }.build()

        imageCapture = ImageCapture(imageCaptureConfig)

        // Apply declared configs to CameraX using the same lifecycle owner
        CameraX.bindToLifecycle(
            viewLifecycleOwner, preview, imageCapture
        )
    }

    /** Method used to re-draw the camera UI controls, called every time configuration changes */
    @SuppressLint("RestrictedApi")
    private fun updateCameraUi() {

        // Remove previous UI if any
        camera_container.findViewById<ConstraintLayout>(R.id.camera_ui_container)?.let {
            camera_container.removeView(it)
        }

        // Inflate a new view containing all UI for controlling the camera
        val controls =
            View.inflate(requireContext(), R.layout.camera_ui_container, camera_container)

        // Listener for button used to capture photo
        camera_capture_button.setOnClickListener {
            // Get a stable reference of the modifiable image capture use case
            imageCapture?.let { imageCapture ->

                // Create output file to hold the image
                val photoFile = createFile(outputDirectory)

                // Setup image capture metadata
                val metadata = Metadata().apply {
                    // Mirror image when using the front camera
                    isReversedHorizontal = lensFacing == CameraX.LensFacing.FRONT
                }

                // Setup image capture listener which is triggered after photo has been taken
                imageCapture.takePicture(photoFile, metadata, executor, imageSavedListener)

                // We can only change the foreground Drawable using API level 23+ API
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

                    // Display flash animation to indicate that photo was captured
                    camera_container.postDelayed({
                        camera_container.foreground = ColorDrawable(Color.WHITE)
                        camera_container.postDelayed(
                            { camera_container.foreground = null }, ANIMATION_FAST_MILLIS
                        )
                    }, ANIMATION_SLOW_MILLIS)
                }
            }
        }

        // Listener for button used to switch cameras
        camera_switch_button.setOnClickListener {
            lensFacing = if (CameraX.LensFacing.FRONT == lensFacing) {
                CameraX.LensFacing.BACK
            } else {
                CameraX.LensFacing.FRONT
            }
            try {
                // Only bind use cases if we can query a camera with this orientation
                CameraX.getCameraWithLensFacing(lensFacing)

                // Unbind all use cases and bind them again with the new lens facing configuration
                CameraX.unbindAll()
                bindCameraUseCases()
            } catch (exc: Exception) {
                // Do nothing
            }
        }

        // Listener for button used to view last photo
        photo_view_button.setOnClickListener {
            Navigation.findNavController(requireActivity(), R.id.fragment_container).navigate(
                CameraFragmentDirections.actionCameraToGallery(outputDirectory.absolutePath)
            )
        }
    }

    private fun setGalleryThumbnail(file: File) {
        // Run the operations in the view's thread
        photo_view_button.post {

            // Remove thumbnail padding
            photo_view_button.setPadding(resources.getDimension(R.dimen.stroke_small).toInt())

            // Load thumbnail into circular button using Glide
            Glide.with(photo_view_button)
                .load(file)
                .apply(RequestOptions.circleCropTransform())
                .into(photo_view_button)
        }
    }

    /** Helper function used to create a timestamped file */
    private fun createFile(baseFolder: File) =
        File(
            baseFolder, SimpleDateFormat(FILENAME, Locale.US)
                .format(System.currentTimeMillis()) + PHOTO_EXTENSION
        )
}
