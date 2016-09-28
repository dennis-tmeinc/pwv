package com.tme_inc.pwv;

import android.app.Activity;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Calendar;
import java.util.GregorianCalendar;


/**
 * Created by dennis on 1/21/15.
 */
public class PWPlaybackStream extends PWStream {

    public String hashId ;

    private DvrClient mClient ;
    private int mPlaybackHandle;    // DVR handle for playback

    private int[] mDayList = {};

    private boolean m_eos = false ;

    private PlaybackThread mThread;
    private Handler mPWHandler;
    private Handler mUIHandler;

    public PWPlaybackStream(int channel, Handler uiHandler) {
        super(channel);

        mUIHandler = uiHandler;
        mClient = new DvrClient();

        // start Handler thread
        mThread = new PlaybackThread();
        mThread.start();
    }

    @Override
    public void start() {
        if(isRunning()) {
            mPWHandler.sendEmptyMessage(PWMessage.MSG_PW_GETFRAME);
        }
    }

    @Override
    public void release() {
        super.release();
        if (isRunning()) {
            mPWHandler.sendEmptyMessage(PWMessage.MSG_PW_QUIT);     // send quit message
        }
        mUIHandler = null;
    }

    @Override
    public boolean isRunning() { return mPWHandler != null ; }

    // seek to specified time, time in miliiseconds
    public void seek(long time){
        if(isRunning()) {
            int sdate, stime;

            if( time<10000 && mDayList.length>0 ) {
                // seek to last date contain video
                sdate = mDayList[mDayList.length-1] ;
                stime = 0 ;
            }
            else {
                Calendar calendar = Calendar.getInstance();
                calendar.clear();
                calendar.setTimeInMillis(time);
                sdate = calendar.get(Calendar.YEAR) * 10000 +
                        (calendar.get(Calendar.MONTH) - Calendar.JANUARY + 1) * 100 +
                        calendar.get(Calendar.DATE);
                stime = calendar.get(Calendar.HOUR_OF_DAY) * 10000 +
                        calendar.get(Calendar.MINUTE) * 100 +
                        calendar.get(Calendar.SECOND);
            }

            mPWHandler.obtainMessage(PWMessage.MSG_PW_SEEK, sdate, stime).sendToTarget();
        }

    }

    public int[] getDayList() {
        return mDayList;
    }

    // date in yyyyMMDD
    public void getClipList( int date ) {
        if(isRunning()) {
            mPWHandler.obtainMessage( PWMessage.MSG_PW_GETCLIPLIST, date, 0 ).sendToTarget();
        }
    }

    class PlaybackThread extends HandlerThread {
        public PlaybackThread(){
            super("PWPlayer");
        }

        @Override
        protected void onLooperPrepared() {
            super.onLooperPrepared();

            mPWHandler = new Handler(){
                @Override
                public void handleMessage(Message msg) {
                    super.handleMessage(msg);
                    handlePWMessage(msg);
                }
            };
            mPWHandler.sendEmptyMessage(PWMessage.MSG_PW_CONNECT);
        }
    }

