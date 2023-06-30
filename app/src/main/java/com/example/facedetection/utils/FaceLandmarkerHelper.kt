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
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult

class FaceLandmarkerHelper(
    private val context: Context,
    // The listener is only used when running in RunningMode.LIVE_STREAM
    private var faceLandmarkerListener: LandmarkerListener? = null
) {

    // For this example this needs to be a var so it can be reset on changes. If the faceDetector
    // will not change, a lazy val would be preferable.
    private var faceLandmarker: FaceLandmarker? = null

    init {
        setupFaceLandmarker()
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

            faceLandmarkerListener?.onResults(
                ResultBundle(
                    result,
                    inferenceTime,
                    input.height,
                    input.width
                )
            )
        } else {
            faceLandmarkerListener?.onEmpty()
        }
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