package com.mshurpo.testview.gpu

import android.annotation.TargetApi
import android.app.ActivityManager
import android.content.Context
import android.graphics.PixelFormat
import android.hardware.Camera
import android.opengl.GLSurfaceView

/**
 * Created by maksimsurpo on 5/21/17.
 */
class GPUImage(context: Context) {

    private var filter: GPUImageFilter = GPUImageFilter()
    private val renderer: GPUImageRenderer = GPUImageRenderer(filter)
    private lateinit var glSurfaceView: GLSurfaceView

    init {
        if (supportsOpenGLES2(context).not()) throw IllegalStateException("OpenGL ES 2.0 is not supported on this phone.")
    }

    /**
     * Checks if OpenGL ES 2.0 is supported on the current device.

     * @param context the context
     * *
     * @return true, if successful
     */
    private fun supportsOpenGLES2(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val configurationInfo = activityManager.deviceConfigurationInfo
        return configurationInfo.reqGlEsVersion >= 0x20000
    }

    /**
     * Sets the GLSurfaceView which will display the preview.

     * @param view the GLSurfaceView
     */
    fun setGLSurfaceView(view: GLSurfaceView) {
        glSurfaceView = view
        glSurfaceView.setEGLContextClientVersion(2)
        glSurfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        glSurfaceView.getHolder().setFormat(PixelFormat.RGBA_8888)
        glSurfaceView.setRenderer(renderer)
        glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY)
        glSurfaceView.requestRender()
    }


    /**
     * Request the preview to be rendered again.
     */
    fun requestRender() {
        if (glSurfaceView != null) {
            glSurfaceView.requestRender()
        }
    }

    /**
     * Sets the up camera to be connected to GPUImage to get a filtered preview.

     * @param camera the camera
     * *
     * @param degrees by how many degrees the image should be rotated
     * *
     * @param flipHorizontal if the image should be flipped horizontally
     * *
     * @param flipVertical if the image should be flipped vertically
     */
    fun setUpCamera(camera: Camera, degrees: Int, flipHorizontal: Boolean,
                    flipVertical: Boolean) {
        glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY)
        setUpCameraGingerbread(camera)
        var rotation = Rotation.NORMAL
        when (degrees) {
            90 -> rotation = Rotation.ROTATION_90
            180 -> rotation = Rotation.ROTATION_180
            270 -> rotation = Rotation.ROTATION_270
        }
        renderer.setRotationCamera(rotation, flipHorizontal, flipVertical)
    }

    @TargetApi(11)
    private fun setUpCameraGingerbread(camera: Camera) {
        renderer.setUpSurfaceTexture(camera)
    }

    /**
     * Sets the filter which should be applied to the image which was (or will
     * be) set by setImage(...).

     * @param filter the new filter
     */
    fun setFilter(filter: GPUImageFilter) {
        this.filter = filter
        renderer.setFilter(this.filter)
        requestRender()
    }


}