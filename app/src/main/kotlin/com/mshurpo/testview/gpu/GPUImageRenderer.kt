package com.mshurpo.testview.gpu

import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import com.mshurpo.testview.gpu.utils.TextureRotationUtil
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.util.*
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Created by maksimsurpo on 5/21/17.
 */
open class GPUImageRenderer(filter: GPUImageFilter) : GLSurfaceView.Renderer, Camera.PreviewCallback {
    companion object {
        val NO_IMAGE = -1
        internal val CUBE = floatArrayOf(-1.0f, -1.0f, 1.0f, -1.0f, -1.0f, 1.0f, 1.0f, 1.0f)
    }

    private var mFilter: GPUImageFilter

    val mSurfaceChangedWaiter = Object()

    private var mGLTextureId = NO_IMAGE
    private var mSurfaceTexture: SurfaceTexture? = null
    private val mGLCubeBuffer: FloatBuffer
    private val mGLTextureBuffer: FloatBuffer
    private var mGLRgbBuffer: IntBuffer? = null

    private var mOutputWidth: Int = 0
    private var mOutputHeight: Int = 0
    private var mImageWidth: Int = 0
    private var mImageHeight: Int = 0
    private val mAddedPadding: Int = 0

    private val mRunOnDraw: Queue<Runnable>
    private var mRotation: Rotation? = null
    private var mFlipHorizontal: Boolean = false
    private var mFlipVertical: Boolean = false
    private val mScaleType = ScaleType.CENTER_CROP

    private val mBackgroundRed = 0f
    private val mBackgroundGreen = 0f
    private val mBackgroundBlue = 0f

    init {
        mFilter = filter
        mRunOnDraw = LinkedList<Runnable>()

        mGLCubeBuffer = ByteBuffer.allocateDirect(CUBE.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
        mGLCubeBuffer.put(CUBE).position(0)

        mGLTextureBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.TEXTURE_NO_ROTATION.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
        setRotation(Rotation.NORMAL, false, false)
    }

    override fun onSurfaceCreated(unused: GL10, config: EGLConfig) {
        GLES20.glClearColor(mBackgroundRed, mBackgroundGreen, mBackgroundBlue, 1f)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        mFilter.init()
    }

    override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
        mOutputWidth = width
        mOutputHeight = height
        GLES20.glViewport(0, 0, width, height)
        GLES20.glUseProgram(mFilter.getProgram())
        mFilter.onOutputSizeChanged(width, height)
        adjustImageScaling()
        synchronized(mSurfaceChangedWaiter) {
            mSurfaceChangedWaiter.notifyAll()
        }
    }

    override fun onDrawFrame(gl: GL10) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        runAll(mRunOnDraw)
        mFilter.onDraw(mGLTextureId, mGLCubeBuffer, mGLTextureBuffer)
        //runAll(mRunOnDrawEnd);
        mSurfaceTexture?.let {
            it.updateTexImage()
        }
    }

    private fun runAll(queue: Queue<Runnable>) {
        synchronized(queue) {
            while (!queue.isEmpty()) {
                queue.poll().run()
            }
        }
    }

    override fun onPreviewFrame(data: ByteArray, camera: Camera) {
        val previewSize = camera.parameters.previewSize
        if (mGLRgbBuffer == null) {
            mGLRgbBuffer = IntBuffer.allocate(previewSize.width * previewSize.height)
        }
        if (mRunOnDraw.isEmpty()) {
            runOnDraw(Runnable {
                GPUImageNativeLibrary.YUVtoRBGA(data, previewSize.width, previewSize.height,
                        mGLRgbBuffer!!.array())
                mGLTextureId = OpenGLUtils.loadTexture(mGLRgbBuffer!!, previewSize, mGLTextureId)
                camera.addCallbackBuffer(data)

                if (mImageWidth != previewSize.width) {
                    mImageWidth = previewSize.width
                    mImageHeight = previewSize.height
                    adjustImageScaling()
                }
            })
        }
    }

    fun setUpSurfaceTexture(camera: Camera) {
        runOnDraw(Runnable {
            val textures = IntArray(1)
            GLES20.glGenTextures(1, textures, 0)
            mSurfaceTexture = SurfaceTexture(textures[0])
            try {
                camera.setPreviewTexture(mSurfaceTexture)
                camera.setPreviewCallback(this@GPUImageRenderer)
                camera.startPreview()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        })
    }

    fun setFilter(filter: GPUImageFilter) {
        runOnDraw(Runnable {
            val oldFilter = mFilter
            mFilter = filter
            oldFilter?.destroy()
            mFilter.init()
            GLES20.glUseProgram(mFilter.getProgram())
            mFilter.onOutputSizeChanged(mOutputWidth, mOutputHeight)
        })
    }

    private fun adjustImageScaling() {
        var outputWidth = mOutputWidth.toFloat()
        var outputHeight = mOutputHeight.toFloat()
        if (mRotation == Rotation.ROTATION_270 || mRotation == Rotation.ROTATION_90) {
            outputWidth = mOutputHeight.toFloat()
            outputHeight = mOutputWidth.toFloat()
        }

        val ratio1 = outputWidth / mImageWidth
        val ratio2 = outputHeight / mImageHeight
        val ratioMax = Math.max(ratio1, ratio2)
        val imageWidthNew = Math.round(mImageWidth * ratioMax)
        val imageHeightNew = Math.round(mImageHeight * ratioMax)

        val ratioWidth = imageWidthNew / outputWidth
        val ratioHeight = imageHeightNew / outputHeight

        var cube = CUBE
        var textureCords = TextureRotationUtil.getRotation(mRotation!!, mFlipHorizontal, mFlipVertical)
        if (mScaleType === ScaleType.CENTER_CROP) {
            val distHorizontal = (1 - 1 / ratioWidth) / 2
            val distVertical = (1 - 1 / ratioHeight) / 2
            textureCords = floatArrayOf(addDistance(textureCords[0], distHorizontal), addDistance(textureCords[1], distVertical), addDistance(textureCords[2], distHorizontal), addDistance(textureCords[3], distVertical), addDistance(textureCords[4], distHorizontal), addDistance(textureCords[5], distVertical), addDistance(textureCords[6], distHorizontal), addDistance(textureCords[7], distVertical))
        } else {
            cube = floatArrayOf(CUBE[0] / ratioHeight, CUBE[1] / ratioWidth, CUBE[2] / ratioHeight, CUBE[3] / ratioWidth, CUBE[4] / ratioHeight, CUBE[5] / ratioWidth, CUBE[6] / ratioHeight, CUBE[7] / ratioWidth)
        }

        mGLCubeBuffer.clear()
        mGLCubeBuffer.put(cube).position(0)
        mGLTextureBuffer.clear()
        mGLTextureBuffer.put(textureCords).position(0)
    }

    private fun addDistance(coordinate: Float, distance: Float): Float {
        return if (coordinate == 0.0f) distance else 1 - distance
    }

    fun setRotationCamera(rotation: Rotation, flipHorizontal: Boolean,
                          flipVertical: Boolean) {
        setRotation(rotation, flipVertical, flipHorizontal)
    }

    fun setRotation(rotation: Rotation) {
        mRotation = rotation
        adjustImageScaling()
    }

    fun setRotation(rotation: Rotation,
                    flipHorizontal: Boolean, flipVertical: Boolean) {
        mFlipHorizontal = flipHorizontal
        mFlipVertical = flipVertical
        setRotation(rotation)
    }

    protected fun runOnDraw(runnable: Runnable) {
        synchronized(mRunOnDraw) {
            mRunOnDraw.add(runnable)
        }
    }

}