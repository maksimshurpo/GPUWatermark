package com.mshurpo.testview

import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import com.mshurpo.testview.mixer.MergeVideos
import com.mshurpo.testview.mixer.MixAsynk
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        btn.setOnClickListener {
            startActivity(Intent(this, CameraActivity::class.java))
        }
        btn_join.setOnClickListener {
            MergeVideos(this).execute()
        }

        btn_mix.setOnClickListener {
            MixAsynk(this).execute()
        }
    }
}
