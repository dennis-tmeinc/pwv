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

    boolean mRunning = false ;
    Thread mThread ;

    public PWLiveStream( int channel ) {
        super(channel);
    }

    @Override
    public void start() {
        mRunning = true ;
        mMaxQueue = 50 ;
        mThread = new Thread( new Runnable(){
            @Override
            public void run() {
                LiveThread();
            }
        });
        mThread.start();
    }

    @Override
    public void release() {
        super.release();
        mRunning = false ;
        if( mThread!=null && mThread.isAlive()) {
            mThread.interrupt();
        }
        mThread = null ;
    }

    @Override
    public boolean isRunning() { return mRunning; }

    private void LiveThread() {
        DvrClient dvrClient = new DvrClient();

        while (mRunning) {
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
                    byte[] buffer = null ;
                    DvrClient.Ans ans = new DvrClient.Ans();

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
                    dvrClient.sendReq(14,mChannel,0);
                    if( dvrClient.recvAns(ans) && ans.size>0 && ans.code == 11 ) {
                        buffer = dvrClient.recv(ans.size);
                        if( buffer!=null ) {
                            ByteBuffer bb = ByteBuffer.wrap(buffer);
                            bb.order(ByteOrder.LITTLE_ENDIAN);
                            if( bb.getInt(0)!=0 )
                                mRes = bb.getInt( 68 ) ;
                            if( digester != null )
                                digester.update(buffer);
                        }
                    }

                    // get Channel Info
                    //struct channel_info {
                    //    int Enable ;
                    //    int Resolution ;
                    //    char CameraName[64] ;
                    //} ;
                    //  send REQCHANNELINFO
                    dvrClient.sendReq(4,0,0);
                    if( dvrClient.recvAns(ans) && ans.size>0 && ans.code == 7 ) {
                        buffer = dvrClient.recv(ans.size);
                        totalChannels = ans.data;
                        if( totalChannels>0 ) {
                            if( digester != null )
                                digester.update(buffer);
                            mChannelNames = new String [totalChannels];
                            synchronized (this) {
                                for (int i = 0; i < totalChannels; i++) {
                                    mChannelNames[i] = new String(buffer, 72 * i + 8, 64, "UTF-8").split("\0")[0];
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
                    dvrClient.sendReq(218, 0, 0);
                    if( dvrClient.recvAns(ans) && ans.size>0 ) {
                        buffer = dvrClient.recv(ans.size);
                        if( ans.code == 211 && ans.size>=28 ) {         // ANS2TIME
                            ByteBuffer bb = ByteBuffer.wrap(buffer);
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
                    }

                    // send REQOPENLIVE packet
                    // struct dvr_req {
                    //      int reqcode;
                    //      int data;
                    //      int reqsize;
                    //  };
                    //  send REQOPENLIVE
                    dvrClient.sendReq(215,mChannel,0);

                    while (mRunning && dvrClient.isConnected()) {
                        // read dvr ans header
                        if ( mRunning && dvrClient.recvAns(ans) && ans.size >= 0 && ans.code > 0 && ans.code < 500) {
                            if( ans.size > 0  ) {
                                buffer = dvrClient.recv(ans.size);
                                if (buffer != null) {
                                    if( ans.code == 202 ) {                           // ANSSTREAMDATA
                                        if( ans.data == 10 && ans.size == 40 ) {      // FRAMETYPE_264FILEHEADER
                                            setheader(buffer);
                                            onHeader( ByteBuffer.wrap(buffer) );
                                        }
                                        else {
                                            onReceiveFrame(ByteBuffer.wrap(buffer));
                                        }
                                    }
                                }
                            }
                        }
                        else {
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

}
