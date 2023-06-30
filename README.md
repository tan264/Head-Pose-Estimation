# Head Pose Estimation using MediaPipe and OpenCV in Android(Kotlin)

## Overview

This project focuses on developing an application that allows users to position
their faces within an oval region on a live camera preview.
The application
utilizes [MediaPipe Face Landmarker](https://developers.google.com/mediapipe/solutions/vision/face_landmarker){:target="_blank"}
to detect the presence of a human face and extract facial landmarks.
[OpenCV](https://docs.opencv.org/4.x/javadoc/org/opencv/calib3d/Calib3d.html#solvePnP(org.opencv.core.MatOfPoint3f,org.opencv.core.MatOfPoint2f,org.opencv.core.Mat,org.opencv.core.MatOfDouble,org.opencv.core.Mat,org.opencv.core.Mat)){:target="_blank"}
is then used to calculate the yaw and pitch angles,
enabling the determination of the directness and orientation of the face
(i.e., facing straight, right, left, up, or down).
<br/>**Note: To integrate OpenCV into the project, I use
this [repo](https://github.com/QuickBirdEng/opencv-android){:target="_blank"}**

## Demo
<img src="/demo/1351190849151434476-_online-video-cutter.com_.gif" width="360" height="740" />