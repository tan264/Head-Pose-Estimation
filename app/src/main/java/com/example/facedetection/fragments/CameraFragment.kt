package com.example.facedetection.fragments

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.facedetection.utils.FaceLandmarkerHelper
import com.example.facedetection.R
import com.example.facedetection.databinding.FragmentCameraBinding
import com.example.facedetection.viewmodels.CameraViewModel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CameraFragment : Fragment(), FaceLandmarkerHelper.LandmarkerListener {
    private val viewModel: CameraViewModel by viewModels()

    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!

    private lateinit var cameraExecutor: ExecutorService

    private var imageAnalyzer: ImageAnalysis? = null

    private lateinit var faceLandmarkerHelper: FaceLandmarkerHelper

    private lateinit var vib: Vibrator

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        viewModel.setMessage(getString(R.string.waiting_camera))
        binding.apply {
            lifecycleOwner = this@CameraFragment
            binding.viewModel = this@CameraFragment.viewModel
        }
        vib = if (Build.VERSION.SDK_INT >= 31) {
            val vibratorManager =
                activity?.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            activity?.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        cameraExecutor = Executors.newSingleThreadExecutor()
        cameraExecutor.execute {
            faceLandmarkerHelper = FaceLandmarkerHelper(requireContext(), this)
            startCamera()
        }
        viewModel.apply {
            isDone.observe(viewLifecycleOwner) {
                if (it) {
                    findNavController().navigate(CameraFragmentDirections.actionCameraFragmentToCompleteFragment())
                }
            }
            hasFrontAngle.observe(
                viewLifecycleOwner
            ) { if (it) vibrate(vib) }
            hasRightAngle.observe(
                viewLifecycleOwner
            ) { if (it) vibrate(vib) }
            hasLeftAngle.observe(
                viewLifecycleOwner
            ) { if (it) vibrate(vib) }
            hasUpAngle.observe(
                viewLifecycleOwner
            ) { if (it) vibrate(vib) }
            hasDownAngle.observe(
                viewLifecycleOwner
            ) { if (it) vibrate(vib) }
        }
    }

    override fun onResume() {
        super.onResume()
        cameraExecutor.execute {
            if (faceLandmarkerHelper.isClosed()) {
                faceLandmarkerHelper.setupFaceLandmarker()
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }

            // Select back camera as a default
            val cameraSelector =
                CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                    .build()

            imageAnalyzer =
                ImageAnalysis.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                    .setTargetRotation(binding.viewFinder.display.rotation)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    // The analyzer can then be assigned to the instance
                    .also {
                        it.setAnalyzer(
                            cameraExecutor,
                            faceLandmarkerHelper::detectLivestreamFrame
                        )
                    }

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer
                )

            } catch (exc: Exception) {
                Log.e("DEBUG", "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(requireContext()))
    }

    override fun onPause() {
        super.onPause()

        if (this::faceLandmarkerHelper.isInitialized) {
            cameraExecutor.execute { faceLandmarkerHelper.clearFaceLandmarker() }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null

        cameraExecutor.shutdown()
        cameraExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)
    }

    override fun onError(error: String, errorCode: Int) {
        Log.e("tan264", error)
    }

    override fun onResults(resultBundle: FaceLandmarkerHelper.ResultBundle) {
        _binding?.let {
            if (viewModel.isInsideTheBox(
                    resultBundle.results.faceLandmarks()[0],
                    resultBundle.inputImageWidth,
                    resultBundle.inputImageHeight,
                    it.ovalView.getOvalRect(),
                    it.ovalView.width,
                    it.ovalView.height
                )
            ) {
                if (!viewModel.hasFrontAngle.value!!) {
                    viewModel.setMessage(getString(R.string.look_straight))
                } else if (!viewModel.hasRightAngle.value!!) {
                    viewModel.setMessage(getString(R.string.look_right))
                } else if (!viewModel.hasLeftAngle.value!!) {
                    viewModel.setMessage(getString(R.string.look_left))
                } else if (!viewModel.hasUpAngle.value!!) {
                    viewModel.setMessage(getString(R.string.look_up))
                } else if (!viewModel.hasDownAngle.value!!) {
                    viewModel.setMessage(getString(R.string.look_down))
                }
                viewModel.calculateYawPitch(
                    resultBundle.results.faceLandmarks()[0],
                    resultBundle.inputImageWidth,
                    resultBundle.inputImageHeight
                )
            } else {
                viewModel.setMessage(getString(R.string.no_face))
                viewModel.resetStatus()
            }
        }

    }

    override fun onEmpty() {
        super.onEmpty()
        viewModel.setMessage(getString(R.string.no_face))
        viewModel.resetStatus()
    }

    private fun vibrate(vib: Vibrator) {
        val duration = 5L
        if (Build.VERSION.SDK_INT >= 26) {
            vib.vibrate(
                VibrationEffect.createOneShot(
                    duration, if (Build.VERSION.SDK_INT >= 29) {
                        VibrationEffect.EFFECT_TICK
                    } else {
                        VibrationEffect.DEFAULT_AMPLITUDE
                    }
                )
            )
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(duration)
        }
    }

}