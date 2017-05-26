package com.mshurpo.testview.gpu.encode

/*
 * AudioVideoRecordingSample
 * Sample project to cature audio and video from internal mic/camera and save as MPEG4 file.
 *
 * Copyright (c) 2014-2015 saki t_saki@serenegiant.com
 *
 * File name: MediaVideoEncoder.java
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
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.util.Log
import android.view.Surface

import java.io.IOException

@TargetApi(18)
class MediaVideoEncoder(muxer: MediaMuxerWrapper, listener: MediaEncoder.MediaEncoderListener,
        //private static final float BPP_LOW = 0.15f;

                        private val mWidth: Int, private val mHeight: Int) : MediaEncoder(muxer, listener) {
    // private RenderHandler mRenderHandler;
    var surface: Surface? = null
        private set

    init {
        if (DEBUG) Log.i(TAG, "MediaVideoEncoder: ")
    }

    override fun frameAvailableSoon(): Boolean {
        return super.frameAvailableSoon()
    }

    @Throws(IOException::class)
    override fun prepare() {
        if (DEBUG) Log.i(TAG, "prepare: ")
        mTrackIndex = -1
        mIsEOS = false
        mMuxerStarted = mIsEOS

        val videoCodecInfo = selectVideoCodec(MIME_TYPE)
        if (videoCodecInfo == null) {
            Log.e(TAG, "Unable to find an appropriate codec for " + MIME_TYPE)
            return
        }
        if (DEBUG) Log.i(TAG, "selected codec: " + videoCodecInfo.name)

        val format = MediaFormat.createVideoFormat(MIME_TYPE, mWidth, mHeight)
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)    // API >= 18
        format.setInteger(MediaFormat.KEY_BIT_RATE, calcBitRate())
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 10)
        if (DEBUG) Log.i(TAG, "format: " + format)

        mMediaCodec = MediaCodec.createEncoderByType(MIME_TYPE)
        mMediaCodec!!.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        // get Surface for encoder input
        // this method only can call between #configure and #start
        surface = mMediaCodec!!.createInputSurface()    // API >= 18
        mMediaCodec!!.start()
        if (DEBUG) Log.i(TAG, "prepare finishing")
        if (mListener != null) {
            try {
                mListener.onPrepared(this)
            } catch (e: Exception) {
                Log.e(TAG, "prepare:", e)
            }

        }
    }

    override fun release() {
        if (DEBUG) Log.i(TAG, "release:")
        if (surface != null) {
            surface!!.release()
            surface = null
        }
        super.release()
    }

    private fun calcBitRate(): Int {
        /*float bpp = BPP;
        if (!"US".equalsIgnoreCase(LocateManager.getInstance().getCountry())) {
            bpp = BPP_LOW;
        }*/
        val bitrate = (BPP * FRAME_RATE.toFloat() * mWidth.toFloat() * mHeight.toFloat()).toInt()
        Log.i(TAG, String.format("bitrate=%5.2f[Mbps]", bitrate.toFloat() / 1024f / 1024f))
        return bitrate
    }

    override fun signalEndOfInputStream() {
        if (DEBUG) Log.d(TAG, "sending EOS to encoder")
        if (mMediaCodec != null) {
            mMediaCodec!!.signalEndOfInputStream()    // API >= 18
        }
        mIsEOS = true
    }

    companion object {
        private val DEBUG = false
        private val TAG = "MediaVideoEncoder"

        private val MIME_TYPE = "video/avc"
        // parameters for recording
        private val FRAME_RATE = 25
        private val BPP = 0.25f

        /**
         * select the first codec that match a specific MIME type
         * @param mimeType
         * *
         * @return null if no codec matched
         */
        protected fun selectVideoCodec(mimeType: String): MediaCodecInfo? {
            if (DEBUG) Log.v(TAG, "selectVideoCodec:")

            // get the list of available codecs
            val numCodecs = MediaCodecList.getCodecCount()
            for (i in 0..numCodecs - 1) {
                val codecInfo = MediaCodecList.getCodecInfoAt(i)

                if (!codecInfo.isEncoder) {    // skipp decoder
                    continue
                }
                // select first codec that match a specific MIME type and color format
                val types = codecInfo.supportedTypes
                for (j in types.indices) {
                    if (types[j].equals(mimeType, ignoreCase = true)) {
                        if (DEBUG) Log.i(TAG, "codec:" + codecInfo.name + ",MIME=" + types[j])
                        val format = selectColorFormat(codecInfo, mimeType)
                        if (format > 0) {
                            return codecInfo
                        }
                    }
                }
            }
            return null
        }

        /**
         * select color format available on specific codec and we can use.
         * @return 0 if no colorFormat is matched
         */
        protected fun selectColorFormat(codecInfo: MediaCodecInfo, mimeType: String): Int {
            if (DEBUG) Log.i(TAG, "selectColorFormat: ")
            var result = 0
            val caps: MediaCodecInfo.CodecCapabilities
            try {
                Thread.currentThread().priority = Thread.MAX_PRIORITY
                caps = codecInfo.getCapabilitiesForType(mimeType)
            } finally {
                Thread.currentThread().priority = Thread.NORM_PRIORITY
            }
            var colorFormat: Int
            for (i in caps.colorFormats.indices) {
                colorFormat = caps.colorFormats[i]
                if (isRecognizedViewoFormat(colorFormat)) {
                    if (result == 0)
                        result = colorFormat
                    break
                }
            }
            if (result == 0)
                Log.e(TAG, "couldn't find a good color format for " + codecInfo.name + " / " + mimeType)
            return result
        }

        /**
         * color formats that we can use in this class
         */
        protected var recognizedFormats: IntArray? = null

        init {
            recognizedFormats = intArrayOf(MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            //            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar,
            //            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar,
            //            MediaCodecInfo.CodecCapabilities.COLOR_QCOM_FormatYUV420SemiPlanar,
        }

        private fun isRecognizedViewoFormat(colorFormat: Int): Boolean {
            if (DEBUG) Log.i(TAG, "isRecognizedViewoFormat:colorFormat=" + colorFormat)
            val n = if (recognizedFormats != null) recognizedFormats!!.size else 0
            for (i in 0..n - 1) {
                if (recognizedFormats!![i] == colorFormat) {
                    return true
                }
            }
            return false
        }
    }

}
