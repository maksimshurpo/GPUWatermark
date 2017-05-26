package com.mshurpo.testview.gpu

import android.annotation.SuppressLint
import android.opengl.GLES20
import com.mshurpo.testview.gpu.utils.TextureRotationUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.ArrayList

/**
 * Created by maksimsurpo on 5/21/17.
 */
class GPUImageFilterGroup : GPUImageFilter {

    protected var mFilters: MutableList<GPUImageFilter>? = null
    protected var mMergedFilters: MutableList<GPUImageFilter>? = null
    private var mFrameBuffers: IntArray? = null
    private var mFrameBufferTextures: IntArray? = null

    private val mGLCubeBuffer: FloatBuffer
    private val mGLTextureBuffer: FloatBuffer
    private val mGLTextureFlipBuffer: FloatBuffer

    constructor() : this(null)

    constructor(filters: MutableList<GPUImageFilter>?) {
        mFilters = filters
        if (mFilters == null) {
            mFilters = java.util.ArrayList<GPUImageFilter>()
        } else {
            updateMergedFilters()
        }

        mGLCubeBuffer = ByteBuffer.allocateDirect(GPUImageRenderer.CUBE.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
        mGLCubeBuffer.put(GPUImageRenderer.CUBE).position(0)

        mGLTextureBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.TEXTURE_NO_ROTATION.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
        mGLTextureBuffer.put(TextureRotationUtil.TEXTURE_NO_ROTATION).position(0)

        val flipTexture = TextureRotationUtil.getRotation(Rotation.NORMAL, false, true)
        mGLTextureFlipBuffer = ByteBuffer.allocateDirect(flipTexture.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
        mGLTextureFlipBuffer.put(flipTexture).position(0)
    }

    fun addFilter(aFilter: GPUImageFilter?) {
        if (aFilter == null) {
            return
        }
        mFilters!!.add(aFilter)
        updateMergedFilters()
    }

    override fun onInit() {
        super.onInit()
        mFilters?.let {
            for (filter in it) {
                filter.init()
            }
        }
    }

    override fun onDestroy() {
        destroyFramebuffers()
        mFilters?.let {
            for (filter in it) {
                filter.destroy()
            }
        }

        super.onDestroy()
    }

    private fun destroyFramebuffers() {
        if (mFrameBufferTextures != null) {
            GLES20.glDeleteTextures(mFrameBufferTextures!!.size, mFrameBufferTextures, 0)
            mFrameBufferTextures = null
        }
        if (mFrameBuffers != null) {
            GLES20.glDeleteFramebuffers(mFrameBuffers!!.size, mFrameBuffers, 0)
            mFrameBuffers = null
        }
    }

    override fun onOutputSizeChanged(width: Int, height: Int) {
        super.onOutputSizeChanged(width, height)
        if (mFilters == null) {
            return
        }
        if (mFrameBuffers != null) {
            destroyFramebuffers()
        }

        var size = mFilters!!.size
        for (i in 0..size - 1) {
            mFilters!![i].onOutputSizeChanged(width, height)
        }

        if (mMergedFilters != null && mMergedFilters!!.size > 0) {
            size = mMergedFilters!!.size
            mFrameBuffers = IntArray(size - 1)
            mFrameBufferTextures = IntArray(size - 1)

            for (i in 0..size - 1 - 1) {
                GLES20.glGenFramebuffers(1, mFrameBuffers, i)
                GLES20.glGenTextures(1, mFrameBufferTextures, i)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mFrameBufferTextures!![i])
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

                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffers!![i])
                GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                        GLES20.GL_TEXTURE_2D, mFrameBufferTextures!![i], 0)

                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            }
        }
    }

    @SuppressLint("WrongCall")
    override fun onDraw(textureId: Int, cubeBuffer: FloatBuffer,
               textureBuffer: FloatBuffer) {
        runPendingOnDrawTasks()
        if (!isInitialized || mFrameBuffers == null || mFrameBufferTextures == null) {
            return
        }
        if (mMergedFilters != null) {
            val size = mMergedFilters!!.size
            var previousTexture = textureId
            for (i in 0..size - 1) {
                val filter = mMergedFilters!!.get(i)
                val isNotLast = i < size - 1
                if (isNotLast) {
                    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffers!![i])
                    GLES20.glClearColor(0f, 0f, 0f, 0f)
                }

                if (i == 0) {
                    filter.onDraw(previousTexture, cubeBuffer, textureBuffer)
                } else if (i == size - 1) {
                    filter.onDraw(previousTexture, mGLCubeBuffer, if (size % 2 == 0) mGLTextureFlipBuffer else mGLTextureBuffer)
                } else {
                    filter.onDraw(previousTexture, mGLCubeBuffer, mGLTextureBuffer)
                }

                if (isNotLast) {
                    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
                    previousTexture = mFrameBufferTextures!![i]
                }
            }
        }
    }

    fun updateMergedFilters() {
        if (mFilters == null) {
            return
        }

        if (mMergedFilters == null) {
            mMergedFilters = ArrayList<GPUImageFilter>()
        } else {
            mMergedFilters!!.clear()
        }

        var filters: List<GPUImageFilter>?
        for (filter in mFilters!!) {
            if (filter is GPUImageFilterGroup) {
                filter.updateMergedFilters()
                filters = filter.mMergedFilters
                if (filters == null || filters.isEmpty())
                    continue
                mMergedFilters!!.addAll(filters)
                continue
            }
            mMergedFilters!!.add(filter)
        }
    }
}