package com.tme_inc.pwv;

import android.app.Activity;
import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import android.view.Surface;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Media Player for PWViewer
 *   Wrap MediaCode, AudioTrack for video/audio playback,
 * Created by dennis on 12/22/14.
 */
public class PWPlayer {

    private MediaCodec mDecoder;

    private int m_AvailableInputBuffer ;
    private int m_AvailableOutputBuffer ;
    private MediaCodec.BufferInfo m_OutputBufferInfo;
    private boolean m_livemode ;

    private final int maxInputSize = 1000000 ;
    private MediaFormat smediaFormat;
    private MediaFormat smediaFormatHD;

    static final String mimeType = "video/avc";

    private MediaFormat mMediaFormat;

    // audio output
    final static int mAudioChunkSize = 256 ;
    private int mAudioLoLimit ;
    private ArrayBlockingQueue<MediaFrame> mAudioBuffers ;
    Thread mAudioThread;
    // keep track of audio frame timestamp
    volatile long audioTimestamp ;

    private Context mContext ;
    private Surface mSurf ;
    private int mAudioRate = 8000 ;   // audio playback samplereate
    private short mAudioCodec = 1 ;     // audio codec, 1: ulaw, 2: alow

    public PWPlayer( Activity activity, Surface surface, boolean livemode) {
        mContext = activity ;
        mSurf = surface ;
        mDecoder = null ;
        m_livemode = livemode ;
        m_OutputBufferInfo = new MediaCodec.BufferInfo();
        m_AvailableInputBuffer = -1 ;
        m_AvailableOutputBuffer = -1 ;
    }

    public void start() {

        try {
            mDecoder = MediaCodec.createDecoderByType(mMediaFormat.getString(MediaFormat.KEY_MIME));
        } catch (IOException e) {
            mDecoder = null;
        }

        if( mDecoder == null ) return ;

        mDecoder.configure(mMediaFormat, mSurf, null, 0);
        mDecoder.start();

        // start audio buffering thread
        mAudioLoLimit = 1 ;
        mAudioBuffers  = new ArrayBlockingQueue( 5 ) ;

        audioTimestamp = 0 ;
        mAudioThread = new Thread(new Runnable() {
            @Override
            public void run() {
                audioRun();
            }
        }) ;
        mAudioThread.setPriority(Thread.MAX_PRIORITY);
        mAudioThread.start();
    }

    /**
     * Releases resources and ends the encoding/decoding session.
     */
    public void Release() {
        if( mDecoder!=null) {
            mDecoder.release();
            mDecoder = null;
        }
        m_AvailableInputBuffer = -1 ;
        m_AvailableOutputBuffer = -1 ;

        // stop Audio Thread
        if (mAudioThread != null && mAudioThread.isAlive()) {
            mAudioThread.interrupt();
            try {
                mAudioThread.join(1000);
            } catch (InterruptedException e) {
                // do nothing
            }
            mAudioThread = null;
        }
        mAudioBuffers.clear();
    }

    private void restart(){
        Release();
        start();
    }

