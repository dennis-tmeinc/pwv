package com.tme_inc.pwv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.WindowManager;

import static android.content.Context.WINDOW_SERVICE;

/**
 * Created by dennis on 2/20/15.
 */
public class PwBootReceiver  extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Intent i;
        i = new Intent(context, PwvService.class);
        context.startService(i);

        SharedPreferences prefs = context.getSharedPreferences("pwv", 0);
        boolean autoStart = prefs.getBoolean("AutoStart", false) ;

        if(autoStart) {
            i = new Intent(context, MainActivity.class);
            i.setAction(Intent.ACTION_MAIN)
             .addCategory(Intent.CATEGORY_LAUNCHER)
             .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);
        }
    }
}