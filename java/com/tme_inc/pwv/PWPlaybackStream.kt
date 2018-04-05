package com.tme_inc.pwv

import android.app.Activity
import android.os.Handler
import android.os.HandlerThread
import android.os.Message

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.Calendar
import java.util.GregorianCalendar


/**
 * Created by dennis on 1/21/15.
 */
class PWPlaybackStream(channel: Int, private var mUIHandler: Handler?) : PWStream(channel) {

    var hashId: String = "c?0"

    private var mPlaybackHandle: Int = 0    // DVR handle for playback
    private val mClient: DvrClient

    var dayList = intArrayOf()
        private set

    private var m_eos = false

    private var mThread: HandlerThread? = null
    private var mPWHandler: Handler? = null

    override val isRunning: Boolean
        get() = mPWHandler != null

    val isEndOfStream: Boolean
        get() = if (m_eos && !videoAvailable()) {
            true
        } else false

    init {
        mClient = DvrClient()

        // start Handler thread
        mThread = object : HandlerThread("PWPLAYER") {
            override fun onLooperPrepared() {
                super.onLooperPrepared()

                mPWHandler = object : Handler() {
                    override fun handleMessage(msg: Message) {
                        super.handleMessage(msg)
                        handlePWMessage(msg)
                    }
                }
                mPWHandler!!.sendEmptyMessage(PWMessage.MSG_PW_CONNECT)
            }

        }
        mThread!!.start()
    }

    // start get video frames
    override fun start() {
        super.start()

        // wait until thread is actually started
        mThread!!.looper
        if (isRunning) {
            mPWHandler!!.sendEmptyMessage(PWMessage.MSG_PW_GETFRAME)
        }
    }

    override fun release() {
        super.release()

        if (isRunning) {
            mPWHandler!!.sendEmptyMessage(PWMessage.MSG_PW_QUIT)     // send quit message
        }
        mUIHandler = null
    }

    // seek to specified time, time in miliiseconds
    fun seek(time: Long) {
        if (isRunning) {
            val sdate: Int
            val stime: Int

            if (time < 10000 && dayList.size > 0) {
                // seek to last date contain video
                sdate = dayList[dayList.size - 1]
                stime = 0
            } else {
                val calendar = Calendar.getInstance()
                calendar.clear()
                calendar.timeInMillis = time
                sdate = calendar.get(Calendar.YEAR) * 10000 +
                        (calendar.get(Calendar.MONTH) - Calendar.JANUARY + 1) * 100 +
                        calendar.get(Calendar.DATE)
                stime = calendar.get(Calendar.HOUR_OF_DAY) * 10000 +
                        calendar.get(Calendar.MINUTE) * 100 +
                        calendar.get(Calendar.SECOND)
            }

            mPWHandler!!.obtainMessage(PWMessage.MSG_PW_SEEK, sdate, stime).sendToTarget()
        }

    }

    // date in yyyyMMDD
    fun getClipList(date: Int) {
        if (isRunning) {
            mPWHandler!!.obtainMessage(PWMessage.MSG_PW_GETCLIPLIST, date, 0).sendToTarget()
        }
    }

