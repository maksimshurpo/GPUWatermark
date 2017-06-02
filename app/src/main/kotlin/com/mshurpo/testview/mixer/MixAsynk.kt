package com.mshurpo.testview.mixer

import android.content.Context
import android.os.AsyncTask
import android.os.Environment
import android.util.Log
import com.mshurpo.testview.gpu.MixTest
import java.io.FileNotFoundException
import java.io.IOException

/**
 * Created by maksimsurpo on 6/2/17.
 */
class MixAsynk(val context: Context) : AsyncTask<Void, Int, String>() {
    internal var building = true
    internal var rootSd = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString()

    override fun onPreExecute() {
        super.onPreExecute()
        Log.d("TTT", "${System.currentTimeMillis()}")
    }

    override fun doInBackground(vararg p0: Void?): String {

        val mixTest = MixTest()
        val duration = 30193
        val fileName = "123.wav"
        Log.d("TTT", "Duration = " + duration)

        Log.d("TTT", "onHandleIntent")


        val success = MixTest.prepareFile(duration, rootSd + "/" + fileName)

        if (success.not()) {
            building = false
            return ""
        }

        val songs = listOf<String>(rootSd + "/23.mp3", rootSd + "/13.mp3")

        var startEventIndex = 0

        var mainFileTime: Long = 0
        var startSecondaryFileTime: Long = 0
        var endSecondaryFileTime: Long = 0
        var secondaryFileVolume = 50
        var secondFileLocation: String

        while (building) {

            //Get important information before getting data from primary and secondary file
            mainFileTime = 0L
            startSecondaryFileTime = 0L
            endSecondaryFileTime = 30193L
            secondaryFileVolume = 50
            secondFileLocation = songs.get(startEventIndex)
            Log.d("TTT", "secondFileLocation = " + secondFileLocation)
            Log.d("TTT", "filename = " + fileName)


            try {
                mixTest.mixFiles(rootSd + "/" + fileName, mainFileTime, secondFileLocation, startSecondaryFileTime, endSecondaryFileTime, secondaryFileVolume)
            } catch (e: FileNotFoundException) {
                // TODO Auto-generated catch block
                e.printStackTrace()
            } catch (e: IOException) {
                // TODO Auto-generated catch block
                e.printStackTrace()
            }

            startEventIndex++
            if (startEventIndex >= songs.size) {
                building = false
                break
            }
        }
        try {
            Thread.sleep(200)
        } catch (e: InterruptedException) {
            // TODO Auto-generated catch block
            e.printStackTrace()
        }

        return ""
    }
}