package com.tme_inc.pwv;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.EdgeEffect;
import android.widget.OverScroller;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;

/**
 * Created by dennis on 1/29/15.
 */
public class TimeBar extends View {

    // State objects and values related to gesture tracking.
    private ScaleGestureDetector mScaleGestureDetector;
    private GestureDetector mGestureDetector;
    private OverScroller mScroller;

    // Edge effect / overscroll tracking objects.
    private EdgeEffect mEdgeEffect;
    private boolean mEdgeRight ;        // Edge effect on right side

    // Viewport
    private int mViewportWidthMin;
    private int mViewportWidthMax;
    private int mViewportWidth;        // View port width in seconds

    // View size ;
    private int mWidth ;                // Width in pixels
    private int mHeight ;

    // Minimum time (Start Time) in calendar time seconds
    private long mPosMin ;

    // Position Range, in seconds
    private int mPosRange;
    private int mPos;                   // center time (related to mPosMin)
    private boolean mSeekPending ;      // indicate a seek operating is pending

    private Toast mPosToast ;
    private Paint nColorPaint ;
    private Paint lColorPaint ;

    private float mDensity = 1f ;       // draw density

    // clip info cache
    private HashMap <Integer, int[]> mClipInfo = new HashMap < Integer, int [] > () ;
    private HashMap <Integer, int[]> mLockInfo = new HashMap < Integer, int [] > () ;

    // UI thread handler from hosting activity
    private Handler mUiHandler ;

    public TimeBar(Context context) {
        this(context, null, 0);
    }

    public TimeBar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TimeBar(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mDensity = context.getResources().getDisplayMetrics().density ;

        // Sets up gesture detectors
        mScaleGestureDetector = new ScaleGestureDetector(context, mScaleGestureListener);
        mGestureDetector = new GestureDetector(context, mGestureListener);
        mScroller = new OverScroller(context);

        // Sets up edge effects
        mEdgeEffect = new EdgeEffect(context);

        mViewportWidthMin = 300;                    // 5 min
        mViewportWidthMax = 30 * 24 * 60 * 60;      // 30 days
        mViewportWidth = 60*60 ;                    // an hour by default
        mWidth = 600;                               // will changed by onSizeChanged()

        // set Max/Min time
        Calendar calendar = Calendar.getInstance();
        calendar.clear();

        calendar.set(Calendar.YEAR, 2000);
        mPosMin = calendar.getTimeInMillis()/1000;
        calendar.clear();
        calendar.set(Calendar.YEAR, 2050);
        long timeMax = calendar.getTimeInMillis()/1000;

        mPos = 0;
        mPosRange = (int)((timeMax - mPosMin)) ;

        // preset time range with no video
        setDateList(null) ;

        mPosToast = Toast.makeText(context, "", Toast.LENGTH_SHORT );
        mPosToast.setGravity(Gravity.CENTER, 0, 0);

        // allocate color paint for L/N color bar
        nColorPaint = new Paint() ;
        nColorPaint.setColor( context.getResources().getColor(R.color.timebar_ncolor) );
        lColorPaint = new Paint();
        lColorPaint.setColor( context.getResources().getColor(R.color.timebar_lcolor) );

    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int minsize = 100;
        setMeasuredDimension(
                Math.max(getSuggestedMinimumWidth(),
                        resolveSize(minsize + getPaddingLeft() + 1000,
                                widthMeasureSpec)),
                Math.max(getSuggestedMinimumHeight(),
                        resolveSize(minsize + getPaddingTop() + 500,
                                heightMeasureSpec)));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mWidth = w ;
        mHeight = h ;
    }

    private int PosToX(int pos) {
        return (int) ( ((long) pos - (long)mPos) * mWidth / mViewportWidth + mWidth / 2 );
    }

