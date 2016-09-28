package com.tme_inc.pwv;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeAnimator;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.util.DisplayMetrics;
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
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Scroller;
import android.widget.TextView;

import java.io.UnsupportedEncodingException;
import java.util.Scanner;

/**
 * Created by dennis on 24/05/16.
 */
public class PwViewActivity extends Activity {

    // app variables
    public static long tb_pos = 0;
    public static int  tb_width = 3600 ;
    public static int  channel = 0 ;

    protected boolean m_screenLandscape = false ;
    protected float m_screendensity ;

    protected int   m_channel ;
    protected int   m_totalChannel ;

    protected ViewGroup m_screen ;

    protected static final int m_maxosd = 16 ;
    protected TextView[] m_osd ;

    protected PWProtocol mPwProtocol ;        // Pw Connection

    // player support
    protected PWPlayer mplayer ;
    protected PWStream mstream ;
    protected TimeAnimator mTimeAnimator = null;
    protected Handler m_UIhandler ;


    // Generic screen initialization
    // called in onCreate(), after setContentView
    protected void setupScreen() {

        int i;
        m_screen = (ViewGroup) findViewById(R.id.layoutscreen);

        Configuration configuration =  getResources().getConfiguration();
        m_screenLandscape = configuration.orientation ==  Configuration.ORIENTATION_LANDSCAPE ;
        if( m_screenLandscape ) {
            // Hide the status bar.
            m_screen.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
        }

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        m_screendensity = metrics.density ;

        // PW protocol for status update
        mPwProtocol = new PWProtocol();

        m_osd = new TextView [m_maxosd];
        for( i=0; i<m_maxosd; i++ ) {
            m_osd[i] = new TextView(this);
            m_osd[i].setTextColor(0xd0ffffff);
            m_screen.addView(m_osd[i], 1, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT));
            m_osd[i].setTextSize(TypedValue.COMPLEX_UNIT_DIP, (float) (configuration.screenWidthDp) / 32.0f);
            m_osd[i].setShadowLayer(2, 0, 0, Color.BLACK);
            m_osd[i].setVisibility(View.INVISIBLE);
        }

        m_channel = channel ;
        m_totalChannel = m_channel+1 ;

