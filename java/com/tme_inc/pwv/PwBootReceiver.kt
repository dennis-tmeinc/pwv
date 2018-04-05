package com.tme_inc.pwv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.view.WindowManager

import android.content.Context.WINDOW_SERVICE

/**
 * Created by dennis on 2/20/15.
 */
class PwBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        var i = Intent(context, PwvService::class.java)
        context.startService(i)

        val prefs = context.getSharedPreferences("pwv", 0)
        val autoStart = prefs.getBoolean("AutoStart", false)

        if (autoStart) {
            i = Intent(context, MainActivity::class.java)
            i.setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i)
        }
    }
}