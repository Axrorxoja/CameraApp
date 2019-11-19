package com.example.cameraapp.fragments

import AutoFitPreviewBuilder
import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.camera.core.*
import androidx.camera.view.TextureViewMeteringPointFactory
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.example.cameraapp.MainActivity
import com.example.cameraapp.R
import kotlinx.android.synthetic.main.fragment_video_capture_fragment.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

/**
 * Main fragment for this app. Implements all camera operations including:
 * - Viewfinder
 * - Video taking
 */
private const val FILENAME = "yyyy-MM-dd-HH-mm-ss-SSS"
private const val VIDEO_EXTENSION = ".mp4"

class VideoCaptureFragment : Fragment(R.layout.fragment_video_capture_fragment) {

    private lateinit var outputDirectory: File
    private val executor by lazy(LazyThreadSafetyMode.NONE) { Executors.newSingleThreadExecutor() }

    private var lensFacing = CameraX.LensFacing.BACK
    private var preview: Preview? = null
    private var videoCapture: VideoCapture? = null
    private var isRecording = false
    private val handler: Handler by lazy(LazyThreadSafetyMode.NONE) { Handler() }
    private val navController by lazy(LazyThreadSafetyMode.NONE) {
        Navigation.findNavController(
            requireActivity(),
            R.id.fragment_container
        )
    }

    override fun onResume() {
        super.onResume()
        // Make sure that all permissions are still present, since user could have removed them
        //  while the app was on paused state
        if (!PermissionsFragment.hasPermissions(requireContext())) {
//            Navigation.findNavController(requireActivity(), R.id.fragment_container).navigate(
//                VideoCa.actionCameraToPermissions()
//            )

        }
    }

    private val videoCaptureLister = object : VideoCapture.OnVideoSavedListener {
        override fun onError(
            videoCaptureError: VideoCapture.VideoCaptureError,
            message: String,
            cause: Throwable?
        ) {
        }

        override fun onVideoSaved(file: File) {

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
            camera_capture_button.performClick()

        }

        initOnBackHanlder()
        setUpTapToFocus()
    }

    private fun setUpTapToFocus() {
        view_finder.setOnTouchListener { _, motionEvent ->
            val point = TextureViewMeteringPointFactory(view_finder)
                .createPoint(motionEvent.x, motionEvent.y)
            val action = FocusMeteringAction.Builder
                .from(point)
                .build()
            CameraX.getCameraControl(CameraX.LensFacing.BACK).startFocusAndMetering(action)
            false
        }
    }

    private fun initOnBackHanlder() {
        requireActivity()
            .onBackPressedDispatcher
            .addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    videoCapture?.apply {
                        if (isRecording)
                            stopRecording()
                    }
                    handler.postDelayed({
                        navController.popBackStack()
                    }, 1000)
                }

            })
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

        setUpVideoCapture()

        // Apply declared configs to CameraX using the same lifecycle owner
        CameraX.bindToLifecycle(
            viewLifecycleOwner, preview, videoCapture
        )
    }

    private fun setUpVideoCapture() {
        val videoCaptureConfig = VideoCaptureConfig.Builder().apply {
            setLensFacing(lensFacing)
            setTargetAspectRatio(AspectRatio.RATIO_4_3)
            setTargetRotation(view_finder.display.rotation)

        }.build()

        videoCapture = VideoCapture(videoCaptureConfig)
    }

    /** Method used to re-draw the camera UI controls, called every time configuration changes */
    @SuppressLint("RestrictedApi")
    private fun updateCameraUi() {

        // Listener for button used to capture photo
        camera_capture_button.setOnLongClickListener {
            videoCapture?.apply {
                stopRecording()
                isRecording = false
            }
            true
        }
        camera_capture_button.setOnClickListener {
            // Get a stable reference of the modifiable image capture use case
            videoCapture?.apply {

                // Create output file to hold the image
                val videoFile = createFile(outputDirectory)

                val metadata = VideoCapture.Metadata()
                    .apply {
                    }

                // Setup image capture listener which is triggered after photo has been taken
                startRecording(videoFile, metadata, executor, videoCaptureLister)
                isRecording = true
            }
        }

        // Listener for button used to view last photo
        photo_view_button.setOnClickListener {
            //            Navigation.findNavController(requireActivity(), R.id.fragment_container).navigate(
//                CameraFragmentDirections.actionCameraToGallery(outputDirectory.absolutePath)
//            )
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
                .format(System.currentTimeMillis()) + VIDEO_EXTENSION
        )
}
