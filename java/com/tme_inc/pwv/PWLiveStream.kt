package com.tme_inc.pwv

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.media.MediaCodec
import android.util.Log

import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.Arrays
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.LinkedList
import java.util.Queue

/**
 * Network stream from DVR for live view
 * * Created by dennis on 12/22/14.
 */
class PWLiveStream(channel: Int) : PWStream(channel) {

    var hashId: String = "c?0"
    private var mThread: Thread? = null

    override val isRunning: Boolean
        get() = mThread != null && mThread!!.isAlive

    inner class PWLiveThread : Thread() {
        var mRunning: Boolean = false

        override fun run() {
            mRunning = true

            val dvrClient = DvrClient()

            while (mRunning && ! isInterrupted) {
                if (!dvrClient.connect()) {
                    try {
                        Thread.sleep(10000)
                    } catch (e: InterruptedException) {
                        mRunning = false
                    }

                    continue
                } else {
                    try {
                        var digester: MessageDigest?
                        try {
                            digester = MessageDigest.getInstance("MD5")
                        } catch (e: NoSuchAlgorithmException) {
                            e.printStackTrace()
                            digester = null
                        }

                        // verify tvs key
                        checkTvsKey(dvrClient)

                        // get Channel setup
                        // struct  DvrChannel_attr {
                        //  int         Enable ;
                        //  char        CameraName[64] ;
                        //  int         Resolution;	// 0: CIF, 1: 2CIF, 2:DCIF, 3:4CIF, 4:QCIF
                        //    ...
                        // }
                        //
                        //  send REQGETCHANNELSETUP
                        var ans = dvrClient.request(14, mChannel)
                        if (ans.size>0 && ans.code == 11) {
                            ans.dataBuffer.order(ByteOrder.LITTLE_ENDIAN)
                            if (ans.dataBuffer.getInt(0) != 0)
                                resolution = ans.dataBuffer.getInt(68)
                            if (digester != null)
                                digester.update(ans.dataBuffer.array())
                        }

                        totalChannels = 8     // default to 8 channels ^^
                        // get Channel Info
                        //struct channel_info {
                        //    int Enable ;
                        //    int Resolution ;
                        //    char CameraName[64] ;
                        //} ;
                        //  send REQCHANNELINFO
                        ans = dvrClient.request(4)
                        if (ans.code == 7) {
                            totalChannels = ans.data
                            mChannelNames.clear()
                            if (ans.size > 0) {
                                for (i in 0 until totalChannels) {
                                    mChannelNames.add( String(
                                        ans.dataBuffer.array(),
                                        ans.dataBuffer.arrayOffset() + ans.dataBuffer.position() + 72 * i + 8,
                                        64
                                        ).split("\u0000")[0].trim() )
                                }
                            }
                        }

                        if (resolution < 0 || mChannel < 0 || mChannel >= totalChannels) {
                            // failed on such channel
                            mRunning = false
                            continue
                        }

                        hashId = "C$mChannel."
                        if (digester != null) {
                            val dg = digester.digest()
                            for (j in dg.indices) {
                                hashId += String.format("%02x", dg[j])
                            }
                        }

                        // REQ2GETLOCALTIME
                        ans = dvrClient.request(218)
                        if (ans.code == 211 && ans.size >= 28) {         // ANS2TIME
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
                        }

                        // send REQOPENLIVE packet
                        // struct dvr_req {
                        //      int reqcode;
                        //      int data;
                        //      int reqsize;
                        //  };
                        //  send REQOPENLIVE
                        ans = dvrClient.request(215, mChannel)
                        if (ans.code == 201)
                            while (mRunning && dvrClient.isConnected && !isInterrupted) {
                                // read dvr ans header
                                ans = dvrClient.recvAns()
                                if (ans.code == 202 && ans.size>0) {                           // ANSSTREAMDATA
                                    if (ans.data == 10 && ans.size == 40) {      // FRAMETYPE_264FILEHEADER
                                        setheader(ans.dataBuffer.array())
                                        onHeader(ans.dataBuffer)
                                    } else {
                                        onReceiveFrame(ans.dataBuffer)
                                    }
                                } else {                // ANSSTREAMOPEN
                                    break
                                }
                            }

                        dvrClient.close()

                    } catch (e: IOException) {
                        mRunning = false
                    }

                }
            }
            dvrClient.close()
        }

        override fun interrupt() {
            mRunning = false
            super.interrupt()
        }
    }

    override fun start() {
        super.start()
        mMaxQueue = 50
        mThread = PWLiveThread()
        mThread!!.start()
    }

    override fun release() {
        super.release()
        if (isRunning) {
            mThread!!.interrupt()
        }
        mThread = null
    }

}
