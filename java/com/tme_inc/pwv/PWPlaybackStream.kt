package com.tme_inc.pwv

import android.os.Handler
import android.os.HandlerThread
import android.os.Message

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.Calendar
import java.util.GregorianCalendar


/**
 * Created by dennis on 1/21/15.
 */
class PWPlaybackStream(channel: Int, val uiHandler: Handler) : PWStream(channel) {

    private val mClient: DvrClient = DvrClient()

    private val playbackHandler =  {
        val t = HandlerThread("playback stream")
        t.start()
        Handler(t.looper) {
            handlePWMessage(it)
        }
    }()

    // start get video frames
    override fun start() {
        super.start()
        isRunning = true
        playbackHandler.sendEmptyMessage(MSG_PW_CONNECT)
    }

    override fun release() {
        super.release()
        isRunning = false
        playbackHandler.sendEmptyMessage(MSG_PW_QUIT)     // send quit message
    }

    // seek to specified time, time in miliiseconds
    fun seek(time: Long) {
        val sdate: Int
        val stime: Int

        if (time < 10000 && dayList.isNotEmpty() ) {
            // seek to last date contain video
            sdate = dayList.last()
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

        playbackHandler.obtainMessage(MSG_PW_SEEK, sdate, stime).sendToTarget()
    }

    // date in yyyyMMDD
    fun getClipList(date: Int) {
        playbackHandler.obtainMessage(MSG_PW_GETCLIPLIST, date, 0).sendToTarget()
    }

    private var playbackHandle: Int = 0    // DVR handle for playback

    private var mEOS = false

    val isEndOfStream: Boolean
        get() = (mEOS && !videoAvailable())

    // handle msg on PW Thread
    internal fun handlePWMessage(msg: Message) : Boolean {
        when (msg.what) {
            MSG_PW_QUIT  // quit thread
            -> {
                if(mClient.isConnected && playbackHandle != 0) {
                    // to close stream handle before close connection
                    //  send REQSTREAMCLOSE (203)
                    mClient.request(203, playbackHandle)
                }
                mClient.close()

                (Thread.currentThread() as HandlerThread).quitSafely()
                isRunning = false
            }

            MSG_PW_CONNECT -> if (connect()) {
                val digester= MessageDigest.getInstance("MD5")

                // get Channel setup
                // struct  DvrChannel_attr {
                //  int         Enable ;
                //  char        CameraName[64] ;
                //  int         Resolution;	// 0: CIF, 1: 2CIF, 2:DCIF, 3:4CIF, 4:QCIF
                //    ...
                // }
                //
                //  send REQGETCHANNELSETUP
                var ans = mClient.request(14, mChannel)
                if ( ans.code == 11 && ans.size > 0 ) {
                    ans.dataBuffer.order(ByteOrder.LITTLE_ENDIAN)
                    if (ans.dataBuffer.getInt(0) != 0) {
                        resolution = ans.dataBuffer.getInt(68)
                    }
                    digester?.update(ans.dataBuffer.array())
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
                    mChannelNames.clear()
                    for (i in 0 until totalChannels) {
                        mChannelNames.add( CString(
                            ans.dataBuffer,
                            72 * i + 8,
                            64) )
                    }
                }

                if (playbackHandle != 0) {
                    // get daylist

                    //  REQSTREAMDAYLIST   (214)
                    ans = mClient.request(214, playbackHandle)
                    if (ans.code == 207 && ans.size > 0) {    //ANSSTREAMDAYLIST : 207
                        ans.dataBuffer.order(ByteOrder.LITTLE_ENDIAN)
                        val s = ans.size / 4
                        dayList.clear()
                        for (i in 0 until s) {
                            dayList.add(ans.dataBuffer.getInt(i * 4))
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

                uiHandler.sendEmptyMessage(MSG_PW_CONNECT)

            }

            MSG_PW_GETFRAME -> {
                if( connect() ) {
                    if ( mVideoFrameQueue.size < 100) {

                        // REQ2STREAMGETDATAEX (233)
                        val ans = mClient.request(233, playbackHandle)
                        if (ans.code == 218 && ans.size > 32) {    //ANS2STREAMDATAEX : 218
                            ans.dataBuffer.order(ByteOrder.LITTLE_ENDIAN)

                            // response contain header of structure dvrtime
                            val cal = GregorianCalendar(
                                ans.dataBuffer.int,
                                ans.dataBuffer.int - 1 + Calendar.JANUARY,
                                ans.dataBuffer.int,
                                ans.dataBuffer.int,
                                ans.dataBuffer.int,
                                ans.dataBuffer.int
                            )
                            resetTimestamp(cal.timeInMillis + ans.dataBuffer.int)

                            ans.dataBuffer.order(ByteOrder.BIG_ENDIAN)
                            ans.dataBuffer.position(32)
                            onReceiveFrame(ans.dataBuffer)
                        } else {
                            // end of stream?
                            mEOS = true
                        }
                        if (!playbackHandler.hasMessages(MSG_PW_GETFRAME) && !mEOS ) {
                            playbackHandler.sendEmptyMessage(MSG_PW_GETFRAME)
                        }
                    } else {
                        if (!playbackHandler.hasMessages(MSG_PW_GETFRAME)) {
                            playbackHandler.sendEmptyMessageDelayed(MSG_PW_GETFRAME, 1000)
                        }
                    }
                } else {
                    playbackHandler.sendEmptyMessageDelayed(MSG_PW_CONNECT, 10000)
                }
            }

            MSG_PW_SEEK -> if (connect()) {

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

                val ans = mClient.request(204, playbackHandle, dvrtime)

                mEOS = false
                if (ans.code == 2 ) {
                    if (ans.size >= 40) {    // ANSOK : 2
                        // treat file header as a frame
                        onReceiveFrame(ans.dataBuffer)
                    }
                }

                playbackHandler.sendEmptyMessage(MSG_PW_GETFRAME)
                uiHandler.sendEmptyMessage(MSG_PW_SEEK)

            }

            MSG_PW_GETCLIPLIST -> if (connect()) {

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
                var ans = mClient.request(210, playbackHandle, dvrtime)
                if (ans.code == 204 && ans.size > 0) {    //ANSSTREAMDAYINFO : 204
                    // struct dayinfoitem {
                    //    int ontime  ;		// seconds of the day
                    //    int offtime ;		// seconds of the day
                    // } ;
                        ans.dataBuffer.order(ByteOrder.LITTLE_ENDIAN)
                        val s = ans.size / 8
                        if (s > 0) {
                            clipInfo = IntArray(s * 2)
                            var i = 0
                            while (i < s * 2) {
                                clipInfo[i] = ans.dataBuffer.getInt(i * 4)
                                clipInfo[i + 1] = ans.dataBuffer.getInt((i + 1) * 4)
                                i += 2
                            }
                        }
                }

                uiHandler.obtainMessage(
                    MSG_PW_GETCLIPLIST,
                    msg.arg1,
                    1,
                    clipInfo as Any
                ).sendToTarget()

                // REQLOCKINFO   (212)
                ans = mClient.request(212, playbackHandle, dvrtime)

                if (ans.code == 204 && ans.size > 0) {    //ANSSTREAMDAYINFO : 204
                    // struct dayinfoitem {
                    //    int ontime  ;		// seconds of the day
                    //    int offtime ;		// seconds of the day
                    // } ;
                    ans.dataBuffer.order(ByteOrder.LITTLE_ENDIAN)
                    val s = ans.size / 8
                    if (s > 0) {
                        lockInfo = IntArray(s * 2)
                        var i = 0
                        while (i < s * 2) {
                            lockInfo[i] = ans.dataBuffer.getInt(i * 4)
                            lockInfo[i + 1] = ans.dataBuffer.getInt((i + 1) * 4)
                            i += 2
                        }
                    }
                }

                uiHandler.obtainMessage(
                    MSG_PW_GETCLIPLIST,
                    msg.arg1,
                    2,
                    lockInfo as Any
                ).sendToTarget()

            } else {
                playbackHandler.sendEmptyMessage(MSG_PW_CONNECT)
            }
        }
        return true
    }

    private fun connect(): Boolean {
        if (!mClient.isConnected || playbackHandle == 0) {
            mClient.close()
            mClient.connect()
            playbackHandle = 0 // reset handle

            if (mClient.isConnected) {
                // verify tvs key
                checkTvsKey(mClient)

                //  REQSTREAMOPEN   (201)
                val ans = mClient.request(201, mChannel)
                if (ans.code == 201) {        //ANSSTREAMOPEN : 201
                    playbackHandle = ans.data
                    if (ans.size >= 40) {
                        // this is 40 bytes file header
                        onReceiveFrame(ans.dataBuffer)
                    }
                }
            }
        }
        return mClient.isConnected && playbackHandle != 0
    }


}
