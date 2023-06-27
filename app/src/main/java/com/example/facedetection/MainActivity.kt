package com.example.facedetection

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.example.facedetection.databinding.ActivityMainBinding
import org.opencv.android.OpenCVLoader
import org.opencv.calib3d.Calib3d
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.MatOfPoint2f
import org.opencv.core.MatOfPoint3f
import org.opencv.core.Point
import org.opencv.core.Point3
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), FaceLandmarkerHelper.LandmarkerListener {
    private lateinit var viewBinding: ActivityMainBinding

    private lateinit var cameraExecutor: ExecutorService

    private var imageAnalyzer: ImageAnalysis? = null

    private lateinit var faceLandmarkerHelper: FaceLandmarkerHelper

    private lateinit var face3D: MatOfPoint3f

    private lateinit var face2D: MatOfPoint2f

    private lateinit var cameraMatrix: Mat
    private lateinit var distCoeffs: MatOfDouble

    private lateinit var rVec: Mat
    private lateinit var tVec: Mat
    private val activityResultLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        )
        { permissions ->
            // Handle Permission granted/rejected
            var permissionGranted = true
            permissions.entries.forEach {
                if (it.key in REQUIRED_PERMISSIONS && !it.value)
                    permissionGranted = false
            }
            if (!permissionGranted) {
                Toast.makeText(
                    baseContext,
                    "Permission request denied",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                startCamera()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()


        // Request camera permissions
        if (allPermissionsGranted()) {
            cameraExecutor.execute {
                faceLandmarkerHelper = FaceLandmarkerHelper(this, this)
                startCamera()
            }
        } else {
            requestPermissions()
        }


        // Set up the listeners for take photo and video capture buttons
//        viewBinding.imageCaptureButton.setOnClickListener { takePhoto() }
//        viewBinding.videoCaptureButton.setOnClickListener { captureVideo() }

    }

    override fun onResume() {
        super.onResume()
        cameraExecutor.execute {
            if (faceLandmarkerHelper.isClosed()) {
                faceLandmarkerHelper.setupFaceLandmarker()
            }
        }
        if (!OpenCVLoader.initDebug())
            Log.e("OpenCV", "Unable to load OpenCV!")
        else {
            face3D = MatOfPoint3f()

            face2D = MatOfPoint2f()

            cameraMatrix = Mat(3,3,CvType.CV_64F)
            distCoeffs = MatOfDouble()

            rVec = Mat()
            tVec = Mat()
            Log.d("OpenCV", "OpenCV loaded Successfully!")
        }

    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
                }

            // Select back camera as a default
            val cameraSelector =
                CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_FRONT).build()

            imageAnalyzer =
                ImageAnalysis.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                    .setTargetRotation(viewBinding.viewFinder.display.rotation)
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
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    override fun onPause() {
        super.onPause()
        if (this::faceLandmarkerHelper.isInitialized) {
            // Close the face detector and release resources
            cameraExecutor.execute { faceLandmarkerHelper.clearFaceLandmarker() }
        }
    }

    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()

    }

    companion object {
        private const val TAG = "DEBUG"
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                Manifest.permission.CAMERA,
            ).toTypedArray()
    }

    override fun onError(error: String, errorCode: Int) {
        Log.d("tan264", error)
    }

    override fun onResults(resultBundle: FaceLandmarkerHelper.ResultBundle) {
        Log.d("tan264", "co")
        this.runOnUiThread {
            viewBinding.textView.text = getString(R.string.face_detected)
        }
        val w = resultBundle.inputImageWidth
        val h = resultBundle.inputImageHeight

        val faceLandmarkerResult = resultBundle.results
        val faceLandmarks = faceLandmarkerResult.faceLandmarks()[0]

        val point3D: MutableList<Point3> = ArrayList()
        val point2D: MutableList<Point> = ArrayList()
        for ((idx, lm) in faceLandmarks.withIndex()) {
            if (idx == 33 || idx == 263 || idx == 1 || idx == 61 || idx == 291 || idx == 199) {
                val x = (lm.x() * w).toDouble()
                val y = (lm.y() * h).toDouble()
                point2D.add(Point(x, y))
                point3D.add(Point3(x, y, lm.z().toDouble()))
            }
        }
        face3D.fromList(point3D)
        face2D.fromList(point2D)
        val focalLength = 1 * w
//        val matrixData = doubleArrayOf(focalLength.toDouble(), 0.0, w / 2.0, 0.0, focalLength.toDouble(), h / 2.0, 0.0, 0.0, 1.0)
        cameraMatrix.put(
            0,
            0,
            focalLength.toDouble(),
            0.0,
            h / 2.0,
            0.0,
            focalLength.toDouble(),
            w / 2.0,
            0.0,
            0.0,
            1.0
        )
        distCoeffs = MatOfDouble(Mat.zeros(4, 1, CvType.CV_64F))

        try {
            Calib3d.solvePnP(
                face3D,
                face2D,
                cameraMatrix,
                distCoeffs,
                rVec,
                tVec
            )
        } catch (e: Exception) {
            Log.e("tan264", e.message.toString())
        }

        val rotationMatrix = Mat(3, 3, CvType.CV_64FC1)
        rotationMatrix.put(0, 0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        Calib3d.Rodrigues(rVec, rotationMatrix)
        val euler = Calib3d.RQDecomp3x3(rotationMatrix, Mat(), Mat())
        val pitch = euler[0] * 360
        val yaw = euler[1] * 360
        this.runOnUiThread {
            viewBinding.textViewYaw.text = String.format("%.2f",yaw)
            viewBinding.textViewPitch.text = String.format("%.2f",pitch)
        }
//        Log.d("vpitch", "${euler[0] * 360}")
//        Log.d("vyaw", "${euler[1] * 360}")

    }

    override fun onEmpty() {
        Log.d("tan264", "khong co")
        this.runOnUiThread {
            viewBinding.textView.text = getString(R.string.no_face)
        }
    }

}