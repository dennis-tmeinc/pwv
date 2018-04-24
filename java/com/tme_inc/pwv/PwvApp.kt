package com.tme_inc.pwv

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.provider.Settings

//import com.sun.jna.Function;
//import com.sun.jna.Native;
//import com.sun.jna.Platform;

import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.Arrays
import java.util.Random


internal var appCtx: Context? = null

// Read raw resource into byte array
internal fun readResFile(resourceId: Int): ByteArray {
    var `is`: InputStream? = null
    var raw : ByteArray
    val res = appCtx!!.resources
    try {
        `is` = res.openRawResource(resourceId)
        raw = ByteArray(`is`!!.available())
        `is`.read(raw)
    } catch (e: IOException) {
        raw = byteArrayOf()
    } finally {
        try {
            `is`!!.close()
        } catch (e: IOException) {
        }

    }
    return raw
}

// Read raw resource into byte array
internal fun readFile(filename: String): ByteArray? {
    try {
        val fs = appCtx!!.openFileInput(filename)
        var l = fs.available()
        l += 8000
        val data = ByteArray(l)
        l = fs.read(data)
        if (l > 0) {
            return Arrays.copyOf(data, l)
        }
        fs.close()
    } catch (e: FileNotFoundException) {
        // e.printStackTrace();
    } catch (e: IOException) {
        // e.printStackTrace();
    }

    return null
}

internal fun saveFile(filename: String, data: ByteArray) {
    try {
        val fs = appCtx!!.openFileOutput(filename, 0)
        fs.write(data, 0, data.size)
        fs.close()
    } catch (e: FileNotFoundException) {
        e.printStackTrace()
    } catch (e: IOException) {
        e.printStackTrace()
    }

}

/**
 * Created by dennis on 06/07/15.
 */
class PwvApp : Application() {

    override fun onCreate() {
        super.onCreate()

        appCtx = applicationContext

        val prefs = applicationContext.getSharedPreferences("pwv", 0)
        val prefEdit = prefs.edit()

        if (!prefs.contains("officerId")) {
            prefEdit.putString("officerId", "00001")
        }
        if (!prefs.contains("dvrPort")) {
            prefEdit.putInt("dvrPort", 15114)
        }
        if (!prefs.contains("AutoStart")) {
            prefEdit.putBoolean("AutoStart", true)
        }
        if (!prefs.contains("autoCheckUpdate")) {
            prefEdit.putBoolean("autoCheckUpdate", true)
        }
        if (!prefs.contains("autoCheckInterval")) {
            prefEdit.putString("autoCheckInterval", "24")
        }

        if (!prefs.contains("aid")) {
            var aid: String? = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ANDROID_ID
            )
            if (aid == null || aid.length <= 2) {
                val code = "0123456789abcdefghijklmnopqrstuvwxyz"
                val random = Random()
                val rb = CharArray(16)
                var i: Int
                i = 0
                while (i < rb.size) {
                    rb[i] = code[random.nextInt(code.length)]
                    i++
                }
                aid = String(rb)
            }
            prefEdit.putString("aid", aid)
        }

        prefEdit.apply()

        val ffpath = applicationContext.getFileStreamPath("ffmpeg")
        if (!ffpath.exists() || !ffpath.canExecute()) {
            val ff = readResFile(R.raw.ffmpeg)
            if (ff.size > 1000) {
                try {
                    val fos = FileOutputStream(ffpath)
                    fos.write(ff)
                    fos.close()
                    ffpath.setExecutable(true)
                } catch (e: FileNotFoundException) {
                    e.printStackTrace()
                } catch (e: IOException) {
                }

            }
        }

    }

}
