package com.mshurpo.testview.mixer

import android.content.Context
import android.os.AsyncTask
import android.os.Environment
import android.util.Log
import android.widget.Toast
import com.googlecode.mp4parser.BasicContainer
import com.googlecode.mp4parser.authoring.Movie
import com.googlecode.mp4parser.authoring.Track
import com.googlecode.mp4parser.authoring.builder.DefaultMp4Builder
import com.googlecode.mp4parser.authoring.container.mp4.MovieCreator
import com.googlecode.mp4parser.authoring.tracks.AppendTrack
import java.io.*
import java.util.*


/**
 * Created by maksimsurpo on 5/29/17.
 */
class MergeVideos(val context: Context) : AsyncTask<Void, Int, String>() {


    override fun onPreExecute() {
        super.onPreExecute()
        Log.d("TTT", "${System.currentTimeMillis()}")
    }

    override fun doInBackground(vararg p0: Void?): String {
          val rootSd = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString()
              val file = File(rootSd + "/MyCameraApp")
              val files = file.listFiles().asList()
              val newFile = File(rootSd + "/MyCameraApp/1.mp4")
              merge(files, newFile)
        return newFile.absolutePath
    }

    fun merge(parts: List<File>, outFile: File) {
        try {
            var inMovies = arrayOfNulls<Movie>(parts.size)

            parts.forEachIndexed { i, file ->
                inMovies[i] = MovieCreator.build(file.path)
            }

            var videoTracks: List<Track> = LinkedList<Track>()
            var audioTracks: List<Track> = LinkedList<Track>()

            inMovies.forEach {
                it?.tracks?.forEach {
                    if (it.handler == "soun") {
                        audioTracks = audioTracks.plus(it)
                    }
                    if (it.handler == "vide") {
                        videoTracks = videoTracks.plus(it)
                    }
                }
            }

            val finalMovie = Movie()

            if (audioTracks.isNotEmpty()) {
                finalMovie.addTrack(AppendTrack(*audioTracks.toTypedArray()))
            }
            if (videoTracks.isNotEmpty()) {
                finalMovie.addTrack(AppendTrack(*videoTracks.toTypedArray()))
            }

            val fos = FileOutputStream(outFile)
            val container = DefaultMp4Builder().build(finalMovie) as BasicContainer
            container.writeContainer(fos.getChannel())
        } catch (e: IOException) {
            Log.e("TTT", "Merge failed", e)
        }

    }

    override fun onPostExecute(result: String?) {
        super.onPostExecute(result)

        Toast.makeText(context, "finish", Toast.LENGTH_SHORT).show()
        Log.d("TTT", "${System.currentTimeMillis()}")
    }
}
