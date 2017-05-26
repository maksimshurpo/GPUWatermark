package com.mshurpo.testview.gpu

import android.opengl.GLES20
import java.nio.FloatBuffer
import java.util.*

/**
 * Created by maksimsurpo on 5/19/17.
 */
open class GPUImageFilter {
    companion object {
        val NO_FILTER_VERTEX_SHADER = "" +
                "attribute vec4 position;\n" +
                "attribute vec4 inputTextureCoordinate;\n" +
                " \n" +
                "varying vec2 textureCoordinate;\n" +
                " \n" +
                "void main()\n" +
                "{\n" +
                "    gl_Position = position;\n" +
                "    textureCoordinate = inputTextureCoordinate.xy;\n" +
                "}"
        val NO_FILTER_FRAGMENT_SHADER = "" +
                "varying highp vec2 textureCoordinate;\n" +
                " \n" +
                "uniform sampler2D inputImageTexture;\n" +
                " \n" +
                "void main()\n" +
                "{\n" +
                "     gl_FragColor = texture2D(inputImageTexture, textureCoordinate);\n" +
                "}"
    }

    private val runOnDraw = LinkedList<Runnable>()
    val vertexShader: String
    val fragmentShader: String

    var glProgId: Int = 0
    protected var glAttribPosition: Int = 0
    protected var glUniformTexture: Int = 0
    protected var glAttribTextureCoordinate: Int = 0
    protected var outputWidth: Int = 0
    protected var outputHeight: Int = 0

    var isInitialized: Boolean = false

    constructor(): this(NO_FILTER_VERTEX_SHADER, NO_FILTER_FRAGMENT_SHADER)

    constructor(vertexShader: String, fragmentShader: String) {
        this.vertexShader = vertexShader
        this.fragmentShader = fragmentShader
    }

    fun init() {
        onInit()
        isInitialized = true
    }

    open fun onInit() {
        glProgId = OpenGLUtils.loadProgram(vertexShader, fragmentShader)
        glAttribPosition = GLES20.glGetAttribLocation(glProgId, "position")
        glUniformTexture = GLES20.glGetUniformLocation(glProgId, "inputImageTexture")
        glAttribTextureCoordinate = GLES20.glGetAttribLocation(glProgId,
                "inputTextureCoordinate")
        isInitialized = true
    }


    fun destroy() {
        isInitialized = false
        GLES20.glDeleteProgram(glProgId)
        onDestroy()
    }

    open fun onDestroy() {}

    open fun onOutputSizeChanged(width: Int, height: Int) {
        this.outputWidth = width
        this.outputHeight = height
    }

    open fun onDraw(textureId: Int, cubeBuffer: FloatBuffer,
               textureBuffer: FloatBuffer) {
        GLES20.glUseProgram(glProgId)
        runPendingOnDrawTasks()
        if (!isInitialized) {
            return
        }

        cubeBuffer.position(0)
        GLES20.glVertexAttribPointer(glAttribPosition, 2, GLES20.GL_FLOAT, false, 0, cubeBuffer)
        GLES20.glEnableVertexAttribArray(glAttribPosition)
        textureBuffer.position(0)
        GLES20.glVertexAttribPointer(glAttribTextureCoordinate, 2, GLES20.GL_FLOAT, false, 0,
                textureBuffer)
        GLES20.glEnableVertexAttribArray(glAttribTextureCoordinate)
        if (textureId != OpenGLUtils.NO_TEXTURE) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
            GLES20.glUniform1i(glUniformTexture, 0)
        }
        onDrawArraysPre()
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(glAttribPosition)
        GLES20.glDisableVertexAttribArray(glAttribTextureCoordinate)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    }

    open protected fun onDrawArraysPre() {}

    protected fun runPendingOnDrawTasks() {
        while (!runOnDraw.isEmpty()) {
            runOnDraw.removeFirst().run()
        }
    }

    fun getProgram(): Int {
        return glProgId
    }

    protected fun runOnDraw(runnable: Runnable) {
        synchronized(runOnDraw) {
            runOnDraw.addLast(runnable)
        }
    }
}