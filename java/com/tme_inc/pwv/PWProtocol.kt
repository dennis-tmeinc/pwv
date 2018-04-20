package com.tme_inc.pwv

import android.app.Activity
import android.content.Context
import android.os.AsyncTask
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.ImageButton

import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.HashSet
import java.util.TreeSet

/**
 * Created by dennis on 1/13/15.
 * Use AsyncTask to complete DVR requests
 */
class PWProtocol : DvrClient() {

    protected var mTask: PWTask<*>? = null

    val isBusy: Boolean
        get() = mTask != null

    fun cancel() {
        close()
        if (mTask != null) {
            mTask!!.cancel(true)
            mTask = null
        }
    }

    protected abstract inner class PWTask<T1>(protected val mlistener:  ( Bundle ) -> Unit )
        : AsyncTask<T1, String, Bundle>() {

        override fun onPreExecute() {
            super.onPreExecute()
            mTask = this
        }

        override fun onPostExecute(result: Bundle?) {
            super.onPostExecute(result)
            if( result != null ) {
                mlistener(result)
            }
            mTask = null
        }
    }

    private inner class GetVriTask(listener: ( Bundle ) -> Unit  ) : PWTask<Void>(listener) {

        override fun doInBackground(vararg avoid: Void): Bundle {
            var result: Bundle? = null
            var vri_s = 0
            var vri_rsize = 468

            result = Bundle()
            var ans: DvrClient.Ans
            // REQ:
            // REQGETDATA   (302)
            // PROTOCOL_PW_GETVRILISTSIZE	(1004)
            ans = request(302, 1004)
            // ANS:
            // ANSGETDATA   (302)
            if (ans.code == 302 && ans.size > 0) {
                val vrisize = String(
                    ans.dataBuffer.array(),
                    0,
                    ans.size
                ).split("\u0000")[0]
                val vris =
                    vrisize.split(",")
                if (vris.size > 0) {
                    vri_s = Integer.parseInt(vris[0])
                    result.putInt("VriListSize", vri_s)
                }
                if (vris.size > 1) {
                    vri_rsize = Integer.parseInt(vris[1])
                    result.putInt("VriItemSize", vri_rsize)
                }
            }

            // REQ:
            // REQGETDATA   (302)
            // PROTOCOL_PW_GETVRILIST	(1005)
            ans = request(302, 1005)
            // ANS:
            // ANSGETDATA   (302)
            if (ans.code == 302 && ans.size >= vri_rsize) {
                vri_s = ans.size / vri_rsize
                result.putInt("VriListSize", vri_s)
                result.putByteArray("VriList", ans.dataBuffer.array())
            }

            return result
        }
    }

    fun GetVri(complete: ( Bundle ) -> Unit ) {
        GetVriTask(complete).execute()
    }

    private inner class SetVriTask(listener: ( Bundle ) -> Unit ) : PWTask<ByteArray>(listener) {

        override fun doInBackground(vararg vridata: ByteArray): Bundle? {
            var result: Bundle? = null
            if (vridata.size > 0 && vridata[0].size > 0) {
                result = Bundle()
                // REQ:
                // REQSENDDATA   (301)
                // PROTOCOL_PW_SETVRILIST		(1006)
                val ans = request(301, 1006, ByteBuffer.wrap(vridata[0]))

                // ANS:
                // ANSOK   (2)
                if (ans.code == 2) {
                    result.putInt("Result", 1)
                }

            }
            return result
        }
    }

    fun SetVri(vridata: ByteArray, complete: ( Bundle ) -> Unit = {} ) {
        SetVriTask(complete).execute(vridata)
    }


    private inner class GetOfficerIDListTask(listener: ( Bundle ) -> Unit ) : PWTask<Void>(listener) {

        override fun doInBackground(vararg aVoid: Void): Bundle? {
            var result: Bundle? = null

            // REQ:
            // REQGETDATA   (302)
            // PROTOCOL_PW_GETPOLICEIDLIST	(1002)
            val ans = request(302, 1002)

            // ANS:
            // ANSGETDATA   (302)
            if (ans.code == 302 && ans.size > 0) {
                val nid = ans.data
                val idlen = ans.size / nid
                result = Bundle()
                result.putInt("policeId_number", ans.data)
                result.putInt("policeId_size", ans.size)
                result.putByteArray("policeId_list", ans.dataBuffer.array())
            }

            return result
        }

    }

    fun GetOfficerIDList(complete: ( Bundle ) -> Unit ) {
        GetOfficerIDListTask(complete).execute()
    }


