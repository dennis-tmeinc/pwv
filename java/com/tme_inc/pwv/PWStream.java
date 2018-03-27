package com.tme_inc.pwv;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.media.MediaCodec;
import android.os.SystemClock;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.Queue;

import static android.R.attr.text;
import static java.util.Arrays.*;

/**
 * Created by dennis on 1/21/15.
 */
public class PWStream {

    static private final String logTag="PWStream" ;

    protected Queue<MediaFrame> mVideoFrameQueue = new LinkedList<MediaFrame>();
    protected Queue<MediaFrame> mAudioFrameQueue = new LinkedList<MediaFrame>();
    protected Queue<MediaFrame> mTextFrameQueue = new LinkedList<MediaFrame>();

    protected int mRes = 0 ;                   // channel resolution
    protected int mMaxQueue = 1000 ;
    protected boolean mStarted ;

    protected MediaFrame x_textframe ;
    protected long refFramePts ;          // reference frame PTS, -1 = not available
    protected long refFrameTS ;           // reference frame TimeStamp
    protected long frameTS;               // last frame TimeStamp

    private boolean file_encrypted ;

    static private byte [] file_encrypt_RC4_table = new byte[1024];;
    static private int file_tag ;
    static private int file_tag_enc ;

    static {
        // initial file decryption table
        byte[] tvskey = pwvApp.readResFile(R.raw.tvskey_mf5000);
        resetheader( tvskey );
    }

    public short audio_codec = 1 ;
    public int   audio_samplerate = 8000;

    public int totalChannels = 0;               // total channel
    public int mChannel = 0 ;                   // current channel
    protected String [] mChannelNames ;

    public int devicetype ;
    public int video_height ;
    public int video_width ;

    public long active_timeMillis ;

    static protected void setheader(byte[] fileheader) {
        ByteBuffer bf = ByteBuffer.wrap(fileheader) ;
        file_tag = bf.getInt(0);

        // encrypted header tag
        byte [] fh =  Arrays.copyOf(fileheader, 4) ;
        PWRc4.RC4_block_crypt(fh, 0, 4, 0, file_encrypt_RC4_table);
        file_tag_enc = ByteBuffer.wrap(fh).getInt(0);
    }

    static protected void resetheader(  byte[] tvskey ) {
        // initial file decryption table
        PWRc4.RC4_crypt_table(file_encrypt_RC4_table, copyOfRange(tvskey, 684, 940));

        // default non encrypted header tag
        byte[] file_header = {0x46, 0x45, 0x4d, 0x54};
        setheader( file_header );
    }

    public PWStream( int channel ) {
        mChannel = channel ;
        totalChannels = 1 ;
        mChannelNames = null ;
        mRes = 0 ;
        video_width = 0 ;
        video_height = 0 ;
        file_encrypted = false ;
        mStarted = false ;
        refFrameTS = 0 ;
        frameTS = 0 ;
        refFramePts = 0 ;
        active_timeMillis = SystemClock.uptimeMillis();
    }

    public void start() {
        active_timeMillis = SystemClock.uptimeMillis();
    }

    public boolean isActive() {
        return (SystemClock.uptimeMillis() - active_timeMillis) < 30000;
    }

    public void release() {
        clearQueue();
    }

    public boolean isRunning() { return false; }

    public int  getResolution() {
        return mRes ;
    }

    public String getChannelName(int ch)
    {
        if( mChannelNames==null || ch>=mChannelNames.length ) {
            return "Camera "+(ch+1) ;
        }
        else {
            return mChannelNames[ch];
        }
    }

    public String getChannelName(){
        return getChannelName(mChannel);
    }

