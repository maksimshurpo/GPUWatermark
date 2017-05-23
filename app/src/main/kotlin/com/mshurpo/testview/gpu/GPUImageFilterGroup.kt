package com.mshurpo.testview.gpu

import android.opengl.GLES20
import com.mshurpo.testview.gpu.utils.TextureRotationUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Created by maksimsurpo on 5/21/17.
 */
class GPUImageFilterGroup : GPUImageFilter {

    protected var filters: List<GPUImageFilter>? = null
    var mergedFilters: List<GPUImageFilter>? = null

    private var frameBuffer: IntArray? = null
    private var frameBufferTexture: IntArray? = null

    private val glCubeBuffer: FloatBuffer = ByteBuffer.allocateDirect(GPUImageRenderer.CUBE.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()

    private val glTextureBuffer: FloatBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.TEXTURE_NO_ROTATION.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()

    private var glTextureFlipBuffer: FloatBuffer

    constructor() : this(null)

    constructor(filters: List<GPUImageFilter>?) {
        this.filters = filters
        if (this.filters == null) {
            this.filters = ArrayList<GPUImageFilter>()
        } else {
            updateMergedFilters()
        }
        glCubeBuffer.put(GPUImageRenderer.CUBE).position(0)
        glTextureBuffer.put(TextureRotationUtil.TEXTURE_NO_ROTATION).position(0)

        val flipTextute = TextureRotationUtil.getRotation(Rotation.NORMAL, false, false)
        glTextureFlipBuffer = ByteBuffer.allocateDirect(flipTextute.size * 4)
        .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
        glTextureFlipBuffer.put(flipTextute).position(0)
    }

    fun addFilter(filter: GPUImageFilter) {
        this.filters = this.filters?.plus(filter)
        updateMergedFilters()
    }

    override fun onInit() {
        super.onInit()
        this.filters?.forEach {
            it.init()
        }
    }

    override fun onDestroy() {
        destroyFrameBuffer()
        this.filters?.forEach { it.destroy() }
        super.onDestroy()
    }

    private fun destroyFrameBuffer() {
        frameBufferTexture?.let {
            GLES20.glDeleteTextures(it.size, it, 0)
            frameBufferTexture = null
        }
        frameBuffer?.let {
            GLES20.glDeleteFramebuffers(it.size, it, 0)
            frameBuffer = null
        }
    }

    override fun onOutputSizeChanged(width: Int, height: Int) {
        super.onOutputSizeChanged(width, height)
        if (frameBuffer != null) {
            destroyFrameBuffer()
        }

        var size = filters?.size ?: 0
        for (i in 0..size - 1) {
            filters?.get(i)?.onOutputSizeChanged(width, height)
        }

        if (mergedFilters != null && mergedFilters?.size ?: 0 > 0) {
            size = mergedFilters?.size ?: 0
            frameBuffer = IntArray(size - 1)
            frameBufferTexture = IntArray(size - 1)

            for (i in 0..size - 1 - 1) {
                GLES20.glGenFramebuffers(1, frameBuffer, i)
                GLES20.glGenTextures(1, frameBufferTexture, i)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, frameBufferTexture!![i])
                GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0,
                        GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
                GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                        GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR.toFloat())
                GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                        GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR.toFloat())
                GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                        GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE.toFloat())
                GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                        GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE.toFloat())

                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBuffer!![i])
                GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                        GLES20.GL_TEXTURE_2D, frameBufferTexture!![i], 0)

                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            }
        }
    }

    override fun onDraw(textureId: Int, cubeBuffer: FloatBuffer, textureBuffer: FloatBuffer) {
        runPendingOnDrawTasks()
        if (isInitialized.not() || frameBuffer == null || frameBufferTexture == null) {
            return
        }

        mergedFilters?.let {
            var previosTexture = textureId
            val size = it.size
            it.forEachIndexed { i, item ->
                val isNotLast = i < size - 1
                if (isNotLast) {
                    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBuffer!![i])
                    GLES20.glClearColor(0F, 0F, 0F, 0F)
                }

                if (i == 0) {
                    item.onDraw(previosTexture, cubeBuffer, textureBuffer)
                } else if (i == size - 1) {
                    val textureBuffer = if (size % 2 == 0) glTextureFlipBuffer else glTextureBuffer
                    item.onDraw(previosTexture, glCubeBuffer, textureBuffer)
                } else {
                    item.onDraw(previosTexture, glCubeBuffer, glTextureBuffer)
                }

                if (isNotLast) {
                    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
                    previosTexture = frameBufferTexture!![i]
                }
            }
        }
    }

    fun updateMergedFilters() {
        if (filters == null) {
            return
        }

        if (mergedFilters == null) {
            mergedFilters = ArrayList<GPUImageFilter>()
        } else {
            (mergedFilters as ArrayList<GPUImageFilter>).clear()
        }

        var filters: List<GPUImageFilter>?
        for (filter in this.filters!!) {
            if (filter is GPUImageFilterGroup) {
                filter.updateMergedFilters()
                filters = filter.mergedFilters
                if (filters == null || filters.isEmpty())
                    continue
                mergedFilters = mergedFilters?.plus(filters)
                continue
            }
            mergedFilters = mergedFilters?.plus(filter)
        }

    }
}