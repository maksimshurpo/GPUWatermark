package com.mshurpo.testview

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Environment
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import com.mshurpo.testview.camera.CameraHelper
import com.mshurpo.testview.camera.CameraLoader
import com.mshurpo.testview.gpu.*
import kotlinx.android.synthetic.main.activity_camera.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Created by maksimsurpo on 5/19/17.
 */
class CameraActivity : AppCompatActivity() {

    lateinit var gpuImage: GPUImage

    lateinit var cameraHelper: CameraHelper

    lateinit var cameraLoader: CameraLoader

    private var isRecording: Boolean = false

    private lateinit var movieWriter: GPUImageMovieWriter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        gpuImage = GPUImage(this)
        gpuImage.setGLSurfaceView(gl_surface)

        movieWriter = GPUImageMovieWriter()

        cameraHelper = CameraHelper()
        cameraLoader = CameraLoader(this, cameraHelper, gpuImage)

        btn_switch.setOnClickListener {
            if (cameraHelper.hasFrontCamera().not() || cameraHelper.hasBackCamera().not()) {
                btn_switch.visibility = View.GONE
            } else {
                cameraLoader.switchCamera()
            }
        }

        btn_recording.setOnClickListener {
            if (isRecording) {
                isRecording = false
                movieWriter.stopRecording()
                btn_recording.text = "Stop"
            } else {
                isRecording = true
                val recordFile: File? = getOutputMediaFile()
                recordFile?.let {
                    movieWriter.startRecording(it.absolutePath, 540, 540)
                }
                btn_recording.text = "Start"
            }
        }

        val filters = GPUImageFilterGroup()
        val filter = createBlendFilter(this, GPUImageNormalBlendFilter::class.java)
        filter?.let {
            filters.addFilter(it)
            filters.addFilter(movieWriter)
            gpuImage.setFilter(filters)
        }

    }

    private fun getOutputMediaFile(): File? {
        val mediaStorageDir = File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "MyCameraApp")
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                Log.d("MyCameraApp", "failed to create directory")
                return null
            }
        }

        // Create a media file name
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val mediaFile: File

        mediaFile = File(mediaStorageDir.path + File.separator +
                "VID_" + timeStamp + ".mp4")
        return mediaFile
    }

    override fun onResume() {
        super.onResume()
        cameraLoader.onResume()
    }

    override fun onPause() {
        cameraLoader.onPause()
        super.onPause()

        if (isRecording) {
            movieWriter.stopRecording()
        }
    }

    private fun createBlendFilter(context: Context, filterClass: Class<out GPUImageTwoInputFilter>): GPUImageFilter? {
        try {
            val filter = filterClass.newInstance()
            filter.setBitmap(BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher))
            return filter
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }

    }
}