    private inner class SetOfficerIdTask(listener: ( Bundle ) -> Unit ) : PWTask<String>(listener) {

        override fun doInBackground(vararg oids: String): Bundle? {
            var result: Bundle? = null
            if (oids.size > 0 && oids[0] != null) {
                val officerId = oids[0] + "\u0000"   // append null terminate
                val oidarray = officerId.toByteArray()

                result = Bundle()
                // REQ:
                // REQSENDDATA   (301)
                // PROTOCOL_PW_SETPOLICEID		(1003)
                val ans = request(301, 1003, ByteBuffer.wrap(oidarray))

                // ANS:
                // ANSOK   (2)
                if (ans.code == 2) {
                    result.putInt("Result", 1)
                }
            }
            return result
        }
    }

    fun SetOfficerId(officerId: String, complete: ( Bundle ) -> Unit = {} ) {
        SetOfficerIdTask(complete).execute(officerId)
    }

    //
    private inner class SendPWKeyTask(listener: ( Bundle ) -> Unit ) : PWTask<Int>(listener) {

        override fun doInBackground(vararg keys: Int?): Bundle? {
            var result: Bundle? = null
            if (keys.size > 0) {
                result = Bundle()
                // REQ:
                // REQ2KEYPAD   (230)
                val ans = request(230, keys[0]!!)

                // ANS:
                // ANSOK   (2)
                if (ans.code == 2) {
                    result.putInt("Result", 1)
                }
            }
            return result
        }
    }

    fun SendPWKey(keycode: Int, complete: ( Bundle ) -> Unit = {} ) {
        SendPWKeyTask(complete).execute(keycode)
    }

    //
    private inner class SetCovertTask : PWTask<Boolean>({}) {

        override fun doInBackground(vararg covs: Boolean?): Bundle {
            if (covs.size > 0) {
                val cov = ByteArray(4)
                if (covs[0]!!) {
                    cov[0] = 1
                } else {
                    cov[0] = 0
                }

                // REQ:
                // REQSENDDATA   (301)
                // PROTOCOL_PW_SETCOVERTMODE		(1009)
                request(301, 1009, ByteBuffer.wrap(cov))

                // ANS: (ignored)
                // ANSOK   (2)\
            }
            return Bundle()
        }
    }

    fun SetCovertMode(covert: Boolean) {
        SetCovertTask().execute(covert)
    }

    //
    private inner class GetPWStatusTask(listener: ( Bundle ) -> Unit ) : PWTask<Void>(listener) {

        override fun doInBackground(vararg avoid: Void): Bundle? {
            var result: Bundle? = null
            result = Bundle()

            // REQ:
            // REQGETDATA   (302)
            // PROTOCOL_PW_GETSTATUS   	(1001)
            var ans: DvrClient.Ans = request(302, 1001)

            // ANS:
            // ANSGETDATA   (302)
            if (ans.code == 302 && ans.size > 0) {
                result.putByteArray("PWStatus", ans.dataBuffer.array())
            }

            // REQ:
            // REQGETDATA   (302)
            // PROTOCOL_PW_GETDISKINFO   	(1010)
            ans = request(302, 1010)

            // ANS:
            // ANSGETDATA   (302)
            if (ans.code == 302 && ans.size > 0) {
                val warning = String(ans.dataBuffer.array())
                result.putString("DiskInfo", warning.trim { it <= ' ' })
            }

            /*
            // REQ:
            // REQGETDATA   (302)
            // PROTOCOL_PW_GETWARNINGMSG   	(1008)

            ans = request( 302, 1008 ) ;

            // ANS:
            // ANSGETDATA   (302)
            if( ans.code==302 && ans.size>0 ){
                String warning = new String(ans.databuf);
                result.putString("Warningmsg", warning.trim());
            }
            */

            return result
        }
    }

    fun GetPWStatus(complete: ( Bundle ) -> Unit ) {
        GetPWStatusTask(complete).execute()
    }

