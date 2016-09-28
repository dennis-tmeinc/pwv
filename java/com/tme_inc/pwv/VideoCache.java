package com.tme_inc.pwv;

import android.content.Context;
import android.content.res.Resources;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;

/**
 * Created by dennis on 21/10/15.
 */
public class VideoCache {
    private String m_channelId ;

    private FileOutputStream m_sampleStream = null ;

    public VideoCache( String chId ){
        m_channelId = chId ;
    }

    private File sampleFile() {
        File cacheDir = pwvApp.appCtx.getExternalCacheDir();
        if( cacheDir==null ) {
            cacheDir = pwvApp.appCtx.getCacheDir();
        }
        File sfDir = new File( cacheDir, "sf") ;
        if( !sfDir.isDirectory() ) {
            sfDir.mkdirs();
        }
        return new File( sfDir, m_channelId );
    }

    public String sampleMP4() {
        File sample = sampleFile();
        String sn = sample.getAbsolutePath();
        return sn + ".mp4" ;
    }

    public void sampleFileWrite( byte[] frameBuffer, int offset, int len) {
        if( m_sampleStream == null ) {
            File sample = sampleFile() ;

            try {
                m_sampleStream = new FileOutputStream( sample );
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                return ;
            }
        }

        try {
            m_sampleStream.write(frameBuffer, offset, len);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public boolean sampleFileClose() {
        if (m_sampleStream != null) {
            try {
                m_sampleStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            m_sampleStream = null;

            File ffpath = pwvApp.appCtx.getFileStreamPath("ff");
            if (!ffpath.exists() || !ffpath.canExecute()) {
                byte[] ff = pwvApp.readResFile(R.raw.ffmpeg);
                if (ff.length > 1000) {
                    try {
                        FileOutputStream fos = pwvApp.appCtx.openFileOutput("ff", Context.MODE_PRIVATE);
                        fos.write(ff);
                        fos.close();
                        ffpath.setExecutable(true);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                    }
                }
            }
            File sample = sampleFile();
            String sn = sample.getAbsolutePath();

            try {
                String cmd = ffpath.getPath() + " -i " + sn + " -y -c copy " + sampleMP4() ;
                Runtime.getRuntime().exec(cmd);
                return true;
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
        return false;
    }

    public boolean sampleFileReady() {
        File mp4file = new File( sampleMP4() );
        if( mp4file.exists() && mp4file.length()>1000 ) {
            return true ;
        }
        return false;
    }

}