    // handle msg on PW Thread
    internal fun handlePWMessage(msg: Message) {
        when (msg.what) {
            PWMessage.MSG_PW_QUIT  // quit thread
            -> {
                if (connect() && mPlaybackHandle != 0) {
                    // to close stream handle before close connection
                    //  send REQSTREAMCLOSE (203)
                    mClient.request(203, mPlaybackHandle)
                }

                mPWHandler = null
                mThread!!.quit()
                mThread = null
                mClient.close()
            }

            PWMessage.MSG_PW_CONNECT -> if (connect()) {
                var digester: MessageDigest?
                try {
                    digester = MessageDigest.getInstance("MD5")
                } catch (e: NoSuchAlgorithmException) {
                    e.printStackTrace()
                    digester = null
                }

                // get Channel setup
                // struct  DvrChannel_attr {
                //  int         Enable ;
                //  char        CameraName[64] ;
                //  int         Resolution;	// 0: CIF, 1: 2CIF, 2:DCIF, 3:4CIF, 4:QCIF
                //    ...
                // }
                //
                //  send REQGETCHANNELSETUP
                var ans: DvrClient.Ans = mClient.request(14, mChannel)
                if (ans.databuf != null && ans.code == 11) {
                    val bb = ByteBuffer.wrap(ans.databuf)
                    bb.order(ByteOrder.LITTLE_ENDIAN)
                    if (bb.getInt(0) != 0) {
                        resolution = bb.getInt(68)
                    }
                    if (digester != null)
                        digester.update(ans.databuf)
                }

                // get Channel Info
                //struct channel_info {
                //    int Enable ;
                //    int Resolution ;
                //    char CameraName[64] ;
                //} ;
                //  send REQCHANNELINFO
                ans = mClient.request(4)
                if (ans.size > 0 && ans.code == 7) {
                    totalChannels = ans.data
                    if (totalChannels > 0) {
                        if (digester != null)
                            digester.update(ans.databuf)
                        mChannelNames = arrayOfNulls(totalChannels)
                        for (i in 0 until totalChannels) {
                            mChannelNames[i] = String(
                                ans.databuf!!,
                                72 * i + 8,
                                64
                            ).split("\u0000".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0]
                        }
                    }
                }

                if (mPlaybackHandle != 0) {
                    // get daylist

                    //  REQSTREAMDAYLIST   (214)
                    ans = mClient.request(214, mPlaybackHandle)
                    if (ans.code == 207 && ans.databuf != null) {    //ANSSTREAMDAYLIST : 207
                        val b = ByteBuffer.wrap(ans.databuf)
                        b.order(ByteOrder.LITTLE_ENDIAN)
                        val s = ans.size / 4
                        dayList = IntArray(s)
                        for (i in 0 until s) {
                            dayList[i] = b.getInt(i * 4)
                        }
                    }

                }

                hashId = "C$mChannel."
                if (digester != null) {
                    val dg = digester.digest()
                    for (j in dg.indices) {
                        hashId += String.format("%02x", dg[j])
                    }
                }

                if (mUIHandler != null) {
                    mUIHandler!!.obtainMessage(PWMessage.MSG_PW_CONNECT).sendToTarget()
                }

            }

            PWMessage.MSG_PW_GETFRAME -> if (connect() && mPlaybackHandle != 0) {
                if (mVideoFrameQueue.size < 100) {

                    // REQ2STREAMGETDATAEX (233)
                    val ans = mClient.request(233, mPlaybackHandle)
                    if (ans.code == 218 && ans.size > 32) {    //ANS2STREAMDATAEX : 218
                        if (ans.databuf != null) {
                            val bb = ByteBuffer.wrap(ans.databuf)
                            bb.order(ByteOrder.LITTLE_ENDIAN)

                            // response contain header of structure dvrtime
                            val cal = GregorianCalendar(
                                bb.int,
                                bb.int - 1 + Calendar.JANUARY,
                                bb.int,
                                bb.int,
                                bb.int,
                                bb.int
                            )
                            resetTimestamp(cal.timeInMillis + bb.int)

                            bb.order(ByteOrder.BIG_ENDIAN)
                            bb.position(32)
                            onReceiveFrame(bb)
                        }
                    } else {
                        // end of stream?
                        m_eos = true
                        return
                    }
                    mPWHandler!!.sendEmptyMessage(PWMessage.MSG_PW_GETFRAME)
                } else {
                    if (!mPWHandler!!.hasMessages(PWMessage.MSG_PW_GETFRAME)) {
                        mPWHandler!!.sendEmptyMessageDelayed(PWMessage.MSG_PW_GETFRAME, 1000)
                    }
                }
            } else {
                mPWHandler!!.sendEmptyMessageDelayed(PWMessage.MSG_PW_CONNECT, 10000)
            }

            PWMessage.MSG_PW_SEEK -> if (connect() && mPlaybackHandle != 0) {
                // clear all queued frames
                clearQueue()

                val date = msg.arg1
                val time = msg.arg2

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
                val dvrtime = ByteBuffer.allocate(32)
                dvrtime.order(ByteOrder.LITTLE_ENDIAN)
                dvrtime.putInt(0, date / 10000)
                dvrtime.putInt(4, date % 10000 / 100)
                dvrtime.putInt(8, date % 100)
                dvrtime.putInt(12, time / 10000)
                dvrtime.putInt(16, time % 10000 / 100)
                dvrtime.putInt(20, time % 100)
                dvrtime.putInt(24, 0)
                dvrtime.putInt(28, 0)

                val ans = mClient.request(204, mPlaybackHandle, dvrtime.array())

                m_eos = false
                if (ans.code == 2 && ans.size >= 40 && ans.databuf != null) {    // ANSOK : 2
                    // treat file header as a frame
                    onReceiveFrame(ByteBuffer.wrap(ans.databuf))
                }

                if (mUIHandler != null) {
                    mUIHandler!!.obtainMessage(PWMessage.MSG_PW_SEEK).sendToTarget()
                }
            }

            PWMessage.MSG_PW_GETCLIPLIST -> if (connect() && mPlaybackHandle != 0) {

                var clipInfo = intArrayOf()
                var lockInfo = intArrayOf()

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
                val dvrtime = ByteBuffer.allocate(32)
                dvrtime.order(ByteOrder.LITTLE_ENDIAN)
                dvrtime.putInt(0, msg.arg1 / 10000)           // year
                dvrtime.putInt(4, msg.arg1 / 100 % 100)      // month
                dvrtime.putInt(8, msg.arg1 % 100)            // day
                var ans: DvrClient.Ans = mClient.request(210, mPlaybackHandle, dvrtime.array())
                if (ans.code == 204) {    //ANSSTREAMDAYINFO : 204
                    // struct dayinfoitem {
                    //    int ontime  ;		// seconds of the day
                    //    int offtime ;		// seconds of the day
                    // } ;
                    if (ans.databuf != null) {
                        val bb = ByteBuffer.wrap(ans.databuf)
                        bb.order(ByteOrder.LITTLE_ENDIAN)
                        val s = ans.size / 8
                        if (s > 0) {
                            clipInfo = IntArray(s * 2)
                            var i = 0
                            while (i < s * 2) {
                                clipInfo[i] = bb.getInt(i * 4)
                                clipInfo[i + 1] = bb.getInt((i + 1) * 4)
                                i += 2
                            }
                        }
                    }
                }

                if (mUIHandler != null) {
                    mUIHandler!!.obtainMessage(
                        PWMessage.MSG_PW_GETCLIPLIST,
                        msg.arg1,
                        1,
                        clipInfo as Any
                    ).sendToTarget()
                }

                // REQLOCKINFO   (212)
                ans = mClient.request(212, mPlaybackHandle, dvrtime.array())

                if (ans.code == 204) {    //ANSSTREAMDAYINFO : 204
                    // struct dayinfoitem {
                    //    int ontime  ;		// seconds of the day
                    //    int offtime ;		// seconds of the day
                    // } ;
                    if (ans.databuf != null) {
                        val bb = ByteBuffer.wrap(ans.databuf)
                        bb.order(ByteOrder.LITTLE_ENDIAN)
                        val s = ans.size / 8
                        if (s > 0) {
                            lockInfo = IntArray(s * 2)
                            var i = 0
                            while (i < s * 2) {
                                lockInfo[i] = bb.getInt(i * 4)
                                lockInfo[i + 1] = bb.getInt((i + 1) * 4)
                                i += 2
                            }
                        }
                    }
                }

                if (mUIHandler != null) {
                    mUIHandler!!.obtainMessage(
                        PWMessage.MSG_PW_GETCLIPLIST,
                        msg.arg1,
                        2,
                        lockInfo as Any
                    ).sendToTarget()
                }
            } else {
                mPWHandler!!.sendEmptyMessage(PWMessage.MSG_PW_CONNECT)
            }
        }
    }

    private fun connect(): Boolean {
        if (!mClient.isConnected || mPlaybackHandle == 0) {
            mClient.close()
            mClient.connect()
            mPlaybackHandle = 0 // reset handle

            if (mClient.isConnected) {
                // verify tvs key
                checkTvsKey(mClient)

                //  REQSTREAMOPEN   (201)
                val ans = mClient.request(201, mChannel)
                if (ans.code == 201) {        //ANSSTREAMOPEN : 201
                    mPlaybackHandle = ans.data
                    if (ans.size >= 40) {
                        // this is 40 bytes file header
                        onReceiveFrame(ByteBuffer.wrap(ans.databuf))
                    }
                }
            }
        }
        return mClient.isConnected
    }


}