    // Remote Login
    private inner class RemoteLoginTask(listener: ( Bundle ) -> Unit ) : PWTask<String>(listener) {

        override fun doInBackground(vararg params: String): Bundle? {
            val result = Bundle()
            var res = true

            // connect to remote server directly
            if (connect(loginServer!!, loginPort)) {
                var nonce = "nonce"

                sendLine("session\n")
                var fields = recvLine().split(Regex( "\\s+" ))
                if (fields.size >= 2 && fields[0].compareTo("try") == 0) {
                    nonce = fields[1]
                }


                if ( fields[0] != "ok" ) {
                    // key = md5("nonce+pass+access+mid")
                    var key = ""
                    try {
                        val digester = MessageDigest.getInstance("MD5")
                        digester.update(nonce.toByteArray())
                        digester.update(params[1].toByteArray())
                        digester.update(params[2].toByteArray())
                        digester.update(mId!!.toByteArray())
                        val digest = digester.digest()
                        for (i in digest.indices) {
                            key += String.format("%02x", digest[i])
                        }
                    } catch (e: NoSuchAlgorithmException) {
                    }

                    sendLine(String.format("session %s %s %s %s\n", params[0], key, params[2], mId))
                    fields = recvLine().split(Regex( "\\s+" ))
                    if (fields.size < 2 || fields[0].compareTo("ok") != 0) {
                        res = false
                    }
                }

                if (res ) {
                    result.putString("sessionId", fields!![1])
                    sendLine(String.format("list %s *\n", fields[1]))
                    fields = recvLine().split(Regex("\\s+"))
                    if (fields.size > 0 && fields[0].compareTo("rlist") == 0) {
                        var i = 0
                        while (i < 1000) {
                            val lfields = recvLine().split(Regex("\\s+"))
                            if (lfields.size < 2) {
                                break
                            }
                            result.putString("id$i", lfields[0])
                            result.putString("name$i", lfields[1])
                            i++
                        }
                        result.putInt("numberOfDevices", i)
                    }
                }
            }

            close()
            result.putBoolean("Result", res)
            return result
        }
    }

    fun RemoteLogin(complete: ( Bundle ) -> Unit , user: String, pass: String, accesskey: String) {
        RemoteLoginTask(complete).execute(user, pass, accesskey)
    }

    // Get local devices list
    private inner class DeviceListTask(listener: ( Bundle ) -> Unit ) : PWTask<String>(listener) {

        override fun doInBackground(vararg params: String): Bundle {
            val result = Bundle()
            try {
                // data packet
                //  REQDVREX	0x7986a348
                //  DVRSVREX	0x95349b63

                val udpSocket = DatagramSocket()
                udpSocket.soTimeout = 1000

                //      int reqsize;
                val packet = ByteArray(4)
                val reqDvrEx = ByteBuffer.wrap(packet)
                reqDvrEx.order(ByteOrder.LITTLE_ENDIAN)
                reqDvrEx.putInt(0x7986a348)

                // enable Broadcast
                udpSocket.broadcast = true

                // generic broadcast
                udpSocket.send(
                    DatagramPacket(
                        packet,
                        0,
                        4,
                        InetSocketAddress("255.255.255.255", mPort)
                    )
                )

                // multicast for dvr device
                udpSocket.send(
                    DatagramPacket(
                        packet,
                        0,
                        4,
                        InetSocketAddress("228.229.230.231", mPort)
                    )
                )

                // send to unicast to preset server ( over internet, maybe )
                if (params.size > 0 && params[0] != null && params[0].length > 1) {
                    udpSocket.send(
                        DatagramPacket(
                            packet,
                            0,
                            4,
                            InetSocketAddress(params[0], mPort)
                        )
                    )
                }

                val deviceSet = HashSet<String>()
                // now wait for response
                var timeout = 10
                while (!isCancelled && mlistener != null && timeout > 0) {
                    val udpPacket = DatagramPacket(ByteArray(64), 64)
                    try {
                        udpSocket.receive(udpPacket)
                    } catch (e: IOException) {
                        // timeout catched
                        timeout--
                        continue
                    }

                    val len = udpPacket.length
                    if (len >= 4) {
                        //  DVRSVREX	0x95349b63
                        val dvrSvrEx =
                            ByteBuffer.wrap(udpPacket.data, udpPacket.offset, udpPacket.length)
                        dvrSvrEx.order(ByteOrder.LITTLE_ENDIAN)
                        if (dvrSvrEx.int == -0x6acb649d) {
                            // response from a PW device
                            val device = udpPacket.address.hostAddress
                            if (deviceSet.add(device)) {
                                if (connect(device, mPort)) {
                                    // get host name
                                    //  REQSERVERNAME, 10
                                    val ans = request(10)
                                    //  ANSSERVERNAME  12
                                    if (ans.code == 12 && ans.size > 0) {
                                        publishProgress(
                                            device,
                                            String(ans.dataBuffer.array()).trim())
                                    }
                                }
                                close()
                            }
                        }
                    }
                }
                deviceSet.clear()
                udpSocket.close()

            } catch (e: SocketException) {
                e.printStackTrace()
            } catch (e: IOException) {
                e.printStackTrace()
            }

            result.putBoolean("Complete", true)
            return result
        }

        override fun onProgressUpdate(vararg values: String?) {
            super.onProgressUpdate(*values)

            if (mlistener != null) {
                val progress = Bundle()
                progress.putBoolean("Complete", false)
                progress.putString("deviceIP", values[0])
                progress.putString("deviceName", values[1])
                mlistener(progress)
            }

        }
    }

