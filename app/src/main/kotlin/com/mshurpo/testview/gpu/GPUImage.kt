package com.mshurpo.testview.gpu

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

    private fun supportsOpenGLES2(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val configurationInfo = activityManager.deviceConfigurationInfo
        return configurationInfo.reqGlEsVersion >= 0x20000
    }

    fun setGLSurfaceView(view: GLSurfaceView) {
        glSurfaceView = view
        glSurfaceView.setEGLContextClientVersion(2)
        glSurfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        glSurfaceView.getHolder().setFormat(PixelFormat.RGBA_8888)
        glSurfaceView.setRenderer(renderer)
        glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY)
        glSurfaceView.requestRender()
    }

    fun requestRender() {
        glSurfaceView.requestRender()
    }

    fun setUpCamera(camera: Camera, degrees: Int, flipHorizontal: Boolean,
                    flipVertical: Boolean) {
        glSurfaceView.queueEvent {
            glSurfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            renderer.setUpSurfaceTexture(camera)
            var rotation = Rotation.NORMAL
            when (degrees) {
                90 -> rotation = Rotation.ROTATION_90
                180 -> rotation = Rotation.ROTATION_180
                270 -> rotation = Rotation.ROTATION_270
            }
            renderer.setRotationCamera(rotation, flipHorizontal, flipVertical)
        }
    }

    fun setFilter(filter: GPUImageFilter) {
        this.filter = filter
        renderer.onFilter(this.filter)
        requestRender()
    }

}