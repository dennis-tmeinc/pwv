package com.tme_inc.pwv;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.provider.Settings;

//import com.sun.jna.Function;
//import com.sun.jna.Native;
//import com.sun.jna.Platform;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Random;

/**
 * Created by dennis on 06/07/15.
 */
public class pwvApp extends Application {

    public static Context appCtx ;

    static {
        // jna testing,
        // loading Native Interface

        /*
        if( Platform.isAndroid() ) {
            // System.setProperty("jna.boot.library.name", "jna");
            int zygotePid = Function.getFunction("c", "getppid").invokeInt(null);
            channel = zygotePid ;
        }
        */

    }

    @Override
    public void onCreate() {
        super.onCreate();

        appCtx = getApplicationContext() ;

        SharedPreferences prefs = appCtx.getSharedPreferences("pwv", 0);
        SharedPreferences.Editor prefEdit = prefs.edit();

        if( ! prefs.contains( "officerId" ) ) {
            prefEdit.putString("officerId", "00001");
        }
        if( ! prefs.contains( "dvrPort" ) ) {
            prefEdit.putInt("dvrPort", 15114);
        }
        if( ! prefs.contains( "AutoStart" ) ) {
            prefEdit.putBoolean("AutoStart", true);
        }
        if( ! prefs.contains( "autoCheckUpdate" ) ) {
            prefEdit.putBoolean("autoCheckUpdate", true);
        }
        if( ! prefs.contains( "autoCheckInterval" ) ) {
            prefEdit.putString("autoCheckInterval", "24");
        }

        if( ! prefs.contains( "aid" ) ) {
            String aid = Settings.Secure.getString(getContentResolver(),
                    Settings.Secure.ANDROID_ID);
            if( aid == null || aid.length()<=2) {
                String code="0123456789abcdefghijklmnopqrstuvwxyz" ;
                Random random = new Random();
                char [] rb = new char [16] ;
                int i;
                for( i=0; i<rb.length; i++ ) {
                    rb[i] = code.charAt(random.nextInt(code.length()));
                }
                aid = new String(rb);
            }
            prefEdit.putString("aid", aid);
        }

        prefEdit.commit() ;

        File ffpath = appCtx.getFileStreamPath("ffmpeg");
        if (!ffpath.exists() || !ffpath.canExecute()) {
            byte[] ff = readResFile(R.raw.ffmpeg);
            if (ff.length > 1000) {
                try {
                    FileOutputStream fos = new FileOutputStream( ffpath ) ;
                    fos.write(ff);
                    fos.close();
                    ffpath.setExecutable(true);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                }
            }
        }

    }

    // Read raw resource into byte array
    static public byte[] readResFile(int resourceId)
    {
        InputStream is = null;
        byte[] raw = {};
        Resources res = appCtx.getResources();
        try {
            is = res.openRawResource(resourceId);
            raw = new byte[is.available()];
            is.read(raw);
        }
        catch (IOException e) {
            raw = new byte[0];
        }
        finally {
            try {
                is.close();
            }
            catch (IOException e) {
            }
        }
        return raw;
    }

    // Read raw resource into byte array
    static public byte[] readFile( String filename )
    {
        try {
            FileInputStream fs = appCtx.openFileInput(filename);
            int l = fs.available();
            l+=8000 ;;
            byte [] data = new byte [l] ;
            l = fs.read(data) ;
            if( l>0 ) {
                return Arrays.copyOf(data, l);
            }
            fs.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null ;
    }

    static public void saveFile( String filename, byte [] data )
    {
        try {
            FileOutputStream fs = appCtx.openFileOutput ( filename, 0 );
            fs.write(data, 0, data.length);
            fs.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}