    private int XToPos(int x) {
        return (int) (((long) x - mWidth / 2) * mViewportWidth / mWidth + mPos);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int posStart = XToPos(0);
        int posEnd = XToPos(mWidth);
        int i ;

        Paint p = new Paint();
        // background, auto draw "timebar.png"

        int clipx = (int)(mDensity * 8) ;

        canvas.clipRect(clipx,clipx, mWidth-clipx, mHeight-clipx) ;

        // draw clip bar
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis( (mPosMin+posEnd)*1000l);
        int enddate = calendar.get(Calendar.YEAR)*10000 +
                (calendar.get(Calendar.MONTH)-Calendar.JANUARY + 1)*100 +
                calendar.get(Calendar.DATE) ;

        // draw clip bar
        calendar.setTimeInMillis( (mPosMin+posStart)*1000l );
        int y,m,d ;
        y = calendar.get(Calendar.YEAR) ;
        m = calendar.get(Calendar.MONTH) ;
        d = calendar.get(Calendar.DATE) ;
        int date = y*10000 +
                (m-Calendar.JANUARY + 1)*100 +
                d ;

        calendar.clear();
        calendar.set(Calendar.YEAR, y);
        calendar.set(Calendar.MONTH, m);
        calendar.set(Calendar.DATE, d);

        while( date <= enddate ) {
            int[] clipList ;
            int posofday = (int)(calendar.getTimeInMillis()/1000 - mPosMin);

            // draw N clips info
            if( mClipInfo.containsKey( date) ) {
                clipList = mClipInfo.get(date);
                if (clipList == null) {
                    if (mUiHandler != null) {
                        mUiHandler.obtainMessage(PWMessage.MSG_TB_GETCLIPLIST, date, 0 ).sendToTarget();
                        mClipInfo.put(date, new int [1]) ;  // indicate get clip request sent
                    }
                }
                else if( clipList.length>1) {
                    /*
                    int l = clipList.length ;
                    for( i=0; i<l; i+=2) {
                        int ex = PosToX(posofday+clipList[i+1]) ;
                        if( ex < 0 ) {
                            continue ;
                        }
                        int sx = PosToX(posofday+clipList[i]) ;
                        if( sx >= mWidth ) {
                            break ;
                        }
                        canvas.drawRect( (float)sx, 2.0f, (float)(ex+2), (float)(mHeight-2), nColorPaint );
                    }
                    */
                    int l = clipList.length ;
                    int psx = 0 ;
                    int pex = -1 ;
                    for( i=0; i<l; i+=2) {
                        int ex = PosToX(posofday+clipList[i+1]) ;
                        if( ex <= 0 ) {
                            continue ;
                        }

                        int sx = PosToX(posofday+clipList[i]) ;
                        if( sx >= mWidth ) {
                            break ;
                        }

                        if( sx<pex+5 ) {
                            // merge it
                            pex = ex ;
                        }
                        else {
                            if( pex> 0 ) {
                                canvas.drawRect( (float)psx, 2.0f, (float)(pex+2), (float)(mHeight-2), nColorPaint );
                            }
                            pex = ex ;
                            psx = sx ;
                        }
                        if( pex>= mWidth ) {
                            pex = mWidth ;
                            break ;
                        }
                    }

                    if( pex> 0 ) {
                        canvas.drawRect( (float)psx, 2.0f, (float)(pex+2), (float)(mHeight-2), nColorPaint );
                    }
                }
            }

            // draw L clips info
            if( mLockInfo.containsKey( date) ) {
                clipList = mLockInfo.get(date) ;
                if( clipList!=null && clipList.length>1) {
                    int l = clipList.length ;
                    int psx = 0 ;
                    int pex = -1 ;
                    for( i=0; i<l; i+=2) {
                        int ex = PosToX(posofday+clipList[i+1]) ;
                        if( ex <= 0 ) {
                            continue ;
                        }

                        int sx = PosToX(posofday+clipList[i]) ;
                        if( sx >= mWidth ) {
                            break ;
                        }

                        if( sx<pex+5 ) {
                            // merge it
                            pex = ex ;
                        }
                        else {
                            if( pex> 0 ) {
                                canvas.drawRect( (float)psx, 2.0f, (float)(pex+2), (float)(mHeight-2), lColorPaint );
                            }
                            pex = ex ;
                            psx = sx ;
                        }
                        if( pex>= mWidth ) {
                            pex = mWidth ;
                            break ;
                        }
                    }

                    if( pex> 0 ) {
                        canvas.drawRect( (float)psx, 2.0f, (float)(pex+2), (float)(mHeight-2), lColorPaint );
                    }
                }
            }

            calendar.add( Calendar.DATE, 1 );
            date = calendar.get(Calendar.YEAR)*10000 +
                    (calendar.get(Calendar.MONTH)-Calendar.JANUARY + 1)*100 +
                    calendar.get(Calendar.DATE) ;
        }


        // Draw time grid
        Paint labelTextPaint = new Paint();
        labelTextPaint.setAntiAlias(true);
        labelTextPaint.setTextSize(mHeight/2);
        int labelWidth = (int) labelTextPaint.measureText("00-00  ");
        int maxLabels = mWidth/labelWidth ;

        int gridWidth = (posEnd-posStart)/maxLabels ;

        int [] gridwarray = { 1, 2, 5, 10, 15, 30, 60 } ;

        String format = "HH:mm";
        if( gridWidth<60*60) {
            for( i=0; i<gridwarray.length; i++ ) {
                if( gridWidth < gridwarray[i]*60 ) {
                    gridWidth = gridwarray[i] * 60;
                    break ;
                }
            }
        }
        else if( gridWidth <  20 * 60 * 60 ){       // less than a day
            gridWidth = ( gridWidth / (60*60) + 1 ) * 60 * 60 ;
        }
        else {              // more than a day
            format = "MM/dd";
            gridWidth = ( gridWidth / (24*60*60) + 1 ) * 24 * 60 * 60 ;
        }

        labelTextPaint.setTextAlign(Paint.Align.CENTER);
        SimpleDateFormat sdf = new SimpleDateFormat(format, Locale.US);

        float descent = labelTextPaint.descent();
        float textY = (float)(mHeight/2+10) ;
        float markY = textY + descent ;

        p.setColor(0xff000000);
        for( int pos = posStart - posStart%gridWidth ; pos<posEnd+gridWidth ; pos+=gridWidth ) {
            calendar.setTimeInMillis( (mPosMin + pos)*1000L );
            String text = sdf.format(calendar.getTime());
            int x = PosToX(pos) ;
            canvas.drawText( text, x, textY, labelTextPaint );
            canvas.drawLine((float)(x), markY, (float)(x), (float)(mHeight-clipx), p  );
            //canvas.drawRect( (float)(x-1), (float)(mHeight-20), (float)(x+1), (float)(mHeight-4), p );
        }

        // draw center indicator
        p.setColor(0xffffffff);
        float dw = mDensity * 2 ;
        canvas.drawRect( (float)(mWidth/2-dw), dw, (float)(mWidth/2+dw), (float)(mHeight-dw), p );


        // draw Edge Effect

        if (!mEdgeEffect.isFinished()) {
            canvas.save();
            if( mEdgeRight ) {
                canvas.translate( mWidth, 0 );
                canvas.rotate(90, 0, 0);
            }
            else {
                canvas.translate(0, mHeight);
                canvas.rotate(-90, 0, 0);
            }
            canvas.scale(1.0f, 2.0f);
            mEdgeEffect.setSize( mHeight, mHeight );
            mEdgeEffect.draw(canvas);
            canvas.restore();
        }

    }

