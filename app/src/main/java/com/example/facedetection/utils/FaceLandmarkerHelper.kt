/*
 * Copyright 2023 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.facedetection.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.camera.core.ImageProxy

// mediapipe
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult

// opencv
import org.opencv.android.OpenCVLoader
import org.opencv.calib3d.Calib3d
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.MatOfPoint2f
import org.opencv.core.MatOfPoint3f
import org.opencv.core.Point
import org.opencv.core.Point3

class FaceLandmarkerHelper(
    private val context: Context,
    // The listener is only used when running in RunningMode.LIVE_STREAM
    private var faceLandmarkerListener: LandmarkerListener? = null
) {

    // For this example this needs to be a var so it can be reset on changes. If the faceDetector
    // will not change, a lazy val would be preferable.
    private var faceLandmarker: FaceLandmarker? = null

    private lateinit var face3D: MatOfPoint3f

    private lateinit var face2D: MatOfPoint2f

    private lateinit var cameraMatrix: Mat
    private lateinit var distCoeffs: MatOfDouble

    private lateinit var rVec: Mat
    private lateinit var tVec: Mat

    private var yaw = 0.0
    private var pitch = 0.0

    init {
        setupFaceLandmarker()
        if (!OpenCVLoader.initDebug())
            Log.e("OpenCV", "Unable to load OpenCV!")
        else {
            face3D = MatOfPoint3f()

            face2D = MatOfPoint2f()

            cameraMatrix = Mat(3, 3, CvType.CV_64F)
            distCoeffs = MatOfDouble()

            rVec = Mat()
            tVec = Mat()
            Log.d("OpenCV", "OpenCV loaded Successfully!")
        }
    }

    fun clearFaceLandmarker() {
        faceLandmarker?.close()
        faceLandmarker = null
    }

    // Initialize the face detector using current settings on the
    // thread that is using it. CPU can be used with detectors
    // that are created on the main thread and used on a background thread, but
    // the GPU delegate needs to be used on the thread that initialized the detector
    fun setupFaceLandmarker() {
        if (faceLandmarkerListener == null) {
            throw IllegalStateException(
                "faceDetectorListener must be set when runningMode is LIVE_STREAM."
            )
        }

        val modelName = "face_landmarker.task"
        val threshold: Float = THRESHOLD_DEFAULT
        val baseOptionsBuilder =
            BaseOptions.builder().setModelAssetPath(modelName)

        try {
            val optionsBuilder = FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(baseOptionsBuilder.build()).setMinFaceDetectionConfidence(threshold)
                .setResultListener(this::returnLivestreamResult)
                .setErrorListener(this::returnLivestreamError)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumFaces(1)
            val options = optionsBuilder.build()
            faceLandmarker = FaceLandmarker.createFromOptions(context, options)
        } catch (e: IllegalStateException) {
            faceLandmarkerListener?.onError(
                "Face Landmarker failed to initialize. See error logs for " +
                        "details"
            )
            Log.e(
                TAG, "MediaPipe failed to load the task with error: " + e
                    .message
            )
        } catch (e: RuntimeException) {
            faceLandmarkerListener?.onError(
                "Face Landmarker failed to initialize. See error logs for " +
                        "details", GPU_ERROR
            )
            Log.e(
                TAG,
                "Face Landmarker failed to load model with error: " + e.message
            )
        }
    }

    // Return running status of recognizer helper
    fun isClosed(): Boolean {
        return faceLandmarker == null
    }

    // Runs face detection on live streaming cameras frame-by-frame and returns the results
    // asynchronously to the caller.
    fun detectLivestreamFrame(imageProxy: ImageProxy) {
//        if (runningMode != RunningMode.LIVE_STREAM) {
//            throw IllegalArgumentException(
//                "Attempting to call detectLivestreamFrame" +
//                        " while not using RunningMode.LIVE_STREAM"
//            )
//        }

        val frameTime = SystemClock.uptimeMillis()

        // Copy out RGB bits from the frame to a bitmap buffer
        val bitmapBuffer =
            Bitmap.createBitmap(
                imageProxy.width,
                imageProxy.height,
                Bitmap.Config.ARGB_8888
            )
        imageProxy.use { bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer) }
        imageProxy.close()
        // Rotate the frame received from the camera to be in the same direction as it'll be shown
        val matrix =
            Matrix().apply {
                postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())

                // postScale is used here because we're forcing using the front camera lens
                // This can be set behind a bool if the camera is togglable.
                // Not using postScale here with the front camera causes the horizontal axis
                // to be mirrored.
                postScale(
                    -1f,
                    1f,
                    imageProxy.width.toFloat(),
                    imageProxy.height.toFloat()
                )
            }

        val rotatedBitmap =
            Bitmap.createBitmap(
                bitmapBuffer,
                0,
                0,
                bitmapBuffer.width,
                bitmapBuffer.height,
                matrix,
                true
            )

        // Convert the input Bitmap face to an MPImage face to run inference
        val mpImage = BitmapImageBuilder(rotatedBitmap).build()

        detectAsync(mpImage, frameTime)
    }

    // Run face detection using MediaPipe Face Detector API
    @VisibleForTesting
    fun detectAsync(mpImage: MPImage, frameTime: Long) {
        // As we're using running mode LIVE_STREAM, the detection result will be returned in
        // returnLivestreamResult function
        faceLandmarker?.detectAsync(mpImage, frameTime)
    }

    // Return the detection result to this FaceDetectorHelper's caller
    private fun returnLivestreamResult(
        result: FaceLandmarkerResult,
        input: MPImage
    ) {
        if (result.faceLandmarks().size > 0) {
            val finishTimeMs = SystemClock.uptimeMillis()
            val inferenceTime = finishTimeMs - result.timestampMs()

            faceLandmarkerListener?.let {
                calculateYawPitch(
                    result.faceLandmarks()[0],
                    imageWidth = input.width,
                    imageHeight = input.height
                )
                it.onResults(
                    ResultBundle(
                        result,
                        inferenceTime,
                        input.height,
                        input.width,
                        yaw, pitch
                    )
                )
            }
        } else {
            faceLandmarkerListener?.onEmpty()
        }
    }

    private fun calculateYawPitch(
        faceLandmarks: List<NormalizedLandmark>,
        imageWidth: Int,
        imageHeight: Int
    ) {
        val points3D: MutableList<Point3> = ArrayList()
        val points2D: MutableList<Point> = ArrayList()
        for ((idx, lm) in faceLandmarks.withIndex()) {
            if (idx == 33 || idx == 263 || idx == 1 || idx == 61 || idx == 291 || idx == 199) {
                val x = (lm.x() * imageWidth).toDouble()
                val y = (lm.y() * imageHeight).toDouble()
                points2D.add(Point(x, y))
                points3D.add(Point3(x, y, lm.z().toDouble()))
            }
        }
        face3D.fromList(points3D)
        face2D.fromList(points2D)
        val focalLength = 1 * imageWidth
        cameraMatrix.put(
            0,
            0,
            focalLength.toDouble(),
            0.0,
            imageHeight / 2.0,
            0.0,
            focalLength.toDouble(),
            imageWidth / 2.0,
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
        pitch = euler[0] * 360
        yaw = euler[1] * 360
    }

    // Return errors thrown during detection to this FaceDetectorHelper's caller
    private fun returnLivestreamError(error: RuntimeException) {
        faceLandmarkerListener?.onError(
            error.message ?: "An unknown error has occurred"
        )
    }

    // Wraps results from inference, the time it takes for inference to be performed, and
    // the input image and height for properly scaling UI to return back to callers
    data class ResultBundle(
        val results: FaceLandmarkerResult,
        val inferenceTime: Long,
        val inputImageHeight: Int,
        val inputImageWidth: Int,
        val yaw: Double,
        val pitch: Double,
    )

    companion object {
        //        const val DELEGATE_CPU = 0
//        const val DELEGATE_GPU = 1
        const val THRESHOLD_DEFAULT = 0.5F
        const val OTHER_ERROR = 0
        const val GPU_ERROR = 1

        const val TAG = "FaceLandmarkerHelper.kt"
    }

    // Used to pass results or errors back to the calling class
    interface LandmarkerListener {
        fun onError(error: String, errorCode: Int = OTHER_ERROR)
        fun onResults(resultBundle: ResultBundle)
        fun onEmpty() {}
    }
}