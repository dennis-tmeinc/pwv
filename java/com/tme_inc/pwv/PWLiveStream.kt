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

    internal inner class PWLiveThread : Thread() {
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
                        var ans: DvrClient.Ans

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
                        ans = dvrClient.request(14, mChannel)
                        if (ans.databuf != null && ans.code == 11) {
                            val bb = ByteBuffer.wrap(ans.databuf)
                            bb.order(ByteOrder.LITTLE_ENDIAN)
                            if (bb.getInt(0) != 0)
                                resolution = bb.getInt(68)
                            if (digester != null)
                                digester.update(ans.databuf)
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
                            if (ans.databuf != null) {
                                if (digester != null)
                                    digester.update(ans.databuf)
                                mChannelNames = Array<String?>(totalChannels,{""})
                                synchronized(this) {
                                    for (i in 0 until totalChannels) {
                                        mChannelNames[i] = String(
                                            ans.databuf!!,
                                            72 * i + 8,
                                            64,
                                            Charsets.UTF_8
                                        ).split("\u0000".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0]
                                    }
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
                        if (ans.code == 211 && ans.databuf != null && ans.databuf!!.size >= 28) {         // ANS2TIME
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
                                if (ans.code == 202 && ans.databuf!=null) {                           // ANSSTREAMDATA
                                    if (ans.data == 10 && ans.size == 40) {      // FRAMETYPE_264FILEHEADER
                                        setheader(ans.databuf!!)
                                        onHeader(ByteBuffer.wrap(ans.databuf))
                                    } else {
                                        onReceiveFrame(ByteBuffer.wrap(ans.databuf))
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
