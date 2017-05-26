package com.mshurpo.testview.gpu

import android.opengl.EGL14
import com.mshurpo.testview.gpu.encode.*
import java.io.IOException
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLContext
import javax.microedition.khronos.egl.EGLDisplay
import javax.microedition.khronos.egl.EGLSurface

/**
 * Created by maksimsurpo on 5/25/17.
 */
class GPUImageMovieWriter : GPUImageFilter() {

    private lateinit var muxer: MediaMuxerWrapper
    private lateinit var videoEncoder: MediaVideoEncoder
    private lateinit var audioEncoder: MediaAudioEncoder
    private var codecInput: WindowSurface? = null

    private lateinit var eglScreenSurface: EGLSurface
    private lateinit var egl: EGL10
    private lateinit var eglDisplay: EGLDisplay
    private lateinit var eglContext: EGLContext
    private var eglCore: EglCore? = null

    private var isRecording = false

    private val mediaEncoderListener = object : MediaEncoder.MediaEncoderListener {

        override fun onPrepared(encoder: MediaEncoder) {}

        override fun onStopped(encoder: MediaEncoder) {}

        override fun onMuxerStopped() {}
    }

    override fun onInit() {
        super.onInit()
        egl = EGLContext.getEGL() as EGL10
        eglDisplay = egl.eglGetCurrentDisplay()
        eglContext = egl.eglGetCurrentContext()
        eglScreenSurface = egl.eglGetCurrentSurface(EGL10.EGL_DRAW)
    }

    override fun onDraw(textureId: Int, cubeBuffer: FloatBuffer, textureBuffer: FloatBuffer) {
        super.onDraw(textureId, cubeBuffer, textureBuffer)
        if (isRecording) {
            if (codecInput == null) {
                eglCore = EglCore(EGL14.eglGetCurrentContext(), EglCore.FLAG_RECORDABLE)
                codecInput = WindowSurface(eglCore!!, videoEncoder.surface, false)
            }

            // Draw on encoder surface
            codecInput?.makeCurrent()
            super.onDraw(textureId, cubeBuffer, textureBuffer)
            codecInput?.swapBuffers()
            videoEncoder.frameAvailableSoon()
        }
        egl.eglMakeCurrent(eglDisplay, eglScreenSurface, eglScreenSurface, eglContext)
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseEncodeSurface()
    }

    fun startRecording(outputPath: String, width: Int, height: Int) {
        runOnDraw(Runnable {
            if (isRecording) {
                return@Runnable
            }

            try {
                muxer = MediaMuxerWrapper(outputPath)

                // for video capturing
                videoEncoder = MediaVideoEncoder(muxer, mediaEncoderListener, width, height)
                // for audio capturing
                audioEncoder = MediaAudioEncoder(muxer, mediaEncoderListener)

                muxer.prepare()
                muxer.startRecording()

                isRecording = true
            } catch (e: IOException) {
                e.printStackTrace()
            }
        })
    }

    fun stopRecording() {
        runOnDraw(Runnable {
            if (isRecording.not()) {
                return@Runnable
            }

            muxer.stopRecording()
            isRecording = false
            releaseEncodeSurface()
        })
    }

    private fun releaseEncodeSurface() {
        if (eglCore != null) {
            eglCore?.makeNothingCurrent()
            eglCore?.release()
            eglCore = null
        }

        if (codecInput != null) {
            codecInput?.release()
            codecInput = null
        }
    }
}