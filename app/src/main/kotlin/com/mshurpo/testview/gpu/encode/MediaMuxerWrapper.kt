package com.mshurpo.testview.gpu.encode

/*
 * AudioVideoRecordingSample
 * Sample project to cature audio and video from internal mic/camera and save as MPEG4 file.
 *
 * Copyright (c) 2014-2015 saki t_saki@serenegiant.com
 *
 * File name: MediaMuxerWrapper.java
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 * All files in the folder are under this Apache License, Version 2.0.
*/

import android.annotation.TargetApi
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log

import java.io.IOException
import java.nio.ByteBuffer

@TargetApi(18)
class MediaMuxerWrapper @Throws(IOException::class)
constructor(
        //private static final String DIR_NAME = "AVRecSample";
        //private static final SimpleDateFormat mDateTimeFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US);

        private val mOutputPath: String) {
    private val mMediaMuxer: MediaMuxer    // API >= 18
    private var mEncoderCount: Int = 0
    private var mStatredCount: Int = 0
    @get:Synchronized var isStarted: Boolean = false
        private set
    private var mVideoEncoder: MediaEncoder? = null
    private var mAudioEncoder: MediaEncoder? = null

    init {
        mMediaMuxer = MediaMuxer(mOutputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        mStatredCount = 0
        mEncoderCount = mStatredCount
        isStarted = false
    }

    @Throws(IOException::class)
    fun prepare() {
        if (mVideoEncoder != null)
            mVideoEncoder!!.prepare()
        if (mAudioEncoder != null)
            mAudioEncoder!!.prepare()
    }

    fun startRecording() {
        if (mVideoEncoder != null)
            mVideoEncoder!!.startRecording()
        if (mAudioEncoder != null)
            mAudioEncoder!!.startRecording()
    }

    fun stopRecording() {
        if (mVideoEncoder != null)
            mVideoEncoder!!.stopRecording()
        mVideoEncoder = null
        if (mAudioEncoder != null)
            mAudioEncoder!!.stopRecording()
        mAudioEncoder = null
    }

    //**********************************************************************
    //**********************************************************************
    /**
     * assign encoder to this calss. this is called from encoder.
     * @param encoder instance of MediaVideoEncoder or MediaAudioEncoder
     */
    /*package*/ internal fun addEncoder(encoder: MediaEncoder) {
        if (encoder is MediaVideoEncoder) {
            if (mVideoEncoder != null)
                throw IllegalArgumentException("Video encoder already added.")
            mVideoEncoder = encoder
        } else if (encoder is MediaAudioEncoder) {
            if (mAudioEncoder != null)
                throw IllegalArgumentException("Video encoder already added.")
            mAudioEncoder = encoder
        } else
            throw IllegalArgumentException("unsupported encoder")
        mEncoderCount = (if (mVideoEncoder != null) 1 else 0) + if (mAudioEncoder != null) 1 else 0
    }

    /**
     * request start recording from encoder
     * @return true when muxer is ready to write
     */
    /*package*/ @Synchronized internal fun start(): Boolean {
        if (DEBUG) Log.v(TAG, "start:")
        mStatredCount++
        if (mEncoderCount > 0 && mStatredCount == mEncoderCount) {
            mMediaMuxer.start()
            isStarted = true
            (this as Object).notifyAll()
            if (DEBUG) Log.v(TAG, "MediaMuxer started:")
        }
        return isStarted
    }

    /**
     * request stop recording from encoder when encoder received EOS
     */
    /*package*/ @Synchronized internal fun stop(): Boolean {
        if (DEBUG) Log.v(TAG, "stop:mStatredCount=" + mStatredCount)
        mStatredCount--
        if (mEncoderCount > 0 && mStatredCount <= 0) {
            mMediaMuxer.stop()
            mMediaMuxer.release()
            isStarted = false
            if (DEBUG) Log.v(TAG, "MediaMuxer stopped:")
            return true
        }
        return false
    }

    /**
     * assign encoder to muxer
     * @param format
     * *
     * @return minus value indicate error
     */
    /*package*/ @Synchronized internal fun addTrack(format: MediaFormat): Int {
        if (isStarted)
            throw IllegalStateException("muxer already started")
        val trackIx = mMediaMuxer.addTrack(format)
        if (DEBUG) Log.i(TAG, "addTrack:trackNum=$mEncoderCount,trackIx=$trackIx,format=$format")
        return trackIx
    }

    /**
     * write encoded data to muxer
     * @param trackIndex
     * *
     * @param byteBuf
     * *
     * @param bufferInfo
     */
    /*package*/ @Synchronized internal fun writeSampleData(trackIndex: Int, byteBuf: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        if (mStatredCount > 0)
            mMediaMuxer.writeSampleData(trackIndex, byteBuf, bufferInfo)
    }

    @Synchronized internal fun removeFailEncoder() {
        mEncoderCount--

        if (mEncoderCount > 0 && mStatredCount == mEncoderCount) {
            mMediaMuxer.start()
            isStarted = true
            (this as Object).notifyAll()
            if (DEBUG) Log.v(TAG, "MediaMuxer force start")
        }
    }

    companion object {
        private val DEBUG = false
        private val TAG = "MediaMuxerWrapper"
    }

}