    fun DeviceList(listener: ( Bundle ) -> Unit , host: String) {
        DeviceListTask(listener).execute(host)
    }

    // Get Web Setup URL
    private inner class WebUrlTask(listener: ( Bundle ) -> Unit ) : PWTask<String>(listener) {

        override fun doInBackground(vararg params: String): Bundle? {
            val result = Bundle()
            result.putString("URL", httpUrl)
            result.putBoolean("Complete", true)
            return result
        }

    }

    fun GetWebUrl(complete: ( Bundle ) -> Unit ) {
        WebUrlTask(complete).execute()
    }

    // Get Video Clip List by disknumber
    private inner class ClipListTask(listener: ( Bundle ) -> Unit ) : PWTask<Int>(listener) {
        override fun doInBackground(vararg params: Int?): Bundle? {

            val disk = params[0]!!
            var daylist = IntArray(0)

            // REQDAYLIST   (238)
            var ans: DvrClient.Ans = request(238)

            // ANSDAYLIST   (223)
            if (ans.code == 223 && ans.size > 0) {
                val daybuffer = ans.dataBuffer
                daybuffer.order(ByteOrder.LITTLE_ENDIAN)
                daylist = IntArray(ans.size / 4)
                for (i in daylist.indices) {
                    daylist[i] = daybuffer.getInt(i * 4)
                }
            }

            for (date in daylist) {

                val reqDate = ByteBuffer.allocate(4)
                reqDate.order(ByteOrder.LITTLE_ENDIAN)
                reqDate.putInt(0, date)

                // REQ:
                // REQDAYCLIPLIST   (237)
                ans = request(237, disk, reqDate)

                // ANS:
                // ANSDAYCLIPLIST   (222)
                if (ans.code == 222 && ans.size > 0) {
                    val clips = String(ans.dataBuffer.array(), 0, ans.size)
                    val cliplist =
                        clips.split(",")
                    publishProgress(*cliplist.toTypedArray())
                }

            }


            val result = Bundle()
            result.putBoolean("Complete", true)
            return result
        }

        override fun onProgressUpdate(vararg values: String?) {
            super.onProgressUpdate(*values)
            if (mlistener != null) {
                val progress = Bundle()
                progress.putBoolean("Complete", false)
                progress.putStringArray("clips", values)
                mlistener(progress)
            }
        }

    }

    fun getClipList(listener: ( Bundle ) -> Unit , disk: Int) {
        ClipListTask(listener).execute(disk)
    }


    // Get Video Clip List by disknumber
    private inner class DayListTask(listener: ( Bundle ) -> Unit ) : PWTask<Int>(listener) {

        override fun doInBackground(vararg params: Int?): Bundle? {
            val result = Bundle()
            var daylist = IntArray(0)

            // REQDAYLIST   (238)
            val ans = request(238)
            // ANSDAYLIST   (223)
            if (ans.code == 223 && ans.size > 0) {
                val daybuffer = ans.dataBuffer
                daybuffer.order(ByteOrder.LITTLE_ENDIAN)
                daylist = IntArray(ans.size / 4)
                for (i in daylist.indices) {
                    daylist[i] = daybuffer.getInt(i * 4)
                }
                result.putIntArray("daylist", daylist)
            }
            result.putBoolean("Complete", true)
            return result
        }

    }

    fun getDayList(listener: ( Bundle ) -> Unit ) {
        DayListTask(listener).execute()
    }

    companion object {

        // PW virtual key
        val PW_VK_TM_UP = 0xe0
        val PW_VK_TM_DOWN = 0x1e0
        val PW_VK_LP_UP = 0xe1
        val PW_VK_LP_DOWN = 0x1e1
        val PW_VK_C1_UP = 0xe6
        val PW_VK_C1_DOWN = 0x1e6
        val PW_VK_C2_UP = 0xe7
        val PW_VK_C2_DOWN = 0x1e7
    }

}
