package com.example.facedetection.viewmodels

import android.graphics.RectF
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.max

class CameraViewModel : ViewModel() {
    private val _message = MutableLiveData<String>()
    val message: LiveData<String> = _message

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

    fun setMessage(message: String) {
        _message.postValue(message)
    }

    fun setFaceAngle(
        yaw: Double,
        pitch: Double
    ) {
        if (yaw < -15 && _hasRightAngle.value!! && !_hasLeftAngle.value!!) {
            _hasLeftAngle.postValue(true)
        } else if (pitch > 15 && _hasLeftAngle.value!! && !_hasUpAngle.value!!) {
            _hasUpAngle.postValue(true)
        } else if (pitch < -10 && _hasUpAngle.value!! && !_hasDownAngle.value!!) {
            _hasDownAngle.postValue(true)
        } else if (yaw > 15 && _hasFrontAngle.value!! && !_hasRightAngle.value!!) {
            _hasRightAngle.postValue(true)
        } else if (yaw in -8.0..8.0 && pitch in -8.0..8.0 && !_hasFrontAngle.value!!) {
            _hasFrontAngle.postValue(true)
        }
        if (hasDownAngle.value!! && hasFrontAngle.value!! && hasUpAngle.value!! && hasRightAngle.value!! && hasLeftAngle.value!!) {
            _isDone.postValue(true)
        }
    }

    fun isInsideTheBox(
        faceLandmarks: List<NormalizedLandmark>,
        imageWidth: Int,
        imageHeight: Int,
        box: RectF,
        viewWidth: Int,
        viewHeight: Int
    ): Boolean {
        val scaleFactor = max(viewWidth * 1f / imageWidth, viewHeight * 1f / imageHeight)

        return box.contains(
            faceLandmarks[234].x() * imageWidth * scaleFactor,
            faceLandmarks[10].y() * imageHeight * scaleFactor,
            faceLandmarks[454].x() * imageWidth * scaleFactor,
            faceLandmarks[200].y() * imageHeight * scaleFactor
        )
    }

    fun resetStatus() {
        _hasFrontAngle.postValue(false)
        _hasRightAngle.postValue(false)
        _hasLeftAngle.postValue(false)
        _hasUpAngle.postValue(false)
        _hasDownAngle.postValue(false)
    }
}