package com.tme_inc.pwv;

import android.animation.TimeAnimator;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.Scroller;
import android.widget.TextView;
import android.widget.Toast;

import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class Playback extends PwViewActivity {

    private int m_playSpeed = 1000;        // 1000 : normal speed, 0: paused, 1: one frame forward than paused
    private boolean m_loading = true ;

    TimeBar mTimeBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_playback);

        // setup player screen
        setupScreen();

        m_UIhandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                switch (msg.what) {
                    case PWMessage.MSG_UI_HIDE:
                        hideUI();
                        break;

                    case PWMessage.MSG_PW_CONNECT:

                        // PW stream connected
                        mTimeBar.setDateList(((PWPlaybackStream)mstream).getDayList());
                        playerSeek(mTimeBar.getPos());

                        break;

                    case PWMessage.MSG_PW_SEEK:
                        // PW Stream seek complete

                        // to flush player if play is running
                        if (mplayer != null) {
                            mplayer.flush();
                        }
                        refFrameTime = 0;       // reset frame reference time
                        if (m_playSpeed == 0)
                            m_playSpeed = 1;    // try display one frame

                        mstream.start();
                        break;

                    case PWMessage.MSG_PW_GETCLIPLIST:
                        if (msg.arg2 == 1) {   // clip info
                            mTimeBar.setClipInfo(msg.arg1, (int[]) msg.obj);
                        } else if (msg.arg2 == 2) {   //lock info
                            mTimeBar.setLockInfo(msg.arg1, (int[]) msg.obj);
                        }
                        break;

                    case PWMessage.MSG_TB_GETCLIPLIST:
                        if (mstream != null) {
                            ((PWPlaybackStream)mstream).getClipList(msg.arg1);
                        }
                        break;

                    case PWMessage.MSG_TB_SCROLL:
                        if (mTimeBar.isSeekPending()) {
                            playerSeek( mTimeBar.getSeekPos() );
                        }
                        break;

                    case PWMessage.MSG_VRI_SELECTED:
                        String vri = (String) msg.obj;

                        // split date/time from vri
                        String[] strArray = vri.split("-");
                        if (strArray.length > 1 && mstream != null) {
                            String datetime = strArray[1];
                            long dt = Long.parseLong(datetime);
                            int date = (int) (dt / 10000L);
                            int time = (int) (dt % 10000L);
                            Calendar calendar = Calendar.getInstance();
                            calendar.clear();
                            calendar.set(Calendar.YEAR, 2000 + date / 10000);
                            calendar.set(Calendar.MONTH, date % 10000 / 100 - 1 + Calendar.JANUARY);
                            calendar.set(Calendar.DATE, date % 100);

                            calendar.set(Calendar.HOUR_OF_DAY, time / 100);
                            calendar.set(Calendar.MINUTE, time % 100);
                            playerSeek(calendar.getTimeInMillis());
                        }
                        break;


                    case PWMessage.MSG_DATE_SELECTED:
                        int date = msg.arg1;
                        Calendar calendar = Calendar.getInstance();
                        calendar.clear();
                        calendar.set(Calendar.YEAR, date / 10000);
                        calendar.set(Calendar.MONTH, date % 10000 / 100 - 1 + Calendar.JANUARY);
                        calendar.set(Calendar.DATE, date % 100);

                        playerSeek(calendar.getTimeInMillis());

                        break;

                }
            }
        };

        mTimeBar = (TimeBar) findViewById(R.id.timebar);
        mTimeBar.setUiHandler(m_UIhandler);

        // restore timebar info
        mTimeBar.setPos(tb_pos);
        mTimeBar.setViewportWidth(tb_width);


        ImageButton button;
        button = (ImageButton) findViewById(R.id.button_tag);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //TagEventDialog tagDialog = new TagEventDialog();
                //tagDialog.setDvrTime(mTimeBar.getPos());
                //tagDialog.show(getFragmentManager(), "tagTagEvent");
                savePref();
                Intent intent = new Intent(getBaseContext(), TagEventActivity.class);
                intent.putExtra("DvrTime", (long) mTimeBar.getPos());
                startActivity(intent);
            }
        });

        button = (ImageButton) findViewById(R.id.button_search);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //VriDialog vriDialog = new VriDialog();
                //vriDialog.setUIHandler(m_handler);
                //vriDialog.show(getFragmentManager(), "tagVriEvent");
                VideoDatesDialog videoDatesDialog = new VideoDatesDialog();
                videoDatesDialog.setDateList(mTimeBar.getDateList());
                videoDatesDialog.setUIHandler(m_UIhandler);
                videoDatesDialog.show(getFragmentManager(), "tagVideoDates");
            }
        });

        // Play button
        button = (ImageButton) findViewById(R.id.button_play);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (m_playSpeed > 1) {
                    m_playSpeed = 1;
                    ((ImageButton) findViewById(R.id.button_play)).setImageResource(R.drawable.play_play);
                    showHint("Paused") ;
                } else {
                    m_playSpeed = 1000;
                    ((ImageButton) findViewById(R.id.button_play)).setImageResource(R.drawable.play_pause);
                    showHint("Play") ;
                }
            }
        });

        // Slow button
        button = (ImageButton) findViewById(R.id.button_slow);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if( m_playSpeed > 125 ) {
                    m_playSpeed /= 2 ;
                }
                showHint( "Play speed: X" + (float)m_playSpeed/1000 ) ;
            }
        });

        // fast button
        button = (ImageButton) findViewById(R.id.button_fast);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if( m_playSpeed < 100 ) {
                    m_playSpeed = 1000 ;
                }
                if( m_playSpeed < 8000 ) {
                    m_playSpeed *= 2 ;
                }
                showHint( "Play speed: X" + (float)m_playSpeed/1000 ) ;
            }
        });

        // backward button
        button = (ImageButton) findViewById(R.id.button_backward);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playerSeek( mTimeBar.getPos()-20000 );
            }
        });

        // forward button
        button = (ImageButton) findViewById(R.id.button_forward);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playerSeek( mTimeBar.getPos()+20000 );
            }
        });

        // forward step
        button = (ImageButton) findViewById(R.id.button_step);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                m_playSpeed = 1;
                ((ImageButton) findViewById(R.id.button_play)).setImageResource(R.drawable.play_pause);
            }
        });

        button= (ImageButton)findViewById(R.id.btPlayMode) ;
        button.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent(getBaseContext(), Liveview.class);
                        startActivity(intent);
                        finish();
                    }
                }
        );
    }

    // player seek to calendar time
    protected void playerSeek( long seekTime ) {
        if (mstream != null) {
            ((PWPlaybackStream)mstream).seek(seekTime);
        }
    }

    @Override
    protected void showUI() {
        super.showUI();
        // display action bar and menu
        if (m_screenLandscape) {
            mTimeBar.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void hideUI() {
        super.hideUI();

        mTimeBar.setVisibility(View.INVISIBLE);
    }

    private Toast buttonHint = null ;
    void showHint(String text) {
        if( buttonHint == null ) {
            buttonHint = Toast.makeText(this, text, Toast.LENGTH_SHORT);
            buttonHint.setGravity(Gravity.CENTER, 0, 0);
        }
        else {
            buttonHint.setText(text);
        }
        buttonHint.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();

        savePref();
    }

    long refTime ;          // ref time
    long refFrameTime ;     // ref frame time stamp
    long idleTime ;         // idleing time (no av output)

    @Override
    protected void onAnimate( long totalTime, long deltaTime)
    {
        super.onAnimate(totalTime,deltaTime);

        if( mstream == null ) {
            mstream = new PWPlaybackStream(m_channel, m_UIhandler);

            m_loading = true ;
            findViewById(R.id.loadingBar).setVisibility(View.VISIBLE);
            ((TextView)findViewById(R.id.loadingText)).setText("Loading...");
            findViewById(R.id.loadingText).setVisibility(View.VISIBLE);

            // clear OSD ;
            for(int idx=0; idx<m_maxosd ; idx++ ) {
                if (m_osd[idx] != null) {
                    m_osd[idx].setText("");
                    m_osd[idx].setVisibility(View.INVISIBLE);
                }
            }
            return ;
        }
        else if( mplayer==null ) {
            String cn = mstream.getChannelName() ;
            if( cn!=null )
                ((TextView)findViewById(R.id.loadingText)).setText( cn );
            if( mstream.videoAvailable() ) {
                //startActivity(new Intent(getBaseContext(), Playback.class));
                TextureView textureView = (TextureView)findViewById(R.id.playScreen);
                if( textureView!=null && textureView.isAvailable()) {
                    SurfaceTexture surface = textureView.getSurfaceTexture();
                    if (surface != null && mstream.getResolution()>=0 ) {
                        //mplayer = PWPlayer.CreatePlayer(this, new Surface(surface), mstream.mRes );
                        mplayer = new PWPlayer(this, new Surface(surface), false);
                        mplayer.setFormat(mstream.getResolution());
                        if( mstream.video_width > 50 && mstream.video_height>50 ) {
                            mplayer.setFormat( mstream.video_width, mstream.video_height ) ;
                        }
                        mplayer.setAudioFormat( mstream.audio_codec, mstream.audio_samplerate ) ;
                        mplayer.start();
                        m_totalChannel = mstream.totalChannels ;

                        m_playSpeed = 1000 ;    // 1000 : normal speed, 0: paused, 1: one frame forward than paused
                        refTime = 0 ;
                        refFrameTime = 0 ;
                        idleTime = 0 ;
                        ((ImageButton)findViewById(R.id.button_play)).setImageResource(R.drawable.play_pause);
                    }
                }
            }
            else if( m_loading ) {
                if( ((PWPlaybackStream)mstream).isEndOfStream() ) {
                    m_loading = false ;
                    findViewById(R.id.loadingBar).setVisibility(View.INVISIBLE);
                    findViewById(R.id.loadingText).setVisibility(View.INVISIBLE);
                }
            }

            return ;
        }

        MediaFrame frame;
        boolean forceOSD = false ;

        if( refFrameTime == 0 ) {
            // first video frame
            frame = mstream.peekVideoFrame();
            if( frame != null ) {
                // reset reference timers
                refFrameTime = frame.timestamp ;
                refTime = totalTime;
                mplayer.resetAudioTimestamp() ;
                forceOSD = true ;
            }
            else {
                return ;
            }
        }

        // expected playing frame time
        long playFrameTime = (totalTime - refTime) * m_playSpeed / 1000 + refFrameTime;

        if( m_playSpeed == 1000 ) {
            // sync with audio frame time
            long audioTimestamp = mplayer.getAudioTimestamp() ;
            if( audioTimestamp>1000) {
                playFrameTime = audioTimestamp ;
            }
        }

        // fill vidoe decoder buffer
        frame = mstream.peekVideoFrame() ;
        if( frame!=null && mplayer.videoInputReady() ) {
            mstream.getVideoFrame();
            // fast play, only output key frames
            if(m_playSpeed<=4000 || frame.flags != 0) {
                mplayer.videoInput(frame);
            }
        }

        // fill audio decoder buffer
        frame = mstream.peekAudioFrame() ;
        if( frame!=null && mplayer.audioReady() ) {
            if( m_playSpeed == 1000 ) {
                if( (frame.timestamp-playFrameTime)<2000 ){
                    mstream.getAudioFrame();
                    mplayer.audioInput(frame);
                }
            }
            else if( frame.timestamp < playFrameTime ) {
                mstream.getAudioFrame();
                mplayer.resetAudioTimestamp() ;
            }
        }

        // Output text frame
        frame = mstream.peekTextFrame();
        if( frame!=null ) {
            long difft = frame.timestamp - playFrameTime ;
            if( difft<-3000 ) {
                // get and discard older text
                mstream.getTextFrame();
            }
            else if( difft<=0 || forceOSD ) {
                // display text
                mstream.getTextFrame();
                displayOSD(frame);
            }
        }

        // Render output buffer if available
        if( mplayer.videoOutputReady() ) {

            long frametime = mplayer.getVideoTimestamp();
            boolean rendered = false ;

            if( m_playSpeed<=0 ) {              // paused
                refTime = totalTime ;
                idleTime = 0 ;                  // fake no idle
                mTimeBar.setPos(frametime);
            }
            else if( m_playSpeed == 1 ) {
                rendered = mplayer.popOutput(true);        // pop one frame
                mTimeBar.setPos(frametime);
                ((ImageButton)findViewById(R.id.button_play)).setImageResource(R.drawable.play_play);
                m_playSpeed = 0 ;               // paused
                refFrameTime = frametime ;
                refTime = totalTime ;
                idleTime = 0 ;                  // fake no idle
            }
            else {
                long diff = frametime - playFrameTime;
                if (diff <= 0) {
                    rendered = mplayer.popOutput(true);
                    mTimeBar.setPos(frametime);
                    idleTime = 0 ;
                    if( diff < 100 ) {
                        refFrameTime = frametime;
                        refTime = totalTime;
                    }
                }
            }

            // first video frame available?
            if( m_loading && rendered ) {
                findViewById(R.id.loadingBar).setVisibility(View.INVISIBLE);
                findViewById(R.id.loadingText).setVisibility(View.INVISIBLE);
                m_loading = false;
            }
        }

        idleTime += deltaTime ;
        if( idleTime > 3000 ) {
            // idling more then 3 seconds, restart sync
            refFrameTime = 0 ;
            idleTime = 0;
        }

        return;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_playback, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            // Intent intent = new Intent(getBaseContext(), Launcher.class);
            Intent intent = new Intent(getBaseContext(), SettingsActivity.class);
            startActivity(intent);
            return true;
        } else if (id == R.id.action_liveview) {
            Intent intent = new Intent(getBaseContext(), Liveview.class);
            startActivity(intent);
            finish();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        savePref();
    }

    private void savePref(){
        // save current channel
        channel = m_channel ;
        tb_pos = mTimeBar.getPos() ;
        tb_width = mTimeBar.getViewportWidth();

    }

}
