package com.tme_inc.pwv;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.media.MediaCodec;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Network stream from DVR for live view
 * * Created by dennis on 12/22/14.
 */
public class PWLiveStream extends PWStream {

    public String hashId ;
    private Thread mThread ;

    public PWLiveStream( int channel ) {
        super(channel);
    }

    class PWLiveThread extends Thread {
        boolean mRunning  ;

        public void run() {
            mRunning = true ;

            DvrClient dvrClient = new DvrClient();

            while (mRunning && !isInterrupted() ) {
                if( !dvrClient.connect() ) {
                    try {
                        Thread.sleep(10000);
                    }
                    catch (InterruptedException e){
                        mRunning=false ;
                    }
                    continue;
                }
                else {
                    try {
                        DvrClient.Ans ans ;

                        MessageDigest digester ;
                        try {
                            digester = MessageDigest.getInstance("MD5");
                        } catch (NoSuchAlgorithmException e) {
                            e.printStackTrace();
                            digester = null ;
                        }

                        // verify tvs key
                        checkTvsKey(dvrClient);

                        // get Channel setup
                        // struct  DvrChannel_attr {
                        //  int         Enable ;
                        //  char        CameraName[64] ;
                        //  int         Resolution;	// 0: CIF, 1: 2CIF, 2:DCIF, 3:4CIF, 4:QCIF
                        //    ...
                        // }
                        //
                        //  send REQGETCHANNELSETUP
                        ans = dvrClient.request(14,mChannel);
                        if( ans.databuf!=null && ans.code == 11 ) {
                            ByteBuffer bb = ByteBuffer.wrap(ans.databuf);
                            bb.order(ByteOrder.LITTLE_ENDIAN);
                            if( bb.getInt(0)!=0 )
                                mRes = bb.getInt( 68 ) ;
                            if( digester != null )
                                digester.update(ans.databuf);
                        }

                        totalChannels = 8 ;     // default to 8 channels ^^
                        // get Channel Info
                        //struct channel_info {
                        //    int Enable ;
                        //    int Resolution ;
                        //    char CameraName[64] ;
                        //} ;
                        //  send REQCHANNELINFO
                        ans = dvrClient.request(4);
                        if( ans.code == 7 ) {
                            totalChannels = ans.data;
                            if (ans.databuf != null) {
                                if (digester != null)
                                    digester.update(ans.databuf);
                                mChannelNames = new String[totalChannels];
                                synchronized (this) {
                                    for (int i = 0; i < totalChannels; i++) {
                                        mChannelNames[i] = new String(ans.databuf, 72 * i + 8, 64, "UTF-8").split("\0")[0];
                                    }
                                }
                            }
                        }

                        if( mRes<0 || mChannel<0 || mChannel>=totalChannels ) {
                            // failed on such channel
                            mRunning=false ;
                            continue ;
                        }

                        hashId = "C"+mChannel+"." ;
                        if( digester!= null ) {
                            byte[] dg = digester.digest();
                            for ( int j = 0; j < dg.length; j++ ) {
                                hashId += String.format("%02x", dg[j]);
                            }
                        }

                        // REQ2GETLOCALTIME
                        ans = dvrClient.request(218);
                        if( ans.code == 211 && ans.databuf!=null && ans.databuf.length>=28 ) {         // ANS2TIME
                            ByteBuffer bb = ByteBuffer.wrap(ans.databuf);
                            bb.order(ByteOrder.LITTLE_ENDIAN);

                            // response contain header of structure dvrtime
                            Calendar cal =  new GregorianCalendar(
                                    bb.getInt(0),
                                    bb.getInt(4)-1+Calendar.JANUARY,
                                    bb.getInt(8),
                                    bb.getInt(12),
                                    bb.getInt(16),
                                    bb.getInt(20)
                            );
                            resetTimestamp( cal.getTimeInMillis() + bb.getInt(24) ) ;
                        }

                        // send REQOPENLIVE packet
                        // struct dvr_req {
                        //      int reqcode;
                        //      int data;
                        //      int reqsize;
                        //  };
                        //  send REQOPENLIVE
                        ans = dvrClient.request(215,mChannel);

                        if( ans.code == 201 )
                            while (mRunning && dvrClient.isConnected() && !isInterrupted()) {
                                // read dvr ans header
                                ans = dvrClient.recvAns();
                                if( ans.code == 202 ) {                           // ANSSTREAMDATA
                                    if( ans.data == 10 && ans.size == 40 ) {      // FRAMETYPE_264FILEHEADER
                                        setheader(ans.databuf);
                                        onHeader( ByteBuffer.wrap(ans.databuf) );
                                    }
                                    else {
                                        onReceiveFrame(ByteBuffer.wrap(ans.databuf));
                                    }
                                }
                                else {                // ANSSTREAMOPEN
                                    break;
                                }
                            }

                        dvrClient.close();

                    } catch (IOException e) {
                        mRunning = false ;
                    }
                }
            }
            dvrClient.close();
        }

        @Override
        public void interrupt() {
            mRunning = false ;
            super.interrupt();
        }
    }

    @Override
    public void start() {
        super.start();
        mMaxQueue = 50 ;
        mThread = new PWLiveThread();
        mThread.start();
    }

    @Override
    public boolean isRunning() {
        return mThread!=null && mThread.isAlive() ;
    }

    @Override
    public void release() {
        super.release();
        if( isRunning() ) {
            mThread.interrupt();
        }
        mThread = null ;
    }


}