    protected boolean checkTvsKey( DvrClient connection ) {
        if( connection.isConnected() ) {
            DvrClient.Ans ans;

            byte[] tvskey = null ;

            tvskey = pwvApp.readFile( "tvskey" );
            if( tvskey!=null ) {
                // send REQCHECKKEY packet
                //          REQCHECKKEY, 303
                ans = connection.request(303, 0, tvskey);
                if( ans.code == 2 ) {       // ANSOK
                    resetheader(tvskey);
                    return true ;
                }
            }

            // check tvskey MF5000
            tvskey = pwvApp.readResFile(R.raw.tvskey_mf5000);

            // send REQCHECKKEY packet
            //          REQCHECKKEY, 303
            ans = connection.request(303, 0, tvskey);
            if( ans.code == 2 ) {       // ANSOK
                pwvApp.saveFile( "tvskey", tvskey );
                resetheader(tvskey);
                return true ;
            }

            // check tvskey MF5001
            tvskey = pwvApp.readResFile(R.raw.tvskey_mf5001);

            // send REQCHECKKEY packet
            //          REQCHECKKEY, 303
            ans = connection.request(303, 0, tvskey);
            if( ans.code == 2 ) {       // ANSOK
                pwvApp.saveFile( "tvskey", tvskey );
                resetheader(tvskey);
                return true ;
            }

            // check external tvskey
            File extkey = pwvApp.appCtx.getExternalFilesDir (null );
            if( extkey.isDirectory() ) {
                File[] keyfiles = extkey.listFiles(new FileFilter() {
                    @Override
                    public boolean accept(File pathname) {
                        return true;
                    }
                });
                for (File keyfile : keyfiles) {
                    try {
                        FileInputStream fi = new FileInputStream(keyfile);
                        int r = fi.read( tvskey ) ;

                        // send REQCHECKKEY packet
                        //          REQCHECKKEY, 303
                        ans = connection.request( 303, 0, tvskey, 0, r );
                        if (ans.code == 2) {       // ANSOK
                            pwvApp.saveFile("tvskey", tvskey);
                            resetheader(tvskey);
                            return true;
                        }

                        fi.close();

                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }
            }

        }
        return false;
    }

    // next frame will be set as reference frame
    protected void resetTimestamp( long Timestamp ) {
        refFrameTS = Timestamp ;
        frameTS = refFrameTS;
        refFramePts = 0 ;      // to refresh reference PTS
    }

    // return frame time stamp in milliseconds
    protected long timeStamp(ByteBuffer frame) {
        // get time stamp from video PES packet
        int pos = frame.position();
        long pts = ((long)frame.get(pos+9)&0x0e) << 29 ;
        pts |= (((long)frame.getShort(pos+10))&0x0fffe)<<14 ;
        pts |= (((long)frame.getShort(pos+12))&0x0fffe)>>1 ;
        pts /= 90 ;             // convert 90k hz counter to 1k hz

        if (refFramePts == 0 ) {     // renew ref Frame PTS
            refFramePts = pts;
        }

        return refFrameTS + (pts - refFramePts);
    }

    protected void onReceiveFrame( ByteBuffer frame){
        active_timeMillis = SystemClock.uptimeMillis();

        while( frame.remaining() >=40  ) {
            int pos = frame.position() ;
            int startcode = frame.getInt(pos);
            int startcodeMasked = startcode & 0xffffff00 ;
            int framelen = 0 ;

            // check packet types
            if (startcode == 0x000001c0) {
                framelen = addAudioFrame(frame);
            }
            else if (startcode == 0x000001e0) {
                framelen = addVideoFrame(frame);
            }
            else if ( startcode == 0x000001ba) {
                // ps packet header, always 20 bytes
                framelen = (frame.get(pos + 13) & 0x7) + 14 ;
            }
            else if ( startcodeMasked == 0x00000100) {
                // other PES packet, skip it  (0x000001bc)
                framelen = 6 + (((int) frame.getShort(pos + 4)) & 0x0ffff) ;
            }
            else if( startcodeMasked == 0x54585400) {   // 'TXT'
                // text
                framelen = addTextFrame(frame);
            }
            else if( startcodeMasked == 0x47505300 ) {   // 'GPS'
                frame.order(ByteOrder.LITTLE_ENDIAN);
                framelen = 8 + (int) frame.getShort( pos+6 );
                frame.order(ByteOrder.BIG_ENDIAN);
            }
            else if( startcode == file_tag )
            {
                // a stream header (not encryped)
                file_encrypted = false ;
                framelen = onHeader(frame);
            }
            else if( startcode == file_tag_enc )
            {
                // a stream header , encryped!
                file_encrypted = true ;
                framelen = onHeader(frame);
            }
            else {
                Log.d(logTag, "Unrecognized package");
                break;
            }
            if( framelen > 0  && framelen <= frame.remaining() ) {
                frame.position(pos + framelen);
            }
            else {
                break ;
            }
        }
    }

    protected synchronized void clearQueue() {
        mVideoFrameQueue.clear();
        mAudioFrameQueue.clear();
        mTextFrameQueue.clear();
        mStarted = false ;
        // resetTimestamp( 0 );
    }

    public synchronized MediaFrame peekVideoFrame(){
        return mVideoFrameQueue.peek();
    }

    public synchronized boolean videoAvailable() {
        return (peekVideoFrame()!=null) ;
    }

    public synchronized MediaFrame getVideoFrame(){
        return mVideoFrameQueue.poll();
    }

    public synchronized MediaFrame peekAudioFrame(){
        return mAudioFrameQueue.peek();
    }

    public synchronized boolean audioAvailable(){
        return (peekAudioFrame()!=null) ;
    }

    public synchronized MediaFrame getAudioFrame(){
        return mAudioFrameQueue.poll();
    }

    public synchronized MediaFrame peekTextFrame(){
        return mTextFrameQueue.peek();
    }

    public synchronized boolean textAvailable(){
        return (peekTextFrame()!=null) ;
    }

    public synchronized MediaFrame getTextFrame(){
        return mTextFrameQueue.poll();
    }


    protected synchronized int onHeader( ByteBuffer frame ) {
        int pos = frame.position() ;
        if( file_encrypted ) {
            PWRc4.RC4_block_crypt(frame.array(),
                    frame.arrayOffset() + pos ,
                    40,
                    0,
                    file_encrypt_RC4_table);
        }

        ByteOrder bo = frame.order();
        frame.order(ByteOrder.LITTLE_ENDIAN);
        audio_codec = frame.getShort(pos+12) ;
        audio_samplerate = frame.getInt(pos+16) ;
        devicetype = (int)frame.getShort(pos+6) ;
        video_width = (int)frame.getShort(pos+24) ;
        video_height = (int)frame.getShort(pos+26) ;
        // restore frame bit order
        frame.order(bo);
        return 40 ;
    }

    private int h264sync( ByteBuffer frame, int pos  ) {
        int off ;
        int limit = frame.limit()-pos-8 ;
        for( off=0; off<limit; off++) {
            if( frame.get(pos+off) == 0 &&
                    frame.get(pos+off+1) == 0 &&
                    frame.get(pos+off+2) == 0 &&
                    frame.get(pos+off+3) == 1 ) {
                return pos+off ;
            }
        }
        return -1 ;
    }

    private int h264sync( ByteBuffer frame, int pos, byte code  ) {
        int off ;
        int limit = frame.limit()-pos-8 ;
        for( off=0; off<limit; off++) {
            if( frame.get(pos+off) == 0 &&
                    frame.get(pos+off+1) == 0 &&
                    frame.get(pos+off+2) == 0 &&
                    frame.get(pos+off+3) == 1 &&
                    frame.get(pos+off+4) == code ) {
                return pos+off ;
            }
        }
        return -1 ;
    }

    //private synchronized int addVideoFrame( byte[] frameBuffer, int offset, int len){
    protected synchronized int addVideoFrame( ByteBuffer frame ) {
        int pos = frame.position() ;

        // PES header length
        int pesHeaderLen = ((int)frame.get(pos+8) & 0xff) + 9 ;

        int pesSize = ((int) frame.getShort(pos + 4) )&0x0ffff ;
        if( pesSize > 0 ) {        // big frame, use len as frame size
            pesSize+=6 ;
        }
        else if( pesHeaderLen>18 ) {
            // large PES packet size
            pesSize = frame.getInt(pos + 15) + pesHeaderLen;
        }

        if( pesSize > frame.remaining() || pesSize<pesHeaderLen ) {
            frame.position(frame.limit());      // invalidate frame buffer
            return 0;
        }

        // decrypt video frame
        if( file_encrypted ) {
            byte [] fa = frame.array();
            int  o = frame.arrayOffset()+pos+pesHeaderLen ;
            int  rem = pesSize - pesHeaderLen ;
            while( rem>10 ) {
                if( fa[o] == 0 && fa[o+1] == 0 && fa[o+2]==1 &&
                        (fa[o+3]&0x9b)==1 )
                {
                    // found enc start point
                    o+=4 ;
                    rem-=4 ;
                    if( rem>1024 ) rem=1024 ;
                    PWRc4.RC4_block_crypt(fa,o,rem,0,file_encrypt_RC4_table);
                    break ;
                }
                o++ ;
                rem--;
            }
        }

        // get time stamp from video PES packet
        frameTS = timeStamp(frame) ;
        if( x_textframe!=null ) {
            // patch osd frame timestamp
            x_textframe.timestamp = frameTS ;
            x_textframe = null ;
        }

        int flags = 0;
        if ((frame.get(pos + pesHeaderLen + 4) & 0x0f) != 1) {
            flags = MediaCodec.BUFFER_FLAG_KEY_FRAME;
            if ( mVideoFrameQueue.size() > mMaxQueue ) {
                clearQueue();
            }
        }

        pos += pesHeaderLen ;
        int len = pesSize - pesHeaderLen ;
        if( !mStarted ) {
            int sync0x67 = h264sync(frame, pos, (byte) 0x67);
            if (sync0x67 >= 0) {
                int syncend = h264sync(frame, sync0x67 + 4);
                if (syncend > 0 && frame.get(syncend + 4) == 0x68) {
                    syncend = h264sync(frame, syncend + 4);
                }
                if (syncend > sync0x67 + 8 && syncend < pos + len) {
                    mStarted = true;
                    mVideoFrameQueue.add(new MediaFrame(frame, sync0x67, (syncend - sync0x67), frameTS, MediaCodec.BUFFER_FLAG_CODEC_CONFIG));
                    mVideoFrameQueue.add(new MediaFrame(frame, syncend,  len - (syncend - pos), frameTS, flags ));
                }
            }
        }
        else {
            mVideoFrameQueue.add(new MediaFrame(frame, pos, len, frameTS, flags));
        }
        return pesSize ;
    }

    protected synchronized int addAudioFrame( ByteBuffer frame ) {
        int pos = frame.position() ;

        // PES header length
        int pesHeaderLen = ((int)frame.get(pos+8) & 0xff) + 9 ;
        int pesSize = 6 + (((int) frame.getShort(pos + 4) )&0x0ffff) ;

        if( pesSize > frame.remaining() || pesSize<pesHeaderLen ) {
            frame.position(frame.limit());      // invalidate frame buffer
            return 0;
        }

        // decrypt audio frame
        if( file_encrypted ) {
            byte [] fa = frame.array();
            int  o = frame.arrayOffset()+pos+pesHeaderLen ;
            int  rem = pesSize - pesHeaderLen ;
            if( rem>0 ) {
                if (rem > 1024) rem = 1024;
                PWRc4.RC4_block_crypt(fa, o, rem, 0, file_encrypt_RC4_table);
            }
        }

        // get time stamp from video PES packet
        frameTS = timeStamp(frame) ;
        mAudioFrameQueue.add(new MediaFrame(frame, pos + pesHeaderLen, pesSize - pesHeaderLen, frameTS, 0));

        return pesSize ;
    }

    protected synchronized int addTextFrame( ByteBuffer frame ){
        int pos = frame.position() ;
        ByteOrder xorder  = frame.order() ;
        frame.order(ByteOrder.LITTLE_ENDIAN);
        int txtLen = (int) frame.getShort( pos+6 );
        frame.order(xorder);

        x_textframe =  new MediaFrame(frame, pos + 8, txtLen, frameTS, 0) ;
        mTextFrameQueue.add(x_textframe);
        return 8+txtLen ;
    }

}
