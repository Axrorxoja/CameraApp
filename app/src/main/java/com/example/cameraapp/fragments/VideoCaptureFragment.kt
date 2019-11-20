package com.example.cameraapp.fragments

import AutoFitPreviewBuilder
import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.camera.core.*
import androidx.camera.view.TextureViewMeteringPointFactory
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
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
private const val DEF_TIME_OUT = 1000L

@SuppressLint("RestrictedApi")
class VideoCaptureFragment : Fragment(R.layout.fragment_video_capture_fragment) {

    private lateinit var outputDirectory: File
    private val executor by lazy(LazyThreadSafetyMode.NONE) { Executors.newSingleThreadExecutor() }

    private var lensFacing = CameraX.LensFacing.BACK
    private var preview: Preview? = null
    private var videoCapture: VideoCapture? = null
    private var isRecording = false
    private val handler = Handler()
    private val navController by lazy(LazyThreadSafetyMode.NONE) {
        Navigation.findNavController(
            requireActivity(),
            R.id.fragment_container
        )
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
            setUpCameraUI()
            bindCameraUseCases()
            startRecordVideo()
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
                    popBackStackWithTimeOut()
                }

            })
    }

    private fun popBackStackWithTimeOut() {
        handler.postDelayed({
            navController.popBackStack()
        }, DEF_TIME_OUT)
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

    @SuppressLint("RestrictedApi")
    private fun setUpCameraUI() {

        camera_stop_button.setOnClickListener {
            videoCapture?.apply {
                stopRecording()
                isRecording = false
                popBackStackWithTimeOut()
            }
        }

        image_view_button.setOnClickListener { navController.popBackStack() }

        camera_pause_button.setOnClickListener { }
    }

    private fun startRecordVideo() {
        videoCapture?.apply {

            // Create output file to hold the image
            val videoFile = createFile(outputDirectory)

            // Setup image capture listener which is triggered after photo has been taken
            startRecording(videoFile, executor, videoCaptureLister)
            isRecording = true
        }
    }

    /** Helper function used to create a timestamped file */
    private fun createFile(baseFolder: File) =
        File(
            baseFolder, SimpleDateFormat(FILENAME, Locale.US)
                .format(System.currentTimeMillis()) + VIDEO_EXTENSION
        )
}