    // to redraw slider in next animation frame
    void redraw() {
        postInvalidateOnAnimation();
    }

    private boolean mTouch ;
    private long mTouchDelayTime ;
    @Override
    public boolean onTouchEvent(MotionEvent event) {

        int act = event.getActionMasked() ;
        if( act == MotionEvent.ACTION_DOWN ) {
            mTouch = true ;
        }
        else if( act == MotionEvent.ACTION_UP ) {
            mTouchDelayTime = SystemClock.uptimeMillis()+3000 ;
            mTouch = false ;
        }

        boolean retVal = mScaleGestureDetector.onTouchEvent(event);
        retVal = mGestureDetector.onTouchEvent(event) || retVal;
        return retVal || super.onTouchEvent(event);
    }

    /**
     * The scale listener, used for handling multi-finger scale gestures.
     */
    private final ScaleGestureDetector.OnScaleGestureListener mScaleGestureListener
            = new ScaleGestureDetector.SimpleOnScaleGestureListener() {
        /**
         * This is the active focal point in terms of the viewport. Could be a local
         * variable but kept here to minimize per-frame allocations.
         */
        private float prevSpan;

        @Override
        public boolean onScaleBegin(ScaleGestureDetector scaleGestureDetector) {
            prevSpan = scaleGestureDetector.getCurrentSpanX();
            return true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector scaleGestureDetector) {
            float span = scaleGestureDetector.getCurrentSpanX();

            mViewportWidth = (int)(prevSpan / span * mViewportWidth) ;
            if( mViewportWidth > mViewportWidthMax ) {
                mViewportWidth = mViewportWidthMax ;
            }
            else if( mViewportWidth < mViewportWidthMin ) {
                mViewportWidth = mViewportWidthMin ;
            }
            prevSpan = span;

            redraw();
            showToast();
            return true;
        }
    };

