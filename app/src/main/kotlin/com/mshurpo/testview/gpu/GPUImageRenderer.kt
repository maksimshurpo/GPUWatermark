package com.mshurpo.testview.gpu

import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
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
open class GPUImageRenderer(var filter: GPUImageFilter) : GLSurfaceView.Renderer, Camera.PreviewCallback {
    companion object {
        val NO_IMAGE = -1
        internal val CUBE = floatArrayOf(-1.0f, -1.0f, 1.0f, -1.0f, -1.0f, 1.0f, 1.0f, 1.0f)
    }

    val surfaceChangedWaiter = Object()
    private var glTextureId = NO_IMAGE
    private var surfaceTexture: SurfaceTexture? = null
    private var glCubeBuffer: FloatBuffer = ByteBuffer.allocateDirect(CUBE.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
    private var glTextureBuffer: FloatBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.TEXTURE_NO_ROTATION.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()

    private var glRgbBuffer: IntBuffer? = null

    private var outputWidth: Int = 0
    private var outputHeight: Int = 0
    private var imageWidth: Int = 0
    private var imageHeight: Int = 0

    private val runOnDraw: Queue<Runnable> = LinkedList<Runnable>()
    private lateinit var rotation: Rotation
    private var flipHorizontal: Boolean = false
    private var flipVertical: Boolean = false
    private val scaleType: ScaleType = ScaleType.CENTER_CROP

    private var backgroundRed: Float = 0F
    private var backgroundGreen: Float = 0F
    private var backgroundBlue: Float = 0F

    init {
        glCubeBuffer.put(CUBE).position(0)
        onRotation(Rotation.NORMAL, false, false)
    }

    override fun onSurfaceCreated(p0: GL10?, p1: EGLConfig?) {
        Log.d("TTT", "onSurfaceCreated")
        GLES20.glClearColor(backgroundRed, backgroundGreen, backgroundBlue, 1f)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        this.filter.init()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        Log.d("TTT", "onSurfaceChanged")
        this.outputWidth = width
        this.outputHeight = height
        GLES20.glViewport(0, 0, width, height)
        GLES20.glUseProgram(this.filter.glProgId)
        this.filter.onOutputSizeChanged(width, height)
        adjustImageScaling()
        synchronized(surfaceChangedWaiter) {
            surfaceChangedWaiter.notifyAll()
        }
    }

    override fun onDrawFrame(gl: GL10?) {
        Log.d("TTT", "onDrawFrame runOnDraw.size = ${runOnDraw.size}")
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        runAll(runOnDraw)
        this.filter.onDraw(glTextureId, glCubeBuffer, glTextureBuffer)
        surfaceTexture?.let {
            it.updateTexImage()
        }
    }

    private fun runAll(queue: Queue<Runnable>) {
        synchronized(queue) {
            while (queue.isEmpty().not()) {
                queue.poll().run()
            }
        }
    }

    override fun onPreviewFrame(data: ByteArray?, camera: Camera?) {
        camera?.let {
            Log.d("TTT", "onPreviewFrame")
            val previewSize = camera.parameters.previewSize
            if (glRgbBuffer == null) {
                glRgbBuffer = IntBuffer.allocate(previewSize.width * previewSize.height)
            }
            if (runOnDraw.isEmpty()) {
                runOnDraw(Runnable {
                    Log.d("TTT", "onPreviewFrame runnable")
                    GPUImageNativeLibrary.YUVtoRBGA(data, previewSize.width, previewSize.height,
                            glRgbBuffer?.array())
                    glTextureId = OpenGLUtils.loadTexture(glRgbBuffer!!, previewSize, glTextureId)
                    camera.addCallbackBuffer(data)

                    if (imageWidth != previewSize.width) {
                        imageWidth = previewSize.width
                        imageHeight = previewSize.height
                        adjustImageScaling()
                    }
                })
            }
        }
    }

    fun setUpSurfaceTexture(camera: Camera) {
        Log.d("TTT", "setUpSurfaceTexture")
        runOnDraw(Runnable {
            val textures = IntArray(1)
            GLES20.glGenTextures(1, textures, 0)
            surfaceTexture = SurfaceTexture(textures[0])
            try {
             /*   val previewSize = camera.parameters.supportedVideoSizes.first()

                camera.parameters.setPreviewSize(previewSize.width, previewSize.height)*/

                camera.setPreviewTexture(surfaceTexture)
                camera.setPreviewCallback(this@GPUImageRenderer)
                camera.startPreview()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        })
    }

    fun onFilter(filter: GPUImageFilter) {
        runOnDraw(Runnable {
            val oldFilter = this.filter
            this.filter = filter
            oldFilter?.let {
                it.destroy()
            }
            this.filter.init()
            GLES20.glUseProgram(this.filter.glProgId)
            this.filter.onOutputSizeChanged(this.outputWidth, this.outputHeight)
        })
    }

    private fun adjustImageScaling() {
        var outputWidth = this.outputWidth.toFloat()
        var outputHeight = this.outputHeight.toFloat()
        if (this.rotation == Rotation.ROTATION_270 || this.rotation == Rotation.ROTATION_90) {
            outputWidth = this.outputHeight.toFloat()
            outputHeight = this.outputWidth.toFloat()
        }

        val ratio1 = outputWidth / this.imageWidth
        val ratio2 = outputHeight / this.imageHeight
        val ratioMax = Math.max(ratio1, ratio2)
        val imageWidthNew = Math.round(this.imageWidth * ratioMax)
        val imageHeightNew = Math.round(this.imageHeight * ratioMax)

        val ratioWidth = imageWidthNew / outputWidth
        val ratioHeight = imageHeightNew / outputHeight

        var cube = CUBE
        var textureCords = TextureRotationUtil.getRotation(this.rotation, this.flipHorizontal, this.flipVertical)
        if (this.scaleType === ScaleType.CENTER_CROP) {
            val distHorizontal = (1 - 1 / ratioWidth) / 2
            val distVertical = (1 - 1 / ratioHeight) / 2
            textureCords = floatArrayOf(addDistance(textureCords[0], distHorizontal), addDistance(textureCords[1], distVertical), addDistance(textureCords[2], distHorizontal), addDistance(textureCords[3], distVertical), addDistance(textureCords[4], distHorizontal), addDistance(textureCords[5], distVertical), addDistance(textureCords[6], distHorizontal), addDistance(textureCords[7], distVertical))
        } else {
            cube = floatArrayOf(CUBE[0] / ratioHeight, CUBE[1] / ratioWidth, CUBE[2] / ratioHeight, CUBE[3] / ratioWidth, CUBE[4] / ratioHeight, CUBE[5] / ratioWidth, CUBE[6] / ratioHeight, CUBE[7] / ratioWidth)
        }

        glCubeBuffer.clear()
        glCubeBuffer.put(cube).position(0)
        glTextureBuffer.clear()
        glTextureBuffer.put(textureCords).position(0)
    }

    private fun addDistance(coordinate: Float, distance: Float): Float {
        return if (coordinate == 0.0f) distance else 1 - distance
    }

    fun setRotationCamera(rotation: Rotation, flipHorizontal: Boolean,
                          flipVertical: Boolean) {
        onRotation(rotation, flipVertical, flipHorizontal)
    }

    fun onRotation(rotation: Rotation) {
        this.rotation = rotation
        adjustImageScaling()
    }

    fun onRotation(rotation: Rotation, flipHorizontal: Boolean, flipVertical: Boolean) {
        this.flipHorizontal = flipHorizontal
        this.flipVertical = flipVertical
        onRotation(rotation)
    }

    protected fun runOnDraw(runnable: Runnable) {
        synchronized(runOnDraw) {
            runOnDraw.add(runnable)
        }
    }

}