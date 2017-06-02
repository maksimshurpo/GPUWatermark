package com.mshurpo.testview.gpu.encode

import android.annotation.TargetApi
import android.media.MediaCodec
import android.util.Log

import java.io.IOException
import java.nio.ByteBuffer

@TargetApi(18)
abstract class MediaEncoder(
        /**
         * Weak refarence of MediaMuxerWarapper instance
         */
        protected var mMuxer: MediaMuxerWrapper?, protected val mListener: MediaEncoder.MediaEncoderListener?) : Runnable {

    interface MediaEncoderListener {
        fun onPrepared(encoder: MediaEncoder)
        fun onStopped(encoder: MediaEncoder)
        fun onMuxerStopped()
    }

    protected val mSync = Object()
    /**
     * Flag that indicate this encoder is capturing now.
     */
    @Volatile protected var mIsCapturing: Boolean = false
    /**
     * Flag that indicate the frame data will be available soon.
     */
    private var mRequestDrain: Int = 0
    /**
     * Flag to request stop capturing
     */
    @Volatile protected var mRequestStop: Boolean = false
    /**
     * Flag that indicate encoder received EOS(End Of Stream)
     */
    protected var mIsEOS: Boolean = false
    /**
     * Flag the indicate the muxer is running
     */
    protected var mMuxerStarted: Boolean = false
    /**
     * Track Number
     */
    protected var mTrackIndex: Int = 0
    /**
     * MediaCodec instance for encoding
     */
    protected var mMediaCodec: MediaCodec? = null                // API >= 16(Android4.1.2)
    /**
     * BufferInfo instance for dequeuing
     */
    private var mBufferInfo: MediaCodec.BufferInfo? = null        // API >= 16(Android4.1.2)

    internal var mInputError = false

    init {
        if (mListener == null) throw NullPointerException("MediaEncoderListener is null")
        if (mMuxer == null) throw NullPointerException("MediaMuxerWrapper is null")
        mMuxer!!.addEncoder(this)
        synchronized(mSync) {
            // create BufferInfo here for effectiveness(to reduce GC)
            mBufferInfo = MediaCodec.BufferInfo()
            // wait for starting thread
            Thread(this, javaClass.simpleName).start()
            try {
                mSync.wait()
            } catch (e: InterruptedException) {
            }

        }
    }

    /**
     * the method to indicate frame data is soon available or already available
     * @return return true if encoder is ready to encod.
     */
    open fun frameAvailableSoon(): Boolean {
        synchronized(mSync) {
            if (!mIsCapturing || mRequestStop) {
                return false
            }
            mRequestDrain++
            mSync.notifyAll()
        }
        return true
    }

    /**
     * encoding loop on private thread
     */
    override fun run() {
        synchronized(mSync) {
            mRequestStop = false
            mRequestDrain = 0
            mSync.notify()
        }
        var isRunning = true
        var localRequestStop: Boolean? = null
        var localRequestDrain: Boolean? = null
        while (isRunning) {
            synchronized(mSync) {
                localRequestStop = mRequestStop
                localRequestDrain = mRequestDrain > 0
                if (localRequestDrain != null && localRequestDrain!!)
                    mRequestDrain--
            }

            if (mInputError) {
                inputError()
                release()
                break
            }

            if (localRequestStop != null && localRequestStop!!) {
                drain()
                // request stop recording
                signalEndOfInputStream()
                // process output data again for EOS signale
                drain()
                // release all related objects
                release()
                break
            }

            if (localRequestDrain != null && localRequestDrain!!) {
                drain()
            } else {
                synchronized(mSync) {
                    try {
                        mSync.wait()
                    } catch (e: InterruptedException) {
                        isRunning = false
                    }
                }
            }
        } // end of while
        if (DEBUG) Log.d(TAG, "Encoder thread exiting")
        synchronized(mSync) {
            mRequestStop = true
            mIsCapturing = false
        }
    }

    @Throws(IOException::class)
    internal abstract fun prepare()

    internal open fun startRecording() {
        if (DEBUG) Log.v(TAG, "startRecording")
        synchronized(mSync) {
            mIsCapturing = true
            mRequestStop = false
            mSync.notifyAll()
        }
    }

    /**
     * the method to request stop encoding
     */
    internal fun stopRecording() {
        if (DEBUG) Log.v(TAG, "stopRecording")
        synchronized(mSync) {
            if (!mIsCapturing || mRequestStop) {
                return
            }
            mRequestStop = true
            mSync.notifyAll()
        }
    }

    /**
     * Release all releated objects
     */
    protected open fun release() {
        if (DEBUG) Log.d(TAG, "release:")
        try {
            mListener?.onStopped(this)
        } catch (e: Exception) {
            Log.e(TAG, "failed onStopped", e)
        }

        mIsCapturing = false
        if (mMediaCodec != null) {
            try {
                mMediaCodec!!.stop()
                mMediaCodec!!.release()
                mMediaCodec = null
            } catch (e: Exception) {
                Log.e(TAG, "failed releasing MediaCodec", e)
            }

        }
        if (mMuxerStarted) {
            val muxer = mMuxer
            if (muxer != null) {
                try {
                    if (muxer.stop()) {
                        mListener?.onMuxerStopped()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "failed stopping muxer", e)
                }

            }
        }
        mBufferInfo = null
        mMuxer = null
    }

    protected open fun signalEndOfInputStream() {
        if (DEBUG) Log.d(TAG, "sending EOS to encoder")
        encode(null, 0, ptsUs)
    }

    /**
     * Method to set byte array to the MediaCodec encoder
     * @param buffer
     * *
     * @param length　length of byte array, zero means EOS.
     * *
     * @param presentationTimeUs
     */
    protected fun encode(buffer: ByteBuffer?, length: Int, presentationTimeUs: Long) {
        if (!mIsCapturing) return
        val inputBuffers = mMediaCodec!!.inputBuffers
        while (mIsCapturing) {
            val inputBufferIndex = mMediaCodec!!.dequeueInputBuffer(TIMEOUT_USEC.toLong())
            if (inputBufferIndex >= 0) {
                val inputBuffer = inputBuffers[inputBufferIndex]
                inputBuffer.clear()
                if (buffer != null) {
                    inputBuffer.put(buffer)
                }
                if (length <= 0) {
                    // send EOS
                    mIsEOS = true
                    if (DEBUG) Log.i(TAG, "send BUFFER_FLAG_END_OF_STREAM")
                    mMediaCodec!!.queueInputBuffer(inputBufferIndex, 0, 0,
                            presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    break
                } else {
                    mMediaCodec!!.queueInputBuffer(inputBufferIndex, 0, length,
                            presentationTimeUs, 0)
                }
                break
            } else if (inputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // wait for MediaCodec encoder is ready to encode
                // nothing to do here because MediaCodec#dequeueInputBuffer(TIMEOUT_USEC)
                // will wait for maximum TIMEOUT_USEC(10msec) on each call
            }
        }
    }

    /**
     * drain encoded data and write them to muxer
     */
    protected fun drain() {
        if (mMediaCodec == null) return
        var encoderOutputBuffers: Array<ByteBuffer>? = null
        try {
            encoderOutputBuffers = mMediaCodec!!.outputBuffers
        } catch (e: IllegalStateException) {
            Log.e(TAG, " mMediaCodec.getOutputBuffers() error")
            return
        }

        var encoderStatus: Int
        var count = 0
        val muxer = mMuxer
        if (muxer == null) {
            Log.w(TAG, "muxer is unexpectedly null")
            return
        }
        while (mIsCapturing) {

            try {
                encoderStatus = mMediaCodec!!.dequeueOutputBuffer(mBufferInfo!!, TIMEOUT_USEC.toLong())
            } catch (e: IllegalStateException) {
                encoderStatus = MediaCodec.INFO_TRY_AGAIN_LATER
            }

            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!mIsEOS) {
                    if (++count > 5)
                        break
                }
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                if (DEBUG) Log.v(TAG, "INFO_OUTPUT_BUFFERS_CHANGED")
                encoderOutputBuffers = mMediaCodec!!.outputBuffers
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (DEBUG) Log.v(TAG, "INFO_OUTPUT_FORMAT_CHANGED")
                if (mMuxerStarted) {    // second time request is error
                    throw RuntimeException("format changed twice")
                }
                val format = mMediaCodec!!.outputFormat // API >= 16
                mTrackIndex = muxer.addTrack(format)
                mMuxerStarted = true
                if (!muxer.start()) {
                    synchronized(muxer) {
                        while (!muxer.isStarted)
                            try {
                                (muxer as Object).wait(100)
                            } catch (e: InterruptedException) {
                                break
                            }

                    }
                }
            } else if (encoderStatus < 0) {
                if (DEBUG) Log.w(TAG, "drain:unexpected result from encoder#dequeueOutputBuffer: " + encoderStatus)
            } else {
                val encodedData = encoderOutputBuffers!![encoderStatus]
                if (mBufferInfo!!.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                    if (DEBUG) Log.d(TAG, "drain:BUFFER_FLAG_CODEC_CONFIG")
                    mBufferInfo!!.size = 0
                }

                if (mBufferInfo!!.size != 0) {
                    count = 0
                    if (!mMuxerStarted) {
                        throw RuntimeException("drain:muxer hasn't started")
                    }
                    mBufferInfo!!.presentationTimeUs = ptsUs
                    muxer.writeSampleData(mTrackIndex, encodedData, mBufferInfo!!)
                    prevOutputPTSUs = mBufferInfo!!.presentationTimeUs
                }
                mMediaCodec!!.releaseOutputBuffer(encoderStatus, false)
                if (mBufferInfo!!.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    mIsCapturing = false
                    break      // out of while
                }
            }
        }
    }

    private var prevOutputPTSUs: Long = 0

    protected val ptsUs: Long
        get() {
            var result = System.nanoTime() / 1000L
            if (result < prevOutputPTSUs)
                result = prevOutputPTSUs - result + result
            return result
        }

    protected fun inputError() {
        val muxer = mMuxer
        muxer?.removeFailEncoder()
    }

    companion object {
        private val DEBUG = false
        private val TAG = "MediaEncoder"

        protected val TIMEOUT_USEC = 10000    // 10[msec]
    }

}