    // handle msg on PW Thread
    void handlePWMessage(Message msg) {
        switch (msg.what) {
            case PWMessage.MSG_PW_QUIT :  // quit thread
                if( connect() && mPlaybackHandle!=0  ) {
                    // to close stream handle before close connection
                    DvrClient.Ans ans = new DvrClient.Ans();

                    //  send REQSTREAMCLOSE (203)
                    mClient.sendReq(203, mPlaybackHandle, 0);
                    mClient.recvAns(ans) ;
                }

                if( mPWHandler!=null ) {
                    mPWHandler = null ;
                }
                if( mThread!=null ) {
                    mThread.quit();
                    mThread = null ;
                }
                mClient.close();
                break;

            case PWMessage.MSG_PW_CONNECT:
                if( connect() ) {
                    MessageDigest digester ;
                    try {
                        digester = MessageDigest.getInstance("MD5");
                    } catch (NoSuchAlgorithmException e) {
                        e.printStackTrace();
                        digester = null ;
                    }

                    byte[] buffer = null ;
                    DvrClient.Ans ans = new DvrClient.Ans();

                    // get Channel setup
                    // struct  DvrChannel_attr {
                    //  int         Enable ;
                    //  char        CameraName[64] ;
                    //  int         Resolution;	// 0: CIF, 1: 2CIF, 2:DCIF, 3:4CIF, 4:QCIF
                    //    ...
                    // }
                    //
                    //  send REQGETCHANNELSETUP
                    mClient.sendReq(14,mChannel,0);
                    if( mClient.recvAns(ans) && ans.size>0 && ans.code == 11 ) {
                        buffer = mClient.recv(ans.size);
                        if( buffer!=null ) {
                            ByteBuffer bb = ByteBuffer.wrap(buffer);
                            bb.order(ByteOrder.LITTLE_ENDIAN);
                            if( bb.getInt(0)!=0 ) {
                                mRes = bb.getInt(68);
                            }
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
                    mClient.sendReq(4,0,0);
                    if( mClient.recvAns(ans) && ans.size>0 && ans.code == 7 ) {
                        buffer = mClient.recv(ans.size);
                        totalChannels = ans.data;
                        if( totalChannels>0 ) {
                            if( digester != null )
                                digester.update(buffer);
                            mChannelNames = new String [totalChannels];
                            for(int i=0; i<totalChannels; i++) {
                                mChannelNames[i] = new String(buffer, 72 * i + 8, 64).split("\0")[0];
                            }
                        }
                    }

                    if( mPlaybackHandle!=0 ) {
                        // get daylist

                        //  REQSTREAMDAYLIST   (214)
                        mClient.sendReq(214,mPlaybackHandle,0);
                        if( mClient.recvAns(ans) && ans.code == 207 ) {    //ANSSTREAMDAYLIST : 207
                            if (ans.size > 0) {
                                buffer = mClient.recv(ans.size);
                                ByteBuffer b = ByteBuffer.wrap(buffer);
                                b.order(ByteOrder.LITTLE_ENDIAN);
                                int s = ans.size/4 ;
                                mDayList = new int[s] ;
                                for( int i=0; i<s; i++) {
                                    mDayList[i] = b.getInt(i*4);
                                }
                            }
                        }

                    }

                    hashId = "C"+mChannel+"." ;
                    if( digester!= null ) {
                        byte[] dg = digester.digest();
                        for ( int j = 0; j < dg.length; j++ ) {
                            hashId += String.format("%02x", dg[j]);
                        }
                    }

                    if( mUIHandler != null ) {
                        mUIHandler.obtainMessage( PWMessage.MSG_PW_CONNECT ).sendToTarget();
                    }

                }
                break;

            case PWMessage.MSG_PW_GETFRAME :
                if( connect() && mPlaybackHandle!=0  ) {
                    if( mVideoFrameQueue.size()<100 ) {

                        // REQ2STREAMGETDATAEX (233)
                        mClient.sendReq(233, mPlaybackHandle, 0);
                        DvrClient.Ans ans = new DvrClient.Ans();
                        if (mClient.recvAns(ans) && ans.code == 218 && ans.size>32 ) {    //ANS2STREAMDATAEX : 218
                            byte[] buffer = mClient.recv(ans.size);
                            if (buffer != null ) {
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

                                bb.order(ByteOrder.BIG_ENDIAN);
                                bb.position(32) ;
                                onReceiveFrame(bb);
                            }
                        }
                        else {
                            // end of stream?
                            m_eos = true ;
                            break ;
                        }
                        mPWHandler.sendEmptyMessage(PWMessage.MSG_PW_GETFRAME);
                    }
                    else {
                        if( !mPWHandler.hasMessages(PWMessage.MSG_PW_GETFRAME)) {
                            mPWHandler.sendEmptyMessageDelayed(PWMessage.MSG_PW_GETFRAME, 1000);
                        }
                    }
                }
                else {
                    mPWHandler.sendEmptyMessageDelayed(PWMessage.MSG_PW_CONNECT, 10000);
                }
                break;

            case PWMessage.MSG_PW_SEEK :
                if( connect() && mPlaybackHandle!=0  ) {
                    // clear all queued frames
                    clearQueue();

                    DvrClient.Ans ans = new DvrClient.Ans();

                    int date = msg.arg1 ;
                    int time = msg.arg2 ;

                    // REQSTREAMSEEK (204)
                    /*  struct dvrtime {
                            int year;
                            int month;
                            int day;
                            int hour;
                            int minute;
                            int second;
                            int milliseconds;
                            int tz;
                        };
                    */
                    ByteBuffer dvrtime = ByteBuffer.allocate(32) ;
                    dvrtime.order(ByteOrder.LITTLE_ENDIAN) ;
                    dvrtime.putInt(0, date / 10000) ;
                    dvrtime.putInt(4, date%10000/100) ;
                    dvrtime.putInt(8, date%100) ;
                    dvrtime.putInt(12, time/10000) ;
                    dvrtime.putInt(16, time%10000/100) ;
                    dvrtime.putInt(20, time%100) ;
                    dvrtime.putInt(24, 0 ) ;
                    dvrtime.putInt(28, 0 ) ;

                    mClient.sendReq(204, mPlaybackHandle, 32);
                    mClient.send(dvrtime.array(), 0, 32) ;

                    m_eos = false ;
                    if (mClient.recvAns(ans) && ans.code == 2) {    // ANSOK : 2
                        if (ans.size >=40 ) {
                            // treat file header as a frame
                            onReceiveFrame(ByteBuffer.wrap(mClient.recv(ans.size)));
                        }
                    }

                    if( mUIHandler != null ) {
                        mUIHandler.obtainMessage(PWMessage.MSG_PW_SEEK).sendToTarget();
                    }
                }
                break;

            case PWMessage.MSG_PW_GETCLIPLIST:
                if( connect() && mPlaybackHandle!=0  ) {
                    DvrClient.Ans ans = new DvrClient.Ans();
                    byte[] buffer = null ;

                    int [] clipInfo = {};
                    int [] lockInfo = {};

                    // REQSTREAMDAYINFO (210)
                    // Data: struct dvrtime {
                    //    int year;
                    //    int month;
                    //    int day;
                    //    int hour;
                    //    int minute;
                    //    int second;
                    //    int milliseconds;
                    //    int tz;
                    //};
                    ByteBuffer dvrtime = ByteBuffer.allocate(32);
                    dvrtime.order(ByteOrder.LITTLE_ENDIAN);
                    dvrtime.putInt(0, msg.arg1/10000);           // year
                    dvrtime.putInt(4, (msg.arg1/100)%100 );      // month
                    dvrtime.putInt(8, msg.arg1%100 );            // day
                    mClient.sendReq(210,mPlaybackHandle,32);
                    mClient.send(dvrtime.array(),0,32);
                    if( mClient.recvAns(ans) && ans.code == 204 ) {    //ANSSTREAMDAYINFO : 204
                        // struct dayinfoitem {
                        //    int ontime  ;		// seconds of the day
                        //    int offtime ;		// seconds of the day
                        // } ;
                        if (ans.size > 0) {
                            buffer = mClient.recv(ans.size);
                            ByteBuffer bb = ByteBuffer.wrap(buffer);
                            bb.order(ByteOrder.LITTLE_ENDIAN);
                            int s = ans.size/8 ;
                            if( s>0 ) {
                                clipInfo = new int [s*2] ;
                                for( int i = 0; i<s*2; i+=2 ) {
                                    clipInfo[i] = bb.getInt(i*4);
                                    clipInfo[i+1] = bb.getInt((i+1)*4);
                                }
                            }
                        }
                    }

                    if( mUIHandler != null ) {
                        mUIHandler.obtainMessage(PWMessage.MSG_PW_GETCLIPLIST, msg.arg1, 1, (Object)clipInfo).sendToTarget();
                    }

                    // REQLOCKINFO   (212)
                    mClient.sendReq(212,mPlaybackHandle,32);
                    mClient.send(dvrtime.array(),0,32);
                    if( mClient.recvAns(ans) && ans.code == 204 ) {    //ANSSTREAMDAYINFO : 204
                        // struct dayinfoitem {
                        //    int ontime  ;		// seconds of the day
                        //    int offtime ;		// seconds of the day
                        // } ;
                        if (ans.size > 0) {
                            buffer = mClient.recv(ans.size);
                            ByteBuffer bb = ByteBuffer.wrap(buffer);
                            bb.order(ByteOrder.LITTLE_ENDIAN);
                            int s = ans.size/8 ;
                            if( s>0 ) {
                                lockInfo = new int [s*2] ;
                                for( int i = 0; i<s*2; i+=2 ) {
                                    lockInfo[i] = bb.getInt(i*4);
                                    lockInfo[i+1] = bb.getInt((i+1)*4);
                                }
                            }
                        }
                    }

                    if( mUIHandler != null ) {
                        mUIHandler.obtainMessage(PWMessage.MSG_PW_GETCLIPLIST, msg.arg1 , 2, (Object)lockInfo).sendToTarget();
                    }
                }
                else {
                    mPWHandler.sendEmptyMessage(PWMessage.MSG_PW_CONNECT);
                }
                break;

        }
    }

    private boolean connect(){
        if( !mClient.isConnected() || mPlaybackHandle==0 ) {
            mClient.close();
            mClient.connect();
            mPlaybackHandle = 0 ; // reset handle

            if( mClient.isConnected() ) {
                byte [] buffer ;
                DvrClient.Ans ans = new DvrClient.Ans();

                // verify tvs key
                checkTvsKey(mClient);

                //  REQSTREAMOPEN   (201)
                mClient.sendReq(201, mChannel, 0);
                if (mClient.recvAns(ans) && ans.code == 201) {    //ANSSTREAMOPEN : 201
                    if (ans.size > 0) {
                        buffer = mClient.recv(ans.size);            // this is 40 bytes file header, not used
                        if( ans.size >=40 ) {
                            onReceiveFrame( ByteBuffer.wrap(buffer) ) ;
                        }
                    }
                    mPlaybackHandle = ans.data;
                }
            }
        }
        return mClient.isConnected() ;
    }

    public boolean isEndOfStream() {
        if( m_eos && !videoAvailable() ) {
            return true ;
        }
        return false ;
    }


}
