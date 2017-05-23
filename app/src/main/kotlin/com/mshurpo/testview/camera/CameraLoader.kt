package com.mshurpo.testview.camera

import android.app.Activity
import android.hardware.Camera
import com.mshurpo.testview.gpu.GPUImage

/**
 * Created by maksimsurpo on 5/19/17.
 */
class CameraLoader(val activity: Activity, val cameraHelper: CameraHelper, val gpuImage: GPUImage) {
    private var currentCameraId = 0
    var cameraInstance: Camera? = null

    fun onResume() {
        setUpCamera(currentCameraId)
    }

    fun onPause() {
        releaseCamera()
    }

    fun switchCamera() {
        releaseCamera()
        currentCameraId = (currentCameraId + 1) % cameraHelper.getNumberOfCameras()
        setUpCamera(currentCameraId)
    }

    private fun setUpCamera(id: Int) {
        cameraInstance = getCameraInstance(id)
        cameraInstance?.let {
            val parameters = it.parameters
            /*if (parameters.supportedFocusModes
                    .contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                parameters.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
            }*/

            it.parameters = parameters

            val orientation = cameraHelper.getCameraDisplayOrientation(activity, currentCameraId)
            val cameraInfo = CameraInfo2
            cameraHelper.getCameraInfo(currentCameraId, cameraInfo)
            val flipHorizontal = cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT
            cameraInstance?.let {
                gpuImage.setUpCamera(it, orientation, flipHorizontal, false)
            }

        }

    }

    private fun getCameraInstance(id: Int): Camera? {
        var camera: Camera? = null
        try {
            camera = cameraHelper.openCamera(id)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return camera
    }

    private fun releaseCamera() {
        cameraInstance?.setPreviewCallback(null)
        cameraInstance?.release()
        cameraInstance = null
    }
}