    // get MediaFormat from sample mp4 file
    private MediaFormat sdFormat() {
        if (smediaFormat == null) {
            MediaFormat mf;
            Uri videoUri = Uri.parse("android.resource://"
                    + mContext.getPackageName() + "/"
                    + R.raw.sample);

            MediaExtractor extractor = new MediaExtractor();
            try {
                extractor.setDataSource(mContext, videoUri, null);
                int nTracks = extractor.getTrackCount();
                for (int i = 0; i < nTracks; ++i) {
                    mf = extractor.getTrackFormat(i);
                    if (mf.getString(MediaFormat.KEY_MIME).contains("video/")) {
                        smediaFormat = mf;
                        smediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, maxInputSize);
                        break;
                    }
                }
            } catch (IOException e) {
                // done
                smediaFormat = null;
            }
            extractor.release();
        }
        smediaFormat = null ;
        if (smediaFormat == null) {
            smediaFormat = MediaFormat.createVideoFormat(mimeType, 720, 480);
            smediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, maxInputSize);
        }
        return smediaFormat;
    }

    // get MediaFormat from sample mp4 file
    private MediaFormat hdFormat() {
        if (smediaFormatHD == null) {
            MediaFormat mf;
            Uri videoUri = Uri.parse("android.resource://"
                    + mContext.getPackageName() + "/"
                    + R.raw.samplehd);

            MediaExtractor extractor = new MediaExtractor();
            try {
                extractor.setDataSource(mContext, videoUri, null);
                int nTracks = extractor.getTrackCount();
                for (int i = 0; i < nTracks; ++i) {
                    mf = extractor.getTrackFormat(i);
                    if (mf.getString(MediaFormat.KEY_MIME).contains("video/")) {
                        smediaFormatHD = mf;
                        smediaFormatHD.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, maxInputSize);
                        break;
                    }
                }
            } catch (IOException e) {
                // done
                smediaFormatHD = null;
            }
            extractor.release();
        }
        smediaFormatHD = null ;
        if (smediaFormatHD == null) {
            smediaFormatHD = MediaFormat.createVideoFormat(mimeType, 1920, 1080);
            // byte[] csd_info = {0, 0, 0, 1, 103, 100, 0, 40, -84, 52, -59, 1, -32, 17, 31, 120, 11, 80, 16, 16, 31, 0, 0, 3, 3, -23, 0, 0, -22, 96, -108, 0, 0, 0, 1, 104, -18, 60, -128};
            // smediaFormatHD.setByteBuffer("csd-0", ByteBuffer.wrap(csd_info));
            smediaFormatHD.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, maxInputSize);
        }
        return smediaFormatHD;
    }

    // set format by sample
    public void setFormat( String sample ) {
        MediaFormat mf;
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource( sample );
            int nTracks = extractor.getTrackCount();
            for (int i = 0; i < nTracks; ++i) {
                mf = extractor.getTrackFormat(i);
                if (mf.getString(MediaFormat.KEY_MIME).contains("video/")) {
                    // decoder.configure(mediaFormat, surface, null, 0);
                    mMediaFormat = mf ;
                    break ;
                }
            }
        } catch (IOException e) {
        }
        extractor.release();
    }

    // set format by resolution setting
    public void setFormat( int res ) {
        int height, width ;

        if (res < 5) {
            width = 720 ;
            height = 480 ;
        } else {
            width = 1920 ;
            height = 1080 ;
        }
        mMediaFormat = MediaFormat.createVideoFormat(mimeType, width, height);
        mMediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, maxInputSize);
        mMediaFormat.setLong(MediaFormat.KEY_DURATION, 100000000000L);
    }

    // set format by video size
    public void setFormat( int width, int height ) {
        mMediaFormat = MediaFormat.createVideoFormat(mimeType, width, height);
        int inputsize = width*height ;
        if( inputsize<100000 ) inputsize = 100000 ;
        mMediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, inputsize );
        mMediaFormat.setLong(MediaFormat.KEY_DURATION, 100000000000L);
    }

    public void setAudioFormat( short audio_codec, int samplerate ) {
        if( samplerate < 8000 || samplerate > 48000 ) samplerate = 8000 ;      //default audio rate = 8000
        mAudioRate = samplerate ;
        mAudioCodec = audio_codec ;
    }

    public boolean videoInputReady() {
        if(m_AvailableInputBuffer<0) {
            try {
                m_AvailableInputBuffer = mDecoder.dequeueInputBuffer(0);
            } catch (IllegalStateException e) {
                Log.d("PWPlayer update", "Illegal State Execption");
            } catch (Exception e) {
                Log.d("PWPlayer update", "other exceptions");
            }
        }
        return m_AvailableInputBuffer>=0 ;
    }

    public boolean videoInput(MediaFrame input) {
        if( input==null || ! videoInputReady()  ) return false ;

        int ilen = input.len();
        if( ilen>0 ) {

            try {
                ByteBuffer buffer ;
                if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ) {
                    buffer = mDecoder.getInputBuffer(m_AvailableInputBuffer);
                }
                else {
                    buffer = mDecoder.getInputBuffers()[m_AvailableInputBuffer];
                    buffer.clear();
                }

                if (ilen > buffer.capacity()) {
                    return false;
                }
                buffer.put(input.array(), input.pos(), ilen);

                mDecoder.queueInputBuffer(m_AvailableInputBuffer, 0, ilen, input.timestamp * 1000, input.flags);
                m_AvailableInputBuffer = -1 ;
                return true ;
            }
            catch (IllegalStateException e ) {

            }

        }
        return false;
    }

    public long getVideoTimestamp() {
        return m_OutputBufferInfo.presentationTimeUs/1000 ;
    }

    public boolean videoOutputReady() {
        if( m_AvailableOutputBuffer<0 ) {
            try {
                m_AvailableOutputBuffer = mDecoder.dequeueOutputBuffer(m_OutputBufferInfo, 0);
            }
            catch (IllegalStateException e ) {
                m_AvailableOutputBuffer = -1 ;
            }
        }
        return (m_AvailableOutputBuffer>=0);
    }

    public boolean popOutput( boolean render ) {
        try {
            if( videoOutputReady() ) {
                // releases the buffer back to the codec
                mDecoder.releaseOutputBuffer(m_AvailableOutputBuffer, render );
                m_AvailableOutputBuffer = -1 ;
                return true ;
            }
        }
        catch (IllegalStateException e) {
        }
        return false;
    }

    private static short uLaw_decode_src(byte u_val) {
        int t = ~u_val ;
        t = (((t & 0x0f) << 3) + 0x84) <<  ((t & 0x70) >> 4) ;
        return (short) ( ((u_val&0x80) != 0 ) ? (t-0x84):(0x84-t) );
    }

    // ref: https://en.wikipedia.org/wiki/G.711
    // to generate this table:
    //   create a 256 bytes binary file with value from 0 - 255
    //   ffmpeg -f u8 -acodec pcm_mulaw -ar 8000 -i <inputfile> -f s16le <output>
    static final short ulaw_table[] =
            {
                    -0x7d7c, -0x797c, -0x757c, -0x717c, -0x6d7c, -0x697c, -0x657c, -0x617c,
                    -0x5d7c, -0x597c, -0x557c, -0x517c, -0x4d7c, -0x497c, -0x457c, -0x417c,
                    -0x3e7c, -0x3c7c, -0x3a7c, -0x387c, -0x367c, -0x347c, -0x327c, -0x307c,
                    -0x2e7c, -0x2c7c, -0x2a7c, -0x287c, -0x267c, -0x247c, -0x227c, -0x207c,
                    -0x1efc, -0x1dfc, -0x1cfc, -0x1bfc, -0x1afc, -0x19fc, -0x18fc, -0x17fc,
                    -0x16fc, -0x15fc, -0x14fc, -0x13fc, -0x12fc, -0x11fc, -0x10fc, -0x0ffc,
                    -0x0f3c, -0x0ebc, -0x0e3c, -0x0dbc, -0x0d3c, -0x0cbc, -0x0c3c, -0x0bbc,
                    -0x0b3c, -0x0abc, -0x0a3c, -0x09bc, -0x093c, -0x08bc, -0x083c, -0x07bc,
                    -0x075c, -0x071c, -0x06dc, -0x069c, -0x065c, -0x061c, -0x05dc, -0x059c,
                    -0x055c, -0x051c, -0x04dc, -0x049c, -0x045c, -0x041c, -0x03dc, -0x039c,
                    -0x036c, -0x034c, -0x032c, -0x030c, -0x02ec, -0x02cc, -0x02ac, -0x028c,
                    -0x026c, -0x024c, -0x022c, -0x020c, -0x01ec, -0x01cc, -0x01ac, -0x018c,
                    -0x0174, -0x0164, -0x0154, -0x0144, -0x0134, -0x0124, -0x0114, -0x0104,
                    -0x00f4, -0x00e4, -0x00d4, -0x00c4, -0x00b4, -0x00a4, -0x0094, -0x0084,
                    -0x0078, -0x0070, -0x0068, -0x0060, -0x0058, -0x0050, -0x0048, -0x0040,
                    -0x0038, -0x0030, -0x0028, -0x0020, -0x0018, -0x0010, -0x0008, -0x0001,
                    0x7d7c,  0x797c,  0x757c,  0x717c,  0x6d7c,  0x697c,  0x657c,  0x617c,
                    0x5d7c,  0x597c,  0x557c,  0x517c,  0x4d7c,  0x497c,  0x457c,  0x417c,
                    0x3e7c,  0x3c7c,  0x3a7c,  0x387c,  0x367c,  0x347c,  0x327c,  0x307c,
                    0x2e7c,  0x2c7c,  0x2a7c,  0x287c,  0x267c,  0x247c,  0x227c,  0x207c,
                    0x1efc,  0x1dfc,  0x1cfc,  0x1bfc,  0x1afc,  0x19fc,  0x18fc,  0x17fc,
                    0x16fc,  0x15fc,  0x14fc,  0x13fc,  0x12fc,  0x11fc,  0x10fc,  0x0ffc,
                    0x0f3c,  0x0ebc,  0x0e3c,  0x0dbc,  0x0d3c,  0x0cbc,  0x0c3c,  0x0bbc,
                    0x0b3c,  0x0abc,  0x0a3c,  0x09bc,  0x093c,  0x08bc,  0x083c,  0x07bc,
                    0x075c,  0x071c,  0x06dc,  0x069c,  0x065c,  0x061c,  0x05dc,  0x059c,
                    0x055c,  0x051c,  0x04dc,  0x049c,  0x045c,  0x041c,  0x03dc,  0x039c,
                    0x036c,  0x034c,  0x032c,  0x030c,  0x02ec,  0x02cc,  0x02ac,  0x028c,
                    0x026c,  0x024c,  0x022c,  0x020c,  0x01ec,  0x01cc,  0x01ac,  0x018c,
                    0x0174,  0x0164,  0x0154,  0x0144,  0x0134,  0x0124,  0x0114,  0x0104,
                    0x00f4,  0x00e4,  0x00d4,  0x00c4,  0x00b4,  0x00a4,  0x0094,  0x0084,
                    0x0078,  0x0070,  0x0068,  0x0060,  0x0058,  0x0050,  0x0048,  0x0040,
                    0x0038,  0x0030,  0x0028,  0x0020,  0x0018,  0x0010,  0x0008,  0x0000
            };

    // to generate this table:
    //   create a 256 bytes binary file with value from 0 - 255
    //   ffmpeg -f u8 -acodec pcm_alaw -ar 8000 -i <inputfile>.raw -f s16le <output>
    static final short alaw_table[] =
            {
                    -0x1580, -0x1480, -0x1780, -0x1680, -0x1180, -0x1080, -0x1380, -0x1280,
                    -0x1d80, -0x1c80, -0x1f80, -0x1e80, -0x1980, -0x1880, -0x1b80, -0x1a80,
                    -0x0ac0, -0x0a40, -0x0bc0, -0x0b40, -0x08c0, -0x0840, -0x09c0, -0x0940,
                    -0x0ec0, -0x0e40, -0x0fc0, -0x0f40, -0x0cc0, -0x0c40, -0x0dc0, -0x0d40,
                    -0x5600, -0x5200, -0x5e00, -0x5a00, -0x4600, -0x4200, -0x4e00, -0x4a00,
                    -0x7600, -0x7200, -0x7e00, -0x7a00, -0x6600, -0x6200, -0x6e00, -0x6a00,
                    -0x2b00, -0x2900, -0x2f00, -0x2d00, -0x2300, -0x2100, -0x2700, -0x2500,
                    -0x3b00, -0x3900, -0x3f00, -0x3d00, -0x3300, -0x3100, -0x3700, -0x3500,
                    -0x0158, -0x0148, -0x0178, -0x0168, -0x0118, -0x0108, -0x0138, -0x0128,
                    -0x01d8, -0x01c8, -0x01f8, -0x01e8, -0x0198, -0x0188, -0x01b8, -0x01a8,
                    -0x0058, -0x0048, -0x0078, -0x0068, -0x0018, -0x0008, -0x0038, -0x0028,
                    -0x00d8, -0x00c8, -0x00f8, -0x00e8, -0x0098, -0x0088, -0x00b8, -0x00a8,
                    -0x0560, -0x0520, -0x05e0, -0x05a0, -0x0460, -0x0420, -0x04e0, -0x04a0,
                    -0x0760, -0x0720, -0x07e0, -0x07a0, -0x0660, -0x0620, -0x06e0, -0x06a0,
                    -0x02b0, -0x0290, -0x02f0, -0x02d0, -0x0230, -0x0210, -0x0270, -0x0250,
                    -0x03b0, -0x0390, -0x03f0, -0x03d0, -0x0330, -0x0310, -0x0370, -0x0350,
                    0x1580,  0x1480,  0x1780,  0x1680,  0x1180,  0x1080,  0x1380,  0x1280,
                    0x1d80,  0x1c80,  0x1f80,  0x1e80,  0x1980,  0x1880,  0x1b80,  0x1a80,
                    0x0ac0,  0x0a40,  0x0bc0,  0x0b40,  0x08c0,  0x0840,  0x09c0,  0x0940,
                    0x0ec0,  0x0e40,  0x0fc0,  0x0f40,  0x0cc0,  0x0c40,  0x0dc0,  0x0d40,
                    0x5600,  0x5200,  0x5e00,  0x5a00,  0x4600,  0x4200,  0x4e00,  0x4a00,
                    0x7600,  0x7200,  0x7e00,  0x7a00,  0x6600,  0x6200,  0x6e00,  0x6a00,
                    0x2b00,  0x2900,  0x2f00,  0x2d00,  0x2300,  0x2100,  0x2700,  0x2500,
                    0x3b00,  0x3900,  0x3f00,  0x3d00,  0x3300,  0x3100,  0x3700,  0x3500,
                    0x0158,  0x0148,  0x0178,  0x0168,  0x0118,  0x0108,  0x0138,  0x0128,
                    0x01d8,  0x01c8,  0x01f8,  0x01e8,  0x0198,  0x0188,  0x01b8,  0x01a8,
                    0x0058,  0x0048,  0x0078,  0x0068,  0x0018,  0x0008,  0x0038,  0x0028,
                    0x00d8,  0x00c8,  0x00f8,  0x00e8,  0x0098,  0x0088,  0x00b8,  0x00a8,
                    0x0560,  0x0520,  0x05e0,  0x05a0,  0x0460,  0x0420,  0x04e0,  0x04a0,
                    0x0760,  0x0720,  0x07e0,  0x07a0,  0x0660,  0x0620,  0x06e0,  0x06a0,
                    0x02b0,  0x0290,  0x02f0,  0x02d0,  0x0230,  0x0210,  0x0270,  0x0250,
                    0x03b0,  0x0390,  0x03f0,  0x03d0,  0x0330,  0x0310,  0x0370,  0x0350
            };

    // Audio Track tweaks, since AudioTrack.write() is blocking
    private void audioRun_x(){

        // init audio track
        short decode_table [] ;
        if( mAudioCodec == 2 ) {
            decode_table = alaw_table;
        }
        else {
            decode_table = ulaw_table;
        }

        int audioBufsize = AudioTrack.getMinBufferSize(mAudioRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);

        AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                mAudioRate  ,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                audioBufsize*2, AudioTrack.MODE_STREAM);

        audioTrack.play();

        while( !Thread.interrupted() && mDecoder!=null ) {
            MediaFrame audioFrame = null;
            try {
                audioFrame = mAudioBuffers.poll(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                audioFrame = null ;
            }
            if( audioFrame!=null ) {
                int alen = audioFrame.len();
                short[] audioBuffer = new short [alen] ;

                int adj = 0 ;
                // audio sync hack for live view
                if( m_livemode && mAudioBuffers.size() > 0) {
                    adj = 1 ;
                }

                // decode audio
                int i ;
                for( i = 0; i < alen; i++) {
                    audioBuffer[i] = decode_table[ audioFrame.geti(i) ] ;
                }
                audioTrack.write(audioBuffer, 0, alen-adj);
                audioTimestamp = audioFrame.timestamp ;
            }
        }

        audioTrack.stop();
        audioTrack.release();

    }


    // Audio Track tweaks, since AudioTrack.write() is blocking
    private void audioRun(){

        MediaCodec aCodec = null ;
        String amime="audio/g711-mlaw" ;

        if( mAudioCodec == 2 ) {
            amime = "audio/g711-alaw";
        }
        else {
            amime = "audio/g711-mlaw";
        }

        try {
            aCodec = MediaCodec.createDecoderByType(amime);
        } catch (IOException e) {
            aCodec = null ;
        } catch (IllegalArgumentException e) {
            aCodec = null ;
        }

        if( aCodec==null ) {
            audioRun_x();
            return;
        }

        MediaFormat aInputformat =  MediaFormat.createAudioFormat(amime,mAudioRate, 1);
        aCodec.configure(aInputformat, null, null, 0);
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo ();
        aCodec.start();

        int audioBufsize = AudioTrack.getMinBufferSize(mAudioRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                mAudioRate  ,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                audioBufsize*2, AudioTrack.MODE_STREAM);
        audioTrack.play();

        while( !Thread.interrupted() && mDecoder!=null ) {

            // mediacodec test
            int outputBufferId = aCodec.dequeueOutputBuffer(bufferInfo, 100000);
            if (outputBufferId >= 0) {

                audioTimestamp = bufferInfo.presentationTimeUs / 1000 ;

                ByteBuffer outputBuffer = aCodec.getOutputBuffer(outputBufferId);
                //MediaFormat format = aCodec.getOutputFormat(outputBufferId);

                ShortBuffer samples = outputBuffer.order(ByteOrder.nativeOrder()).asShortBuffer();
                int alen = samples.remaining() ;
                short[] res = new short[alen];
                for (int i = 0; i < alen; ++i) {
                    res[i] = samples.get(i);
                }
                audioTrack.write(res, 0, alen);
                aCodec.releaseOutputBuffer(outputBufferId, false);
            }

            MediaFrame audioFrame = null;
            try {
                audioFrame = mAudioBuffers.poll(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                audioFrame = null ;
            }
            if( audioFrame!=null ) {
                // mediacodec test
                int inputBufferId = aCodec.dequeueInputBuffer(100000);
                if (inputBufferId >= 0) {
                    ByteBuffer inputBuffer = aCodec.getInputBuffer(inputBufferId);
                    // fill inputBuffer with valid data
                    inputBuffer.put(audioFrame.array(), audioFrame.pos(), audioFrame.len());
                    aCodec.queueInputBuffer(inputBufferId, 0, audioFrame.len(), audioFrame.timestamp *1000, 0 );
                }
            }
        }

        audioTrack.stop();
        audioTrack.release();

    }

    public long getAudioTimestamp() {
        return audioTimestamp ;
    }

    public void resetAudioTimestamp() {
        audioTimestamp = 0;
    }

    public boolean audioReady() {
        return (mAudioBuffers.size()<=mAudioLoLimit );
    }

    public boolean audioInput(MediaFrame input) {
        if( input!=null ) {
            if( mAudioBuffers.remainingCapacity() == 0 ){
                mAudioBuffers.poll();
                Log.d("Audio", "Audio frame skipped.");
            }
            mAudioBuffers.offer(input);
            return true ;
        }
        return false ;
    }

    // flush all buffers
    public void flush() {

        // flush video codec
        mDecoder.flush();
        m_AvailableInputBuffer = -1 ;
        m_AvailableOutputBuffer = -1 ;

        // flush audio codec
        mAudioBuffers.clear();
        audioTimestamp = 0 ;

    }

    // updown: 0: down, 1: up
    public void adjustVolume( boolean volumeup ) {

        AudioManager am = (AudioManager)mContext.getSystemService(Context.AUDIO_SERVICE);
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                volumeup ? AudioManager.ADJUST_RAISE:AudioManager.ADJUST_LOWER ,
                AudioManager.FLAG_SHOW_UI );
    }

}
