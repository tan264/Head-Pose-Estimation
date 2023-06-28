package com.example.facedetection.viewmodels

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import org.opencv.android.OpenCVLoader
import org.opencv.calib3d.Calib3d
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.MatOfPoint2f
import org.opencv.core.MatOfPoint3f
import org.opencv.core.Point
import org.opencv.core.Point3

class CameraViewModel : ViewModel() {
    private val _message = MutableLiveData<String>()
    val message: LiveData<String> = _message

    private val _yaw = MutableLiveData<Double>()
    val yaw: LiveData<Double> = _yaw

    private val _pitch = MutableLiveData<Double>()
    val pitch: LiveData<Double> = _pitch

    private val _faceAngle = MutableLiveData<String>()
    val faceAngle: LiveData<String> = _faceAngle

    private var _hasFrontAngle = MutableLiveData(false)
    val hasFrontAngle: LiveData<Boolean> = _hasFrontAngle

    private var _hasLeftAngle = MutableLiveData(false)
    val hasLeftAngle: LiveData<Boolean> = _hasLeftAngle

    private var _hasRightAngle = MutableLiveData(false)
    val hasRightAngle: LiveData<Boolean> = _hasRightAngle

    private var _hasUpAngle = MutableLiveData(false)
    val hasUpAngle: LiveData<Boolean> = _hasUpAngle

    private var _hasDownAngle = MutableLiveData(false)
    val hasDownAngle: LiveData<Boolean> = _hasDownAngle

    private var _isDone = MutableLiveData(false)
    val isDone: LiveData<Boolean> = _isDone

    private lateinit var face3D: MatOfPoint3f

    private lateinit var face2D: MatOfPoint2f

    private lateinit var cameraMatrix: Mat
    private lateinit var distCoeffs: MatOfDouble

    private lateinit var rVec: Mat
    private lateinit var tVec: Mat

    init {
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

    fun setMessage(message: String) {
        _message.postValue(message)
    }

    private fun setPitch(pitch: Double) {
        _pitch.postValue(pitch)
    }

    private fun setYaw(yaw: Double) {
        _yaw.postValue(yaw)
    }

    private fun setFaceAngle(faceAngle: String) {
        _faceAngle.postValue(faceAngle)
    }

    // A complicated function using OpenCV
    fun calculateYawPitch(
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
        val pitch = euler[0] * 360
        val yaw = euler[1] * 360
        setFaceAngle(
            if (yaw < -15) {
                _hasLeftAngle.postValue(true)
                "Left"
            } else if (pitch > 15) {
                _hasUpAngle.postValue(true)
                "Up"
            } else if (pitch < -15) {
                _hasDownAngle.postValue(true)
                "Down"
            } else if (yaw > 15) {
                _hasRightAngle.postValue(true)
                "Right"
            } else {
                _hasFrontAngle.postValue(true)
                "Front"
            }
        )
        setPitch(pitch)
        setYaw(yaw)
        if(hasDownAngle.value!! && hasFrontAngle.value!! && hasUpAngle.value!! && hasRightAngle.value!! && hasLeftAngle.value!!) {
            _isDone.postValue(true)
        }
    }
}