    /**
     * The gesture listener, used for handling simple gestures such as scrolls and flings.
     */
    private final GestureDetector.SimpleOnGestureListener mGestureListener
            = new GestureDetector.SimpleOnGestureListener() {
        @Override
        public boolean onDown(MotionEvent e) {
            releaseScroll();
            redraw();
            return true;
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            if( e.getX() < (mWidth/4) ) {
                scrollBy(-mWidth / 2);
            }
            else if(e.getX() > (mWidth*3/4) ) {
                scrollBy(mWidth / 2);
            }
            showToast();
            return super.onSingleTapConfirmed(e);
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            int vpw = 24*60*60 ;
            if( mViewportWidth > 24*60*60 ) {
                mPosToast.setText( "Time range : 24 hrs" );
            }
            else if( mViewportWidth > 60*60  ) {
                vpw = 60*60 ;
                mPosToast.setText( "Time range : 1 hr" );
            }
            else if( mViewportWidth > 15*60 ){
                vpw = 15 * 60 ;
                mPosToast.setText( "Time range : 15 min" );
            }
            else {
                mPosToast.setText( "Time range : 24 hrs");
            }
            mPosToast.show();

            ValueAnimator animator = ValueAnimator.ofInt( mViewportWidth, vpw );
            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    setViewportWidth((int)animation.getAnimatedValue());
                    invalidate();
                }
            });
            animator.start();

            return super.onDoubleTap(e);
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            mPos += (int)( distanceX * (float)mViewportWidth / (float)mWidth );

            if (mPos < 0) {
                mEdgeRight = false ;
                mEdgeEffect.onPull( -distanceX );
                mPos = 0;
            } else if (mPos > mPosRange) {
                mEdgeRight = true ;
                mEdgeEffect.onPull( distanceX );
                mPos = mPosRange;
            }

            redraw();
            showToast();
            postSeek();

            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            fling((int) -velocityX);
            return true;
        }
    };

    private void releaseScroll() {
        mEdgeEffect.onRelease();
        mScroller.forceFinished(true);
    }

    private int mScrollStartPos;

    private void fling(int velocity) {
        releaseScroll();
        mScrollStartPos = mPos;
        mScroller.fling(0, 0, velocity, 0, -1000000, 1000000, -1000000, 1000000, 0, 0);
        redraw();
        postSeek();
    }

    private void scrollBy( int offset ) {
        int posoffset = offset * mViewportWidth / mWidth ;

        int npos = mPos + posoffset ;
        if (npos < 0) {
            npos = 0;
        } else if (npos > mPosRange) {
            npos = mPosRange;
        }
        if( npos!=mPos ) {
            releaseScroll();
            mScrollStartPos = mPos ;
            int dx = (npos-mPos) * mWidth / mViewportWidth ;
            mScroller.startScroll ( 0, 0, dx, 0 );
            redraw();
            postSeek();
        }
    }

    @Override
    public void computeScroll() {
        super.computeScroll();

        if (mScroller.computeScrollOffset()) {
            // The scroller isn't finished, meaning a fling or programmatic pan operation is
            // currently active.

            int diffPos = (int)((long)mScroller.getCurrX() * (long)mViewportWidth / mWidth) ;
            mPos = mScrollStartPos + diffPos;

            if( mPos<0 ) {
                releaseScroll();
                mEdgeEffect.onAbsorb( (int) mScroller.getCurrVelocity() );
                mPos=0 ;
            }
            else if( mPos>mPosRange ) {
                releaseScroll();
                mEdgeEffect.onAbsorb( -(int) mScroller.getCurrVelocity() );
                mPos=mPosRange ;
            }

            redraw();
            showToast();
            postSeek();
        }
        else if( !mEdgeEffect.isFinished() ) {
            redraw();
        }
    }

    private boolean mToastPending;
    // Toast of current time indicated in time bar
    //private Calendar mToastTime = Calendar.getInstance();     // make it a member so only initialize once
    private Runnable mRunToast = new Runnable() {
        @Override
        public void run() {
            Calendar toastTime = Calendar.getInstance();
            toastTime.setTimeInMillis((mPosMin + mPos)*1000);

            //String format = "yyyy-MM-dd HH:mm:ss";
            String format = "MM/dd/yyyy HH:mm:ss";
            SimpleDateFormat sdf = new SimpleDateFormat(format, Locale.US);
            mPosToast.setText(sdf.format(toastTime.getTime()));
            mPosToast.show();
            mToastPending = false;
            mTouchDelayTime = SystemClock.uptimeMillis()+3000 ;
        }
    } ;

    private void showToast(){
        if(!mToastPending) {
            mToastPending=true ;
            postDelayed(mRunToast, 50);
        }
    }

    public void setUiHandler( Handler handler ) {
        mUiHandler = handler ;
    }

    // set time bar date range
    // parameter:
    //    startDate, starting date of time bar, in bcd : yyyyMMDD
    //    endDate, ending date of time bar, in bcd : yyyyMMDD
    private void setDateRange( long startTime, long endTime ) {
        mPosMin = startTime/1000 ;
        mPosRange = (int)(endTime/1000-mPosMin) ;

        if( mPos > mPosRange ) mPos = mPosRange ;
        redraw();
    }

    // set time bar date range
    // parameter:
    //    date[]: list of dates with available videos
    public void setDateList( int date[] ) {
        mClipInfo.clear();
        mLockInfo.clear();

        if( date !=null && date.length>0 ) {
            long startTime, endTime ;
            int beginDate, endDate ;

            Calendar calendar = Calendar.getInstance();
            beginDate = date[0];
            endDate = date[0];

            for( int i=0; i< date.length; i++ ) {
                mClipInfo.put( date[i], null );
                mLockInfo.put( date[i], null );
                if( date[i]<beginDate ) beginDate = date[i];
                if( date[i]>endDate ) endDate = date[i];
            }

            calendar.clear();
            calendar.set(Calendar.YEAR, beginDate/10000) ;
            calendar.set(Calendar.MONTH, beginDate%10000/100 -1+Calendar.JANUARY ) ;
            calendar.set(Calendar.DATE, beginDate%100) ;
            startTime = calendar.getTimeInMillis() ;

            calendar.clear();
            calendar.set(Calendar.YEAR, endDate/10000) ;
            calendar.set(Calendar.MONTH, endDate%10000/100 -1+Calendar.JANUARY ) ;
            calendar.set(Calendar.DATE, endDate%100) ;
            calendar.add(Calendar.DATE, 1);
            endTime = calendar.getTimeInMillis() ;
            setDateRange(startTime,endTime) ;

        }
    }


    // get available date list
    // parameter:
    //    date[]: list of dates with available videos
    public Integer [] getDateList() {
        Integer [] dateArray =  mClipInfo.keySet().toArray( new Integer [0] );
        Arrays.sort(dateArray);
        return dateArray ;
    }

    public boolean isSeekPending(){
        return mSeekPending;
    }

    private void postSeek() {
        mSeekPending = true;
        if (mUiHandler != null) {
            mUiHandler.removeMessages(PWMessage.MSG_TB_SCROLL);
            mUiHandler.sendEmptyMessageDelayed(PWMessage.MSG_TB_SCROLL, 200) ;
        }
    }

    private long mPosX ;

    // set new current pos
    // pos: time stamp in milliseconds
    public void setPos( long pos ) {
        mPosX = pos/1000 ;

        if( mSeekPending || mTouch || SystemClock.uptimeMillis()<= mTouchDelayTime )
            return ;                 // not allow to set position when seeking

        int npos = (int) (mPosX - mPosMin);
        if (npos < 0)
            npos = 0;
        if (npos > mPosRange)
            npos = mPosRange;

        if( PosToX(npos) != mWidth / 2  ) {
            mPos = npos ;
            redraw();
        }
    }

    // get current position in milliseconds
    public long getPos() {
        if( mSeekPending ) {
            return (mPosMin + mPos) * 1000l ;
        }
        else {
            return mPosX * 1000l ;
        }
        // return mPosMin + 1000L * mPos ;
    }

    // get current position in milliseconds and clear seek pending flag
    public long getSeekPos() {
        mSeekPending = false ;
        return (mPosMin + mPos) * 1000l ;
    }

    public void setClipInfo( int date, int [] clipinfo )
    {
        mClipInfo.put(date, clipinfo);
        redraw();
    }

    public void setLockInfo( int date, int [] clipinfo )
    {
        mLockInfo.put(date, clipinfo);
        redraw();
    }

    public int getViewportWidth(){
        return mViewportWidth ;
    }

    public void setViewportWidth( int vpw ) {
        if( vpw>mViewportWidthMax )
            vpw = mViewportWidthMax ;
        if( vpw<mViewportWidthMin )
            vpw = mViewportWidthMin ;
        mViewportWidth = vpw ;
    }

}
