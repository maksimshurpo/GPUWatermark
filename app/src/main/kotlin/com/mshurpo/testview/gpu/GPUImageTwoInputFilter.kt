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

    var filterSecondTextureCoordinateAttribute: Int = 0
    var filterInputTextureUniform2: Int = 0
    var filterSourceTexture2 = OpenGLUtils.NO_TEXTURE
    private lateinit var texture2CoordinatesBuffer: ByteBuffer
    private lateinit var bitmap: Bitmap

    constructor(fragmentShader: String) : this(VERTEX_SHADER, fragmentShader)
    constructor(vertexShader: String, fragmentShader: String) : super(vertexShader, fragmentShader) {
        setRotation(Rotation.NORMAL, false, false)
    }

    override fun onInit() {
        super.onInit()

        filterSecondTextureCoordinateAttribute = GLES20.glGetAttribLocation(glProgId, "inputTextureCoordinate2")
        filterInputTextureUniform2 = GLES20.glGetUniformLocation(glProgId, "inputImageTexture2") // This does assume a name of "inputImageTexture2" for second input texture in the fragment shader
        GLES20.glEnableVertexAttribArray(filterSecondTextureCoordinateAttribute)

        if (bitmap != null && !bitmap.isRecycled()) {
            initBitmap(bitmap)
        }
    }

    fun initBitmap(bitmap: Bitmap) {
        if (bitmap != null && bitmap.isRecycled) {
            return
        }
        this.bitmap = bitmap
        if (this.bitmap == null) {
            return
        }
        runOnDraw(Runnable {
            if (filterSourceTexture2 == OpenGLUtils.NO_TEXTURE) {
                if (bitmap == null || bitmap.isRecycled) {
                    return@Runnable
                }
                GLES20.glActiveTexture(GLES20.GL_TEXTURE3)
                filterSourceTexture2 = OpenGLUtils.loadTexture(bitmap, OpenGLUtils.NO_TEXTURE, false)
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        GLES20.glDeleteTextures(1, intArrayOf(filterSourceTexture2), 0)
        filterSourceTexture2 = OpenGLUtils.NO_TEXTURE
    }

    override fun onDrawArraysPre() {
        GLES20.glEnableVertexAttribArray(filterSecondTextureCoordinateAttribute)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE3)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, filterSourceTexture2)
        GLES20.glUniform1i(filterInputTextureUniform2, 3)

        texture2CoordinatesBuffer.position(0)
        GLES20.glVertexAttribPointer(filterSecondTextureCoordinateAttribute, 2, GLES20.GL_FLOAT, false, 0, texture2CoordinatesBuffer)
    }

    fun setRotation(rotation: Rotation, flipHorizontal: Boolean, flipVertical: Boolean) {
        val buffer = TextureRotationUtil.getRotation(rotation, flipHorizontal, flipVertical)

        val bBuffer = ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder())
        val fBuffer = bBuffer.asFloatBuffer()
        fBuffer.put(buffer)
        fBuffer.flip()

        texture2CoordinatesBuffer = bBuffer
    }
}