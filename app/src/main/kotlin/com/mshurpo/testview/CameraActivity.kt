package com.mshurpo.testview

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View
import com.mshurpo.testview.camera.CameraHelper
import com.mshurpo.testview.camera.CameraLoader
import com.mshurpo.testview.gpu.*
import kotlinx.android.synthetic.main.activity_camera.*

/**
 * Created by maksimsurpo on 5/19/17.
 */
class CameraActivity : AppCompatActivity() {

    lateinit var gpuImage: GPUImage

    lateinit var cameraHelper: CameraHelper

    lateinit var cameraLoader: CameraLoader

    private var isRecording: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        gpuImage = GPUImage(this)
        gpuImage.setGLSurfaceView(gl_surface)

        cameraHelper = CameraHelper()
        cameraLoader = CameraLoader(this, cameraHelper, gpuImage)

        btn_switch.setOnClickListener {
            if (cameraHelper.hasFrontCamera().not() || cameraHelper.hasBackCamera().not()) {
                btn_switch.visibility = View.GONE
            } else {
                cameraLoader.switchCamera()
            }
        }

        val filters = GPUImageFilterGroup()
        val filter = createBlendFilter(this, GPUImageNormalBlendFilter::class.java)
        filter?.let {
            filters.addFilter(it)
            gpuImage.setFilter(filters)
        }

    }

    override fun onResume() {
        super.onResume()
        cameraLoader.onResume()
    }

    override fun onPause() {
        cameraLoader.onPause()
        super.onPause()

        if (isRecording) {
            //todo stopRecording
        }
    }

    private fun createBlendFilter(context: Context, filterClass: Class<out GPUImageTwoInputFilter>): GPUImageFilter? {
        try {
            val filter = filterClass.newInstance()
            filter.initBitmap(BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher))
            return filter
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }

    }
}