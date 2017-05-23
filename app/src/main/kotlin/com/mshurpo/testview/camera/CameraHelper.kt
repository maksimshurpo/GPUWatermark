package com.mshurpo.testview.camera

import android.app.Activity
import android.hardware.Camera
import android.view.Surface

/**
 * Created by maksimsurpo on 5/19/17.
 */
class CameraHelper {
    fun getNumberOfCameras(): Int = Camera.getNumberOfCameras()

    fun openCamera(id: Int): Camera = Camera.open(id)

    fun hasFrontCamera(): Boolean = hasCamera(Camera.CameraInfo.CAMERA_FACING_FRONT)

    fun hasBackCamera(): Boolean = hasCamera(Camera.CameraInfo.CAMERA_FACING_BACK)

    private fun hasCamera(cameraFacingFront: Int): Boolean = getCameraId(cameraFacingFront) != -1

    fun getCameraInfo(cameraId: Int, cameraInfo: CameraInfo2) {
        val info = Camera.CameraInfo()
        Camera.getCameraInfo(cameraId, info)
        cameraInfo.facing = info.facing
        cameraInfo.orientation = info.orientation
    }

    private fun getCameraId(facing: Int): Int {
        val numberOfCameras = Camera.getNumberOfCameras()
        val info = Camera.CameraInfo()
        for (id in 0..numberOfCameras - 1) {
            Camera.getCameraInfo(id, info)
            if (info.facing == facing) {
                return id
            }
        }
        return -1
    }

    fun getCameraDisplayOrientation(activity: Activity, cameraId: Int): Int {
        val rotation = activity.windowManager.defaultDisplay
                .rotation
        var degrees = 0
        when (rotation) {
            Surface.ROTATION_0 -> degrees = 0
            Surface.ROTATION_90 -> degrees = 90
            Surface.ROTATION_180 -> degrees = 180
            Surface.ROTATION_270 -> degrees = 270
        }

        val result: Int
        val info = CameraInfo2
        getCameraInfo(cameraId, info)
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360
        } else { // back-facing
            result = (info.orientation - degrees + 360) % 360
        }
        return result
    }

}