        // Screen swip support
        m_screen.setOnTouchListener(new View.OnTouchListener() {

            int   scroll_dir  ;      // 0: touch down, 1: scrolling  2: volume

            Scroller scroller = new Scroller(getBaseContext());;
            final Runnable scrollUpdate = new Runnable() {
                @Override
                public void run() {
                    int sw = m_screen.getWidth();
                    int sx = m_screen.getScrollX();
                    if( sx>sw || sx<-sw) {
                        scroller.forceFinished(true);
                    }
                    if (scroller.computeScrollOffset()) {
                        m_screen.scrollTo( scroller.getCurrX(), 0);
                        m_screen.postOnAnimation(this);
                    }
                    else {
                        sx = m_screen.getScrollX();
                        if( sx > sw/2) {
                            goNextChannel();
                        }
                        else if(sx < -sw/2) {
                            goPrevChannel();
                        }
                    }
                }
            };

            void fling( int velocityX ) {
                int screenWidth = m_screen.getWidth();
                scroller.fling( m_screen.getScrollX(), m_screen.getScrollY(), -velocityX/2, 0, -5*screenWidth, 5*screenWidth, 0, 0) ;
                int dx = scroller.getFinalX() ;
                if( dx > -screenWidth && dx <= - screenWidth/2 ) {
                    scroller.setFinalX(-screenWidth);
                }
                else if( dx < screenWidth && dx >= screenWidth/2 ) {
                    scroller.setFinalX( screenWidth );
                }
                else if( dx > -screenWidth/2 && dx < screenWidth/2 ){
                    scroller.setFinalX(0);
                }
                if( scroller.getDuration() < 300 ) {
                    scroller.extendDuration(500 - scroller.getDuration() );
                }
                m_screen.postOnAnimation(scrollUpdate);
            }

            GestureDetector gestureDetector = new GestureDetector( getBaseContext(), new GestureDetector.SimpleOnGestureListener(){

                @Override
                public boolean onDown(MotionEvent e) {
                    scroll_dir = 0 ;
                    return true ;
                }

                @Override
                public boolean onSingleTapUp(MotionEvent e) {
                    toggleUI();
                    return true ;
                }

                float disty ;

                @Override
                public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                    if( scroll_dir==0 && m_screen.getScrollX()==0 ) {
                        if( Math.abs(distanceX) > Math.abs(distanceY)) {
                            scroll_dir = 1 ;
                        }
                        else {
                            scroll_dir = 2 ;
                            disty = 0f ;
                        }
                    }

                    if( scroll_dir == 1 ) {
                        m_screen.scrollBy((int)distanceX, 0);
                    }
                    else if(scroll_dir == 2 && mplayer != null ){      // 2 : set volume
                        disty += distanceY ;
                        if ( disty > m_screendensity*8 ) {
                            mplayer.adjustVolume(true);
                            disty = 0f ;
                        }
                        else if( disty < - m_screendensity*8 ) {
                            mplayer.adjustVolume(false);
                            disty = 0f ;
                        }
                    }

                    return true ;
                }

                @Override
                public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                    if( scroll_dir==1 ) {
                        fling((int) velocityX);
                        return true;
                    }
                    return false ;
                }
            });

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                boolean res = gestureDetector.onTouchEvent(event);
                if( !res ) {
                    int act = event.getActionMasked();
                    if( act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL ) {
                        int sx = m_screen.getScrollX() ;
                        if( sx!= 0 ) {
                            fling(0);
                            return true ;
                        }
                    }

                }
                return res ;
            }
        });

    }

    private boolean m_req_hideui = false ;
    protected void showUI()
    {
        // display action bar and menu
        if( m_screenLandscape ) {

            getActionBar().show();
            findViewById(R.id.pwcontrol).setVisibility(View.VISIBLE);
            findViewById(R.id.pwcontrol).animate().alpha(1.0f);
            findViewById(R.id.btPlayMode).animate().alpha(1.0f);

            if( m_UIhandler!=null) {
                m_UIhandler.removeMessages(PWMessage.MSG_UI_HIDE); // remove pending hide ui
                m_UIhandler.sendEmptyMessageDelayed(PWMessage.MSG_UI_HIDE, 30000);
                m_req_hideui = true;
            }

        }

    }

    protected void hideUI()
    {
        // display action bar and menu
        if( m_screenLandscape && getActionBar().isShowing()) {

            // Hide the status bar.
            m_screen.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );

            // Hide Title bar
            getActionBar().hide();

            findViewById(R.id.btPlayMode).animate()
                    .alpha(0.0f);
            // Hide Buttons
            findViewById(R.id.pwcontrol).animate()
                    .alpha(0.0f)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            super.onAnimationEnd(animation);
                            if( getActionBar().isShowing() ) {
                                findViewById(R.id.pwcontrol).setVisibility(View.VISIBLE);
                            }
                            else {
                                findViewById(R.id.pwcontrol).setVisibility(View.GONE);
                            }
                        }
                    });

            m_req_hideui = false ;
        }
    }

    private void toggleUI(){
        // display action bar and menu
        if(m_screenLandscape && getActionBar().isShowing()) {
            hideUI();
        }
        else {
            showUI();
        }
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        if( m_req_hideui  && m_UIhandler!=null) {
            m_UIhandler.removeMessages(PWMessage.MSG_UI_HIDE); // remove pending hide ui
            m_UIhandler.sendEmptyMessageDelayed(PWMessage.MSG_UI_HIDE, 30000);
            m_req_hideui = true ;
        }
    }

    boolean m_keepon = false ;
    protected void screen_KeepOn( boolean on ) {
        if( on ) {
            if( !m_keepon ) {
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                m_keepon = true;
            }
        }
        else {
            if( m_keepon ) {
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                m_keepon = false;
            }
        }
    }

    protected int savedScreenTimeout = 0 ;
    protected  int getSreenTimeout( ) {
        try {
            return Settings.System.getInt( getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT );
        }
        catch (Settings.SettingNotFoundException e) {
        }
        catch (SecurityException e) {
        }
        return 0 ;
    }

    protected  void setSreenTimeout(int ms) {
        try {
            Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, ms);
        }
        catch (SecurityException e) {

        }
    }

    protected void stopMedia() {
        if (mstream != null) {
            mstream.release();
            mstream = null;
        }
        if (mplayer != null) {
            mplayer.Release();
            mplayer = null;
        }
        m_screen.scrollTo(0,0);
    }

    protected void goPrevChannel() {
        if( mstream != null ) {
            m_totalChannel = mstream.totalChannels ;
        }
        if( m_totalChannel<1 ) m_totalChannel = 1 ;
        m_channel--;
        if (m_channel < 0) {
            m_channel = m_totalChannel - 1;
        }
        stopMedia();
    }

    protected void goNextChannel() {
        if( mstream != null ) {
            m_totalChannel = mstream.totalChannels ;
        }
        if( m_totalChannel<1 ) m_totalChannel = 1 ;
        m_channel++;
        if (m_channel >= m_totalChannel) {
            m_channel = 0;
        }
        stopMedia();
    }

    protected void onAnimate( long totalTime, long deltaTime) {
    }

    @Override
    protected void onResume() {
        super.onResume();

        savedScreenTimeout = getSreenTimeout();
        if( savedScreenTimeout <= 0 ) {
            savedScreenTimeout = 60000 ;
        }
        if( savedScreenTimeout>1800000 ) {
            savedScreenTimeout=1800000 ;
        }

        // force turn on screen keep on
        m_keepon = false ;
        screen_KeepOn( true );

        showUI();

        // start animator
        if( mTimeAnimator==null ) {
            mTimeAnimator = new TimeAnimator();
            mTimeAnimator.setTimeListener(new TimeAnimator.TimeListener() {
                @Override
                public void onTimeUpdate(TimeAnimator timeAnimator, long totalTime, long deltaTime) {
                    onAnimate(totalTime, deltaTime);
                }
            });
        }
        mTimeAnimator.start();
    }

    @Override
    protected void onPause() {
        super.onPause();

        mTimeAnimator.end();
        stopMedia();
        mPwProtocol.cancel();

        if( savedScreenTimeout > 0 ) {
            setSreenTimeout(savedScreenTimeout);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if( hasFocus && !m_screenLandscape  ) {
            int w = m_screen.getWidth();
            int h = m_screen.getHeight();
            if (w > 0 && h > 0) {
                RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) m_screen.getLayoutParams();
                // get layout width&height
                w += lp.leftMargin + lp.rightMargin;
                h += lp.topMargin + lp.bottomMargin;
                if (h * 4 > w * 3) {
                    h = (h - (w * 3 / 4)) / 2;
                    lp.setMargins(0, h, 0, h);
                    m_screen.requestLayout();
                }
            }
        }
    }

    protected void displayOSD( MediaFrame txtFrame ){

        if( txtFrame == null )
            return ;

        byte [] frame = txtFrame.array();
        int text_off = txtFrame.pos() ;
        int text_len = text_off+txtFrame.len();

        for(int idx=0;
            idx<m_maxosd &&
                    m_osd[idx]!=null &&
                    text_off < text_len &&
                    frame[text_off] == (byte) 's' &&
                    frame[text_off + 1] == (byte) 't' ;
            idx++)
        {
            int osdLen = frame[text_off + 6];
            if (osdLen >= 4) {
                if( m_osd[idx].getVisibility() != View.VISIBLE ) {
                    m_osd[idx].setVisibility(View.VISIBLE);

                    byte align = frame[text_off + 8];
                    int posx  = frame[text_off + 9];
                    int posy  = frame[text_off + 10];

                    float h = m_osd[idx].getHeight()/24.0f ;
                    FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams)m_osd[idx].getLayoutParams();
                    lp.height = -2 ;
                    lp.width = -2 ;
                    lp.gravity = 0;

                    if( (align&1) != 0) {       // ALIGN LEFT
                        lp.leftMargin = posx*2 ;
                        lp.rightMargin = 0 ;
                        lp.gravity |= Gravity.LEFT ;
                    }
                    else if( (align&2) != 0) {       // ALIGN RIGHT
                        lp.leftMargin = 0 ;
                        lp.rightMargin = posx*2 ;
                        lp.gravity |= Gravity.RIGHT ;
                    }
                    if( (align&4) != 0) {       // ALIGN TOP
                        lp.topMargin = (int) (posy*h) ;
                        lp.bottomMargin = 0 ;
                        lp.gravity |= Gravity.TOP ;
                    }
                    else if( (align&8) != 0) {       // ALIGN BOTTOM
                        lp.topMargin = 0 ;
                        lp.bottomMargin = (int) (posy*h) ;
                        lp.gravity |= Gravity.BOTTOM ;
                    }
                    m_osd[idx].requestLayout();

                }

                try {
                    m_osd[idx].setText( new String( frame, text_off + 12, osdLen - 4, "ISO-8859-1").trim() );
                }
                catch (UnsupportedEncodingException e) {
                }
                text_off += osdLen + 8;

            } else {
                break;
            }
        }
    }

}

