package com.tme_inc.pwv

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Handler
import android.os.Message
import android.os.SystemClock
import android.util.AttributeSet
import android.util.Log
import android.util.SparseArray
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.EdgeEffect
import android.widget.OverScroller
import android.widget.Toast

import java.text.SimpleDateFormat
import java.util.*

/**
 * Created by dennis on 1/29/15.
 */
class TimeBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
    )
    : View(context, attrs, defStyle) {

    // UI thread handler from hosting activity
    var uiHandler: Handler? = null

    // Edge effect / overscroll tracking objects.
    private val mEdgeEffect = EdgeEffect(context)

    private var mEdgeRight: Boolean = false        // Edge effect on right side

    // Viewport
    private val mViewportWidthMin: Int = 300                    // 5 min
    private val mViewportWidthMax: Int = 30 * 24 * 60 * 60      // 30 days
    private var mViewportWidth: Int = 3600                      // View port width in seconds, 1 hour default

    // View size ;
    private var mWidth =  600                               // Width in pixels, 600 default, will be changed by onSizeChanged()
    private var mHeight = 20

    // Minimum time (Start Time) in calendar time seconds
    private var mPosMin: Long = 0

    // Position Range, in seconds
    private var mPosRange: Int = 0
    private var mPos: Int = 0                   // center time (related to mPosMin)

    var isSeekPending: Boolean = false
        private set      // indicate a seek operating is pending

    private val mDensity = context.resources.displayMetrics.density

    // clip info cache
    private val mClipInfo = SparseArray<IntArray>()
    private val mLockInfo = SparseArray<IntArray>()

    private var mTouch: Boolean = false
    private var mTouchDelayTime: Long = 0

    private val mPosToast: Toast = Toast.makeText(context, "", Toast.LENGTH_SHORT)

    // paints and colors
    private val nColor = context.resources.getColor(R.color.timebar_ncolor)
    private val lColor = context.resources.getColor(R.color.timebar_lcolor)

    private val colorPaint = Paint()
    private val textPaint = Paint()

    // State objects and values related to gesture tracking.

    // Sets up gesture detectors, to handel multi-finger scale gestures.
    private val mScaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        /**
         * This is the active focal point in terms of the viewport. Could be a local
         * variable but kept here to minimize per-frame allocations.
         */
        private var prevSpan: Float = 0.0F

        override fun onScaleBegin(scaleGestureDetector: ScaleGestureDetector): Boolean {
            prevSpan = scaleGestureDetector.currentSpanX
            return true
        }

        override fun onScale(scaleGestureDetector: ScaleGestureDetector): Boolean {
            val span = scaleGestureDetector.currentSpanX

            mViewportWidth = (prevSpan / span * mViewportWidth).toInt()
            if (mViewportWidth > mViewportWidthMax) {
                mViewportWidth = mViewportWidthMax
            } else if (mViewportWidth < mViewportWidthMin) {
                mViewportWidth = mViewportWidthMin
            }
            prevSpan = span

            redraw()
            showToast()
            return true
        }
    })

    // The gesture listener, used for handling simple gestures such as scrolls and flings.
    private val mGestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            releaseScroll()
            redraw()
            return true
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            if (e.x < mWidth / 4) {
                scrollBy(-mWidth / 2)
            } else if (e.x > mWidth * 3 / 4) {
                scrollBy(mWidth / 2)
            }
            showToast()
            return super.onSingleTapConfirmed(e)
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            val vpw = if (mViewportWidth > 24 * 60 * 60) {
                mPosToast.setText("Time range : 24 hrs")
                24 * 60 * 60
            } else if (mViewportWidth > 60 * 60) {
                mPosToast.setText("Time range : 1 hr")
                60 * 60
            } else if (mViewportWidth > 15 * 60) {
                mPosToast.setText("Time range : 15 min")
                15 * 60
            } else {
                mPosToast.setText("Time range : 24 hrs")
                24 * 60 * 60
            }
            mPosToast.show()

            val animator = ValueAnimator.ofInt(mViewportWidth, vpw)
            animator.addUpdateListener { animation ->
                viewportWidth = animation.animatedValue as Int
                invalidate()
            }
            animator.start()

            return super.onDoubleTap(e)
        }

        override fun onScroll(
            e1: MotionEvent,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            mPos += (distanceX * mViewportWidth.toFloat() / mWidth.toFloat()).toInt()

            if (mPos < 0) {
                mEdgeRight = false
                mEdgeEffect.onPull(-distanceX)
                mPos = 0
            } else if (mPos > mPosRange) {
                mEdgeRight = true
                mEdgeEffect.onPull(distanceX)
                mPos = mPosRange
            }

            redraw()
            showToast()
            postSeek()

            return true
        }

        override fun onFling(
            e1: MotionEvent,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            fling((-velocityX).toInt())
            return true
        }
    })

    private val mScroller = OverScroller(context)

    init {
        // set Max/Min time
        mPosMin = dateToMillis(20000000) / 1000

        val timeMax = dateToMillis(20500000) / 1000

        mPos = 0
        mPosRange = (timeMax - mPosMin).toInt()

        mPosToast.setGravity(Gravity.CENTER, 0, 0)

        // label paint
        textPaint.isAntiAlias = true
        textPaint.textSize = 20f
        textPaint.textAlign = Paint.Align.CENTER
    }

    private var mScrollStartPos: Int = 0

    private var mToastPending: Boolean = false
    // Toast of current time indicated in time bar
    //private Calendar mToastTime = Calendar.getInstance();     // make it a member so only initialize once
    private val mRunToast = Runnable {
        val toastTime = Calendar.getInstance()
        toastTime.timeInMillis = (mPosMin + mPos) * 1000

        //String format = "yyyy-MM-dd HH:mm:ss";
        val format = "MM/dd/yyyy HH:mm:ss"
        val sdf = SimpleDateFormat(format, Locale.US)
        mPosToast.setText(sdf.format(toastTime.time))
        mPosToast.show()
        mToastPending = false
        mTouchDelayTime = SystemClock.uptimeMillis() + 3000
    }

    // convert time in BCD seconds to milliseconds
    private fun timeToMillis(time:Long) : Long {
        val date = (time/1000000).toInt()
        val rtime = (time%1000000).toInt()
        return GregorianCalendar(
            date / 10000,
            date % 10000 / 100 - 1 + Calendar.JANUARY,
            date % 100,
            rtime/10000,
            rtime%10000/100,
            rtime%100
        ).timeInMillis
    }

    private fun dateToMillis(date:Int) : Long {
        return GregorianCalendar(
            date / 10000,
            date % 10000 / 100 - 1 + Calendar.JANUARY,
            date % 100
        ).timeInMillis
    }

    // set date list
    // parameter:
    //    date[]: list of dates with available videos
    fun setDateList(date:IntArray) {
        mClipInfo.clear()
        mLockInfo.clear()

        if (date.size > 0)
        {
            val startTime = dateToMillis(date[0])
            val endTime = dateToMillis(date[date.size-1]) + 24*3600*1000 - 1
            setDateRange(startTime, endTime)
        }
    }

    private var mPosX: Long = 0

    // get current position in milliseconds
    // set new current pos
    // pos: time stamp in milliseconds
    // return mPosMin + 1000L * mPos ;
    // not allow to set position when seeking
    var pos: Long
        get() = if (isSeekPending) {
            (mPosMin + mPos) * 1000L
        } else {
            mPosX * 1000L
        }
        set(pos) {
            mPosX = pos / 1000

            if (isSeekPending || mTouch || SystemClock.uptimeMillis() <= mTouchDelayTime)
                return

            var npos = (mPosX - mPosMin).toInt()
            if (npos < 0)
                npos = 0
            if (npos > mPosRange)
                npos = mPosRange

            if (PosToX(npos) != mWidth / 2) {
                mPos = npos
                redraw()
            }
        }

    // get current position in milliseconds and clear seek pending flag
    val seekPos: Long
        get() {
            isSeekPending = false
            return (mPosMin + mPos) * 1000L
        }

    var viewportWidth: Int
        get() = mViewportWidth
        set(vpw) {
            mViewportWidth = vpw
            if (vpw > mViewportWidthMax)
                mViewportWidth = mViewportWidthMax
            if (vpw < mViewportWidthMin)
                mViewportWidth = mViewportWidthMin
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val minsize = 100
        setMeasuredDimension(
            Math.max(
                suggestedMinimumWidth,
                View.resolveSize(
                    minsize + paddingLeft + 1000,
                    widthMeasureSpec
                )
            ),
            Math.max(
                suggestedMinimumHeight,
                View.resolveSize(
                    minsize + paddingTop + 500,
                    heightMeasureSpec
                )
            )
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        mWidth = w
        mHeight = h

        // also change label text size
        textPaint.textSize = (mHeight / 2).toFloat()
    }

    private fun PosToX(pos: Int): Int {
        return ((pos.toLong() - mPos.toLong()) * mWidth / mViewportWidth + mWidth / 2).toInt()
    }

    private fun XToPos(x: Int): Int {
        return ((x.toLong() - mWidth / 2) * mViewportWidth / mWidth + mPos).toInt()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val posStart = XToPos(0)
        val posEnd = XToPos(mWidth)
        var i: Int

        // background, auto draw "timebar.png"
        val clipx = (mDensity * 8).toInt()
        canvas.clipRect(clipx, clipx, mWidth - clipx, mHeight - clipx)

        // draw clip bar
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = (mPosMin + posEnd) * 1000L
        val enddate = calendar.get(Calendar.YEAR) * 10000 +
                (calendar.get(Calendar.MONTH) - Calendar.JANUARY + 1) * 100 +
                calendar.get(Calendar.DATE)

        // draw clip bar
        calendar.timeInMillis = (mPosMin + posStart) * 1000L
        val y: Int
        val m: Int
        val d: Int
        y = calendar.get(Calendar.YEAR)
        m = calendar.get(Calendar.MONTH)
        d = calendar.get(Calendar.DATE)
        var date = y * 10000 +
                (m - Calendar.JANUARY + 1) * 100 +
                d

        calendar.clear()
        calendar.set(Calendar.YEAR, y)
        calendar.set(Calendar.MONTH, m)
        calendar.set(Calendar.DATE, d)

        colorPaint.color = nColor
        while (date <= enddate) {
            val posofday = (calendar.timeInMillis / 1000 - mPosMin).toInt()

            // draw N clips info
            var clipList:IntArray? = mClipInfo[date]
            if (clipList == null) {
                if (uiHandler != null) {
                    uiHandler!!.obtainMessage(MSG_TB_GETCLIPLIST, date, 0)
                        .sendToTarget()
                    mClipInfo.put(date, IntArray(0))  // to indicate get clip request sent
                }
            }
            else if (clipList.isNotEmpty() ) {
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
                    canvas.drawRect( (float)sx, 2.0f, (float)(ex+2), (float)(mHeight-2), colorPaint );
                }
                */
                val l = clipList.size
                var psx = 0
                var pex = -1
                i = 0
                while (i < l) {
                    val ex = PosToX(posofday + clipList[i + 1])
                    if (ex <= 0) {
                        i += 2
                        continue
                    }

                    val sx = PosToX(posofday + clipList[i])
                    if (sx >= mWidth) {
                        break
                    }

                    if (sx < pex + 5) {
                        // merge it
                        pex = ex
                    } else {
                        if (pex > 0) {
                            canvas.drawRect(
                                psx.toFloat(),
                                2.0f,
                                (pex + 2).toFloat(),
                                (mHeight - 2).toFloat(),
                                colorPaint
                            )
                        }
                        pex = ex
                        psx = sx
                    }
                    if (pex >= mWidth) {
                        pex = mWidth
                        break
                    }
                    i += 2
                }

                if (pex > 0) {
                    canvas.drawRect(
                        psx.toFloat(),
                        2.0f,
                        (pex + 2).toFloat(),
                        (mHeight - 2).toFloat(),
                        colorPaint
                    )
                }
            }


            // draw L clips info
            colorPaint.color = lColor
            clipList = mLockInfo[date]
            if (clipList != null && clipList.size > 1) {
                val l = clipList.size
                var psx = 0
                var pex = -1
                i = 0
                while (i < l) {
                    val ex = PosToX(posofday + clipList[i + 1])
                    if (ex <= 0) {
                        i += 2
                        continue
                    }

                    val sx = PosToX(posofday + clipList[i])
                    if (sx >= mWidth) {
                        break
                    }

                    if (sx < pex + 5) {
                        // merge it
                        pex = ex
                    } else {
                        if (pex > 0) {
                            canvas.drawRect(
                                psx.toFloat(),
                                2.0f,
                                (pex + 2).toFloat(),
                                (mHeight - 2).toFloat(),
                                colorPaint
                            )
                        }
                        pex = ex
                        psx = sx
                    }
                    if (pex >= mWidth) {
                        pex = mWidth
                        break
                    }
                    i += 2
                }

                if (pex > 0) {
                    canvas.drawRect(
                        psx.toFloat(),
                        2.0f,
                        (pex + 2).toFloat(),
                        (mHeight - 2).toFloat(),
                        colorPaint
                    )
                }
            }

            calendar.add(Calendar.DATE, 1)
            date = calendar.get(Calendar.YEAR) * 10000 +
                    (calendar.get(Calendar.MONTH) - Calendar.JANUARY + 1) * 100 +
                    calendar.get(Calendar.DATE)
        }


        // Draw time grid
        val labelWidth = textPaint.measureText("00-00  ").toInt()
        val maxLabels = mWidth / labelWidth

        var gridWidth = (posEnd - posStart) / maxLabels

        val gridwarray = intArrayOf(1, 2, 5, 10, 15, 30, 60)

        var format = "HH:mm"
        if (gridWidth < 60 * 60) {
            i = 0
            while (i < gridwarray.size) {
                if (gridWidth < gridwarray[i] * 60) {
                    gridWidth = gridwarray[i] * 60
                    break
                }
                i++
            }
        } else if (gridWidth < 20 * 60 * 60) {       // less than a day
            gridWidth = (gridWidth / (60 * 60) + 1) * 60 * 60
        } else {              // more than a day
            format = "MM/dd"
            gridWidth = (gridWidth / (24 * 60 * 60) + 1) * 24 * 60 * 60
        }

        val sdf = SimpleDateFormat(format, Locale.US)
        val descent = textPaint.descent()
        val textY = (mHeight / 2 + 10).toFloat()
        val markY = textY + descent

        colorPaint.color = 0xff000000.toInt()
        var pos = posStart - posStart % gridWidth
        while (pos < posEnd + gridWidth) {
            calendar.timeInMillis = (mPosMin + pos) * 1000L
            val text = sdf.format(calendar.time)
            val x = PosToX(pos)
            canvas.drawText(text, x.toFloat(), textY, textPaint)
            canvas.drawLine(x.toFloat(), markY, x.toFloat(), (mHeight - clipx).toFloat(), colorPaint)
            pos += gridWidth
            //canvas.drawRect( (float)(x-1), (float)(mHeight-20), (float)(x+1), (float)(mHeight-4), gridPaint );
        }

        // draw center indicator
        colorPaint.color = -1
        val dw = mDensity * 2
        canvas.drawRect(mWidth / 2 - dw, dw, mWidth / 2 + dw, mHeight - dw, colorPaint)


        // draw Edge Effect
        if (!mEdgeEffect.isFinished) {
            canvas.save()
            if (mEdgeRight) {
                canvas.translate(mWidth.toFloat(), 0f)
                canvas.rotate(90f, 0f, 0f)
            } else {
                canvas.translate(0f, mHeight.toFloat())
                canvas.rotate(-90f, 0f, 0f)
            }
            canvas.scale(1.0f, 2.0f)
            mEdgeEffect.setSize(mHeight, mHeight)
            mEdgeEffect.draw(canvas)
            canvas.restore()
        }

    }

    // to redraw slider in next animation frame
    internal fun redraw() {
        postInvalidateOnAnimation()
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        val act = event?.actionMasked
        if (act == MotionEvent.ACTION_DOWN) {
            mTouch = true
        } else if (act == MotionEvent.ACTION_UP) {
            mTouchDelayTime = SystemClock.uptimeMillis() + 3000
            mTouch = false
        }

        var retVal = mScaleGestureDetector.onTouchEvent(event)
        retVal = mGestureDetector.onTouchEvent(event) || retVal
        return retVal || super.onTouchEvent(event)
    }

    private fun releaseScroll() {
        mEdgeEffect.onRelease()
        mScroller.forceFinished(true)
    }

    private fun fling(velocity: Int) {
        releaseScroll()
        mScrollStartPos = mPos
        mScroller.fling(0, 0, velocity, 0, -1000000, 1000000, -1000000, 1000000, 0, 0)
        redraw()
        postSeek()
    }

    private fun scrollBy(offset: Int) {
        val posoffset = offset * mViewportWidth / mWidth

        var npos = mPos + posoffset
        if (npos < 0) {
            npos = 0
        } else if (npos > mPosRange) {
            npos = mPosRange
        }
        if (npos != mPos) {
            releaseScroll()
            mScrollStartPos = mPos
            val dx = (npos - mPos) * mWidth / mViewportWidth
            mScroller.startScroll(0, 0, dx, 0)
            redraw()
            postSeek()
        }
    }

    override fun computeScroll() {
        super.computeScroll()

        if (mScroller.computeScrollOffset()) {
            // The scroller isn't finished, meaning a fling or programmatic pan operation is
            // currently active.

            val diffPos = (mScroller.currX.toLong() * mViewportWidth.toLong() / mWidth).toInt()
            mPos = mScrollStartPos + diffPos

            if (mPos < 0) {
                releaseScroll()
                mEdgeEffect.onAbsorb(mScroller.currVelocity.toInt())
                mPos = 0
            } else if (mPos > mPosRange) {
                releaseScroll()
                mEdgeEffect.onAbsorb(-mScroller.currVelocity.toInt())
                mPos = mPosRange
            }

            redraw()
            showToast()
            postSeek()
        } else if (!mEdgeEffect.isFinished) {
            redraw()
        }
    }

    private fun showToast() {
        if (!mToastPending) {
            mToastPending = true
            postDelayed(mRunToast, 50)
        }
    }

    // set time bar date range
    // parameter:
    //    startDate, starting date of time bar, in bcd : yyyyMMDD
    //    endDate, ending date of time bar, in bcd : yyyyMMDD
    private fun setDateRange(startTime: Long, endTime: Long) {
        mPosMin = startTime / 1000
        mPosRange = (endTime / 1000 - mPosMin).toInt()

        if (mPos > mPosRange) mPos = mPosRange
        redraw()
    }

    private fun postSeek() {
        isSeekPending = true
        if (uiHandler != null) {
            uiHandler!!.removeMessages(MSG_TB_SCROLL)
            uiHandler!!.sendEmptyMessageDelayed(MSG_TB_SCROLL, 200)
        }
    }

    fun setClipInfo(date: Int, clipinfo: IntArray) {
        mClipInfo.put(date, clipinfo)
        redraw()
    }

    fun setLockInfo(date: Int, clipinfo: IntArray) {
        mLockInfo.put(date, clipinfo)
        redraw()
    }

}
