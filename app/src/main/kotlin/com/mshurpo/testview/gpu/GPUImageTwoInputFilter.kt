package com.mshurpo.testview.gpu

import android.graphics.Bitmap
import android.opengl.GLES20
import com.mshurpo.testview.gpu.utils.TextureRotationUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Created by maksimsurpo on 5/21/17.
 */
open class GPUImageTwoInputFilter : GPUImageFilter {


    companion object {
        private val VERTEX_SHADER = "attribute vec4 position;\n" +
                "attribute vec4 inputTextureCoordinate;\n" +
                "attribute vec4 inputTextureCoordinate2;\n" +
                " \n" +
                "varying vec2 textureCoordinate;\n" +
                "varying vec2 textureCoordinate2;\n" +
                " \n" +
                "void main()\n" +
                "{\n" +
                "    gl_Position = position;\n" +
                "    textureCoordinate = inputTextureCoordinate.xy;\n" +
                "    textureCoordinate2 = inputTextureCoordinate2.xy;\n" +
                "}"
    }

    var mFilterSecondTextureCoordinateAttribute: Int = 0
    var mFilterInputTextureUniform2: Int = 0
    var mFilterSourceTexture2 = OpenGLUtils.NO_TEXTURE
    private var mTexture2CoordinatesBuffer: ByteBuffer? = null
    private var mBitmap: Bitmap? = null


    constructor(fragmentShader: String) : this(VERTEX_SHADER, fragmentShader)
    constructor(vertexShader: String, fragmentShader: String) : super(vertexShader, fragmentShader) {
        setRotation(Rotation.NORMAL, false, false)
    }

    override fun onInit() {
        super.onInit()

        mFilterSecondTextureCoordinateAttribute = GLES20.glGetAttribLocation(getProgram(), "inputTextureCoordinate2")
        mFilterInputTextureUniform2 = GLES20.glGetUniformLocation(getProgram(), "inputImageTexture2") // This does assume a name of "inputImageTexture2" for second input texture in the fragment shader
        GLES20.glEnableVertexAttribArray(mFilterSecondTextureCoordinateAttribute)

        if (mBitmap != null && !mBitmap!!.isRecycled()) {
            setBitmap(mBitmap)
        }
    }

    fun setBitmap(bitmap: Bitmap?) {
        if (bitmap != null && bitmap.isRecycled) {
            return
        }
        mBitmap = bitmap
        if (mBitmap == null) {
            return
        }
        runOnDraw(Runnable {
            if (mFilterSourceTexture2 == OpenGLUtils.NO_TEXTURE) {
                if (bitmap == null || bitmap.isRecycled) {
                    return@Runnable
                }
                GLES20.glActiveTexture(GLES20.GL_TEXTURE3)
                mFilterSourceTexture2 = OpenGLUtils.loadTexture(bitmap, OpenGLUtils.NO_TEXTURE, false)
            }
        })
    }
    override fun onDestroy() {
        super.onDestroy()
        GLES20.glDeleteTextures(1, intArrayOf(mFilterSourceTexture2), 0)
        mFilterSourceTexture2 = OpenGLUtils.NO_TEXTURE
    }

    override fun onDrawArraysPre() {
        GLES20.glEnableVertexAttribArray(mFilterSecondTextureCoordinateAttribute)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE3)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mFilterSourceTexture2)
        GLES20.glUniform1i(mFilterInputTextureUniform2, 3)

        mTexture2CoordinatesBuffer!!.position(0)
        GLES20.glVertexAttribPointer(mFilterSecondTextureCoordinateAttribute, 2, GLES20.GL_FLOAT, false, 0, mTexture2CoordinatesBuffer)
    }

    fun setRotation(rotation: Rotation, flipHorizontal: Boolean, flipVertical: Boolean) {
        val buffer = floatArrayOf(
                0.0f, 1.0f, 1.0f,
                1.0f, 0.0f, 0.0f,
                1.0f, 0.0f)//TextureRotationUtil.getRotation(rotation, flipHorizontal, flipVertical)

        val bBuffer = ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder())
        val fBuffer = bBuffer.asFloatBuffer()
        fBuffer.put(buffer)
        fBuffer.flip()

        mTexture2CoordinatesBuffer = bBuffer
    }
}