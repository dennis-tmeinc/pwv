package com.tme_inc.pwv;

import android.app.Activity;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Media Decoder for PWViewer
 *   Wrap MediaCode, AudioTrack for video/audio playback,
 * Created by dennis on 12/22/14.
 */
public class PWDecoder {

    private MediaCodec mDecoder;
    private ByteBuffer[] mInputBuffers;
    private ByteBuffer[] mOutputBuffers;

    private int mAvailableInputBuffer = -1;
    private int mAvailableOutputBuffer = -1;
    private MediaCodec.BufferInfo mOutputBufferInfo;

    static MediaFormat smediaFormat;
    static MediaFormat smediaFormatHD;

    static final String mimeType = "video/avc";
    private MediaFormat mOutputFormat;

    static final int maxInputSize = 1200000 ;

    // audio output
    private AudioTrack mAudioTrack;
    private Queue<MediaFrame> mAudioBuffers;
    Thread mAudioThread;

    public PWDecoder(MediaCodec decoder) {
        mDecoder = decoder;
        mDecoder.start();

        mInputBuffers = mDecoder.getInputBuffers();
        mAvailableInputBuffer = -1 ;

        mOutputBuffers = mDecoder.getOutputBuffers();
        mOutputBufferInfo = new MediaCodec.BufferInfo();
        mAvailableOutputBuffer = -1;

        // init audio track
        int samplerate = 8010;
        int minsize = AudioTrack.getMinBufferSize(samplerate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (minsize < 2000) minsize = 2000;
        mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                samplerate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minsize, AudioTrack.MODE_STREAM);
        mAudioTrack.play();

        // start audio buffering thread
        mAudioBuffers = new ArrayDeque<MediaFrame>();
        mAudioThread = new Thread(new Runnable() {
            @Override
            public void run() {
                audioRun();
            }
        });
        mAudioThread.start();

    }

    // get MediaFormat from sample mp4 file
    private static MediaFormat sdFormat(Activity activity, int res) {
        if (smediaFormat == null) {
            MediaFormat mf;
            Uri videoUri = Uri.parse("android.resource://"
                    + activity.getPackageName() + "/"
                    + R.raw.sample);

            MediaExtractor extractor = new MediaExtractor();
            try {
                extractor.setDataSource(activity, videoUri, null);
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
        if (smediaFormat == null) {
            smediaFormat = MediaFormat.createVideoFormat(mimeType, 720, 480);
            smediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, maxInputSize);
        }
        return smediaFormat;
    }

    // get MediaFormat from sample mp4 file
    private static MediaFormat hdFormat(Activity activity, int res) {
        if (smediaFormatHD == null) {
            MediaFormat mf;
            Uri videoUri = Uri.parse("android.resource://"
                    + activity.getPackageName() + "/"
                    + R.raw.samplehd);

            MediaExtractor extractor = new MediaExtractor();
            try {
                extractor.setDataSource(activity, videoUri, null);
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
        if (smediaFormatHD == null) {
            smediaFormatHD = MediaFormat.createVideoFormat(mimeType, 1920, 1080);
            byte[] csd_info = {0, 0, 0, 1, 103, 100, 0, 40, -84, 52, -59, 1, -32, 17, 31, 120, 11, 80, 16, 16, 31, 0, 0, 3, 3, -23, 0, 0, -22, 96, -108, 0, 0, 0, 1, 104, -18, 60, -128};
            smediaFormatHD.setByteBuffer("csd-0", ByteBuffer.wrap(csd_info));
            smediaFormatHD.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, maxInputSize);
        }
        return smediaFormatHD;
    }

    public static PWDecoder CreatePlayer(Activity activity, Surface surface, int res) {

        MediaFormat mediaFormat;
        //try {
        if (res < 5) {
            mediaFormat = sdFormat(activity, res);
        } else {
            mediaFormat = hdFormat(activity, res);
        }

        mediaFormat.setLong(MediaFormat.KEY_DURATION, 100000000000L);

        PWDecoder player=null;
        try {
            MediaCodec decoder = MediaCodec.createDecoderByType(mimeType);
            decoder.configure(mediaFormat, surface, null, 0);
            player = new PWDecoder(decoder);
        } catch (IOException e) {
            player = null ;
        }
        return player ;
    }


    /**
     * Releases resources and ends the encoding/decoding session.
     */
    public void Release() {

        mDecoder.stop();
        mDecoder.release();
        mDecoder = null;

        // stop Audio Thread
        if (mAudioThread != null && mAudioThread.isAlive()) {
            mAudioThread.interrupt();
            try {
                mAudioThread.join(10000);
            } catch (InterruptedException e) {
                // do nothing
            }
            mAudioThread = null;
        }

        mAudioBuffers.clear();
        mAudioTrack.stop();
        mAudioTrack.release();
        mAudioTrack = null;

    }

    public boolean inputReady() {
        // Get valid input buffers from the codec to fill later in the same order they were
        // made available by the codec.
        if( mAvailableInputBuffer>=0 ) {
            return true ;
        }

        int index;
        try {
            if ((index = mDecoder.dequeueInputBuffer(0)) != MediaCodec.INFO_TRY_AGAIN_LATER) {
                mAvailableInputBuffer = index ;
                return true ;
            }
        } catch (IllegalStateException e) {
            Log.d("PWPlayer update", "Illegal State Execption");
            Release();
        } catch (Exception e) {
            Log.d("PWPlayer update", "other exceptions");
            Release();
        }
        return false ;
    }

    public boolean writeInput(MediaFrame input) {
        int len = input.len;
        if ( inputReady() && input!=null && len > 0 ) {
            ByteBuffer buffer = mInputBuffers[mAvailableInputBuffer];

            // we can't write our sample to a lesser capacity input buffer.
            if (len > buffer.capacity()) {
                return false;
            }

            buffer.clear();
            buffer.put(input.array, input.pos, len);
            buffer.flip ();

            // Submit the buffer to the codec for decoding. The presentationTimeUs
            // indicates the position (play time) for the current sample.
            mDecoder.queueInputBuffer(mAvailableInputBuffer, 0, len, input.getTimestamp(), input.getFlags());
            mAvailableInputBuffer=-1 ;

            return true ;
        }
        return false;
    }

    /**
     * Performs a peek() operation in the queue to extract media info for the buffer ready to be
     * released i.e. the head element of the queue.
     *
     * @param out_bufferInfo An output var to hold the buffer info.
     * @return True, if the peek was successful.
     */
    public boolean peekSample(MediaCodec.BufferInfo out_bufferInfo) {
        if (mAvailableOutputBuffer < 0) {
            int index;
            while ((index = mDecoder.dequeueOutputBuffer(mOutputBufferInfo, 0)) != MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (index == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    mOutputBuffers = mDecoder.getOutputBuffers();
                } else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    mOutputFormat = mDecoder.getOutputFormat();
                } else if (index >= 0) {
                    mAvailableOutputBuffer = index;
                    break;
                } else {
                    throw new IllegalStateException("Unknown status from dequeueOutputBuffer");
                }
            }
        }

        if (mAvailableOutputBuffer >= 0) {
            if (out_bufferInfo != null) {
                out_bufferInfo.set(
                        mOutputBufferInfo.offset,
                        mOutputBufferInfo.size,
                        mOutputBufferInfo.presentationTimeUs,
                        mOutputBufferInfo.flags);
            }
            return true;
        }
        return false;
    }

    /**
     * Processes, releases and optionally renders the output buffer available at the head of the
     * queue. All observers are notified with a callback.
     */
    public boolean popOutput() {
        if (peekSample(null)) {
            // releases the buffer back to the codec
            mDecoder.releaseOutputBuffer(mAvailableOutputBuffer, true);
            mAvailableOutputBuffer = -1;
            return true;
        }
        return false;
    }

    private short uLaw_decode(byte u_val) {
        u_val = (byte) ~u_val ;
        int t = (((u_val & 0x0f) << 3) + 0x84) <<  ((u_val & 0x70) >> 4) ;
        return (short) (((u_val & 0x80) == 0) ? (t-0x84):(0x84-t));
    }

    // Audio Track tweaks, since AudioTrack.write() is blocking
    private void audioRun(){

        while(mDecoder!=null) {
            MediaFrame audioFrame = mAudioBuffers.poll();
            if (audioFrame!=null) {
                if( mAudioTrack.getState() == AudioTrack.STATE_INITIALIZED && mAudioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING ) {
                    int size = audioFrame.len;
                    short[] buffer = new short[size];
                    for (int i = 0; i < size; i++) {
                        buffer[i] = uLaw_decode(audioFrame.get(i));
                    }
                    mAudioTrack.write(buffer, 0, size);
                }
            }
            else{
                try {
                    Thread.sleep(20L);
                }
                catch (InterruptedException e){
                    break;
                }
            }
        }

    }

    public boolean audioReady() {
        return (mAudioBuffers.size()<2 );
    }

    public boolean writeAudio(MediaFrame input) {
        if( input!=null && mAudioBuffers!=null )
            mAudioBuffers.offer(input);
        return true ;
    }

}
