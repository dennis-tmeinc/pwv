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

fun CString( buf: ByteArray, offset:Int = 0, length:Int = buf.size - offset  ) : String {
    val l = String( buf, offset, length ).split("\u0000")
    return if( l.isEmpty() )
        ""
    else
        l[0]
}

fun CString( buf: ByteBuffer, offset:Int = 0, length:Int = buf.remaining() - offset ) : String {
    val pos = buf.arrayOffset() + buf.position() + offset
    return CString( buf.array(), pos, length)
}

/**
 * Created by dennis on 1/13/15.
 * Use AsyncTask to complete DVR requests
 */
class PWProtocol {

    private var clientCache : DvrClient? = null
    private var currentTask: PWTask<*>? = null

    val isBusy: Boolean
        get() = (currentTask != null) && (currentTask!!.status == AsyncTask.Status.RUNNING)

    fun cancel() {
        if (isBusy) {
            currentTask!!.cancel(true)
        }
    }

    protected abstract inner class PWTask<T1>(protected val mlistener:  ( Bundle ) -> Unit = {} )
        : AsyncTask<T1, String, Bundle>() {

        protected val result = Bundle()
        protected var client : DvrClient = synchronized(this@PWProtocol) {
            if(clientCache!=null) {
                val c = clientCache!!
                clientCache = null
                c
            }
            else {
                DvrClient()
            }
        }

        override fun onPreExecute() {
            super.onPreExecute()

            if (currentTask == null) {
                currentTask = this
            }
        }

        override fun onPostExecute(result: Bundle?) {
            super.onPostExecute(result)

            synchronized(this@PWProtocol) {
                if(clientCache == null) {
                    clientCache = client
                }
                else {
                    client.close()
                }
            }

            if(currentTask == this )
                currentTask = null

            if( result != null && !result.isEmpty ) {
                mlistener(result)
            }
        }

        override fun onCancelled() {
            super.onCancelled()

            synchronized(this@PWProtocol) {
                if(clientCache == null) {
                    clientCache = client
                }
                else {
                    client.close()
                }
            }

            if(currentTask == this )
                currentTask = null
        }
    }

    private inner class GetVriTask(listener: ( Bundle ) -> Unit  ) : PWTask<Void>(listener) {
        override fun doInBackground(vararg params: Void?): Bundle {
            var vriItemSize = 468

            // REQ:
            // REQGETDATA   (302)
            // PROTOCOL_PW_GETVRILISTSIZE	(1004)
            var ans = client.request(302, 1004)
            // ANS:
            // ANSGETDATA   (302)
            if (ans.code == 302 && ans.size > 0) {
                val vrisize = CString(ans.dataBuffer)
                val vris =
                    vrisize.split(",")
                if (vris.isNotEmpty()) {
                    result.putInt("VriListSize", vris[0].toInt())
                }
                if (vris.size > 1) {
                    vriItemSize = vris[1].toInt()
                }
            }
            result.putInt("VriItemSize", vriItemSize)

            // REQ:
            // REQGETDATA   (302)
            // PROTOCOL_PW_GETVRILIST	(1005)
            ans = client.request(302, 1005)
            // ANS:
            // ANSGETDATA   (302)
            if (ans.code == 302 && ans.size >= vriItemSize) {
                result.putInt("VriListSize", ans.size / vriItemSize)
                result.putByteArray("VriList", ans.dataBuffer.array())
            }
            else {
                result.putInt("VriListSize", 0)
            }

            return result
        }
    }

    fun getVri(complete: ( Bundle ) -> Unit ) {
        GetVriTask(complete).execute()
    }

    private inner class SetVriTask : PWTask<ByteArray>() {

        override fun doInBackground(vararg vridata: ByteArray): Bundle? {
            if (vridata.isNotEmpty() && vridata[0].isNotEmpty()) {
                // REQ:
                // REQSENDDATA   (301)
                // PROTOCOL_PW_SETVRILIST		(1006)
                val ans = client.request(301, 1006, ByteBuffer.wrap(vridata[0]))

                // ANS:
                // ANSOK   (2)
                if (ans.code == 2) {
                    result.putInt("Result", 1)
                }

            }
            return result
        }
    }

    fun setVri(vridata: ByteArray) {
        SetVriTask().execute(vridata)
    }


    private inner class GetOfficerIDListTask(listener: ( Bundle ) -> Unit ) : PWTask<Void>(listener) {

        override fun doInBackground(vararg aVoid: Void): Bundle {
            // REQ:
            // REQGETDATA   (302)
            // PROTOCOL_PW_GETPOLICEIDLIST	(1002)
            val ans = client.request(302, 1002)

            // ANS:
            // ANSGETDATA   (302)
            if (ans.code == 302 && ans.size > 0) {
                result.putInt("policeId_number", ans.data)
                result.putInt("policeId_size", ans.size)
                result.putByteArray("policeId_list", ans.dataBuffer.array())
            }

            return result
        }

    }

    fun getOfficerIDList(complete: ( Bundle ) -> Unit ) {
        GetOfficerIDListTask(complete).execute()
    }


    private inner class SetOfficerIdTask : PWTask<String>() {

        override fun doInBackground(vararg oids: String): Bundle? {
            if (oids.isNotEmpty()) {
                val officerId = oids[0] + "\u0000"   // append null terminate
                val oidarray = officerId.toByteArray()

                // REQ:
                // REQSENDDATA   (301)
                // PROTOCOL_PW_SETPOLICEID		(1003)
                val ans = client.request(301, 1003, ByteBuffer.wrap(oidarray))

                // ANS:
                // ANSOK   (2)
                if (ans.code == 2) {
                    result.putInt("Result", 1)
                }
            }
            return result
        }
    }

    fun setOfficerId(officerId: String) {
        SetOfficerIdTask().execute(officerId)
    }

    //
    private inner class SendPWKeyTask : PWTask<Int>() {

        override fun doInBackground(vararg keys: Int?): Bundle? {
            if (keys.isNotEmpty()) {
                // REQ:
                // REQ2KEYPAD   (230)
                val ans = client.request(230, keys[0]!!)

                // ANS:
                // ANSOK   (2)
                if (ans.code == 2) {
                    result.putInt("Result", 1)
                }
            }
            return result
        }
    }

    fun sendPWKey(keycode: Int) {
        SendPWKeyTask().execute(keycode)
    }

    //
    private inner class SetCovertTask : PWTask<Boolean>({}) {

        override fun doInBackground(vararg covs: Boolean?): Bundle {
            if (covs.isNotEmpty()) {
                val cov = ByteArray(4)
                if (covs[0]!!) {
                    cov[0] = 1
                } else {
                    cov[0] = 0
                }

                // REQ:
                // REQSENDDATA   (301)
                // PROTOCOL_PW_SETCOVERTMODE		(1009)
                client.request(301, 1009, ByteBuffer.wrap(cov))

                // ANS: (ignored)
                // ANSOK   (2)\
            }
            return result
        }
    }

    fun setCovertMode(covert: Boolean) {
        SetCovertTask().execute(covert)
    }

    //
    private inner class GetPWStatusTask(listener: ( Bundle ) -> Unit ) : PWTask<Void>(listener) {

        override fun doInBackground(vararg avoid: Void): Bundle? {
            // REQ:
            // REQGETDATA   (302)
            // PROTOCOL_PW_GETSTATUS   	(1001)
            var ans = client.request(302, 1001)

            // ANS:
            // ANSGETDATA   (302)
            if (ans.code == 302 && ans.size > 0) {
                result.putByteArray("PWStatus", ans.dataBuffer.array())
            }

            // REQ:
            // REQGETDATA   (302)
            // PROTOCOL_PW_GETDISKINFO   	(1010)
            ans = client.request(302, 1010)

            // ANS:
            // ANSGETDATA   (302)
            if (ans.code == 302 && ans.size > 0) {
                result.putString("DiskInfo", String(ans.dataBuffer.array()).trim { it <= ' ' })
            }

            /*
            // REQ:
            // REQGETDATA   (302)
            // PROTOCOL_PW_GETWARNINGMSG   	(1008)

            ans = client.request( 302, 1008 ) ;

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

    fun getPWStatus(complete: ( Bundle ) -> Unit ) {
        GetPWStatusTask(complete).execute()
    }

    // Remote Login
    private inner class RemoteLoginTask(listener: ( Bundle ) -> Unit ) : PWTask<String>(listener) {

        override fun doInBackground(vararg params: String): Bundle? {
            var res = true

            // connect to remote server directly
            with( client ) {
                close()
                if( params[3].isNotBlank() )
                    connectMode = params[3].toInt()
                if (connect(loginServer, loginPort)) {
                    var nonce = "nonce"

                    sendLine("session\n")
                    var fields = recvLine().split(Regex( "\\s+" )).dropLastWhile { it.isBlank() }
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
                            digester.update(mId.toByteArray())
                            val digest = digester.digest()
                            for (i in digest.indices) {
                                key += String.format("%02x", digest[i])
                            }
                        } catch (e: NoSuchAlgorithmException) {
                        }

                        sendLine("session ${params[0]} $key ${params[2]} $mId\n")
                        fields = recvLine().split(Regex( "\\s+" )).dropLastWhile { it.isBlank() }
                        if (fields.size < 2 || fields[0].compareTo("ok") != 0) {
                            res = false
                        }
                    }

                    if (res ) {
                        result.putString("sessionId", fields[1])
                        sendLine( "list ${fields[1]} *\n")
                        fields = recvLine().split(Regex( "\\s+" )).dropLastWhile { it.isBlank() }
                        if (fields.isNotEmpty() && fields[0] == "rlist") {
                            var i = 0
                            while (i < 1000) {
                                val lfields = recvLine().split(Regex( "\\s+" )).dropLastWhile { it.isBlank() }
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
            }

            result.putBoolean("Result", res)
            return result
        }
    }

    fun remoteLogin(complete: ( Bundle ) -> Unit , user: String, pass: String, accesskey: String, connMode: String = "" ) {
        RemoteLoginTask(complete).execute(user, pass, accesskey, connMode)
    }

    // Get local devices list
    private inner class DeviceListTask(listener: ( Bundle ) -> Unit ) : PWTask<String>(listener) {

        override fun doInBackground(vararg params: String): Bundle {
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
                        InetSocketAddress("255.255.255.255", client.port)
                    )
                )

                // multicast for dvr device
                udpSocket.send(
                    DatagramPacket(
                        packet,
                        0,
                        4,
                        InetSocketAddress("228.229.230.231", client.port)
                    )
                )

                // send to unicast to preset server ( over internet, maybe )
                if (params.isNotEmpty() && params[0].isNotBlank()) {
                    udpSocket.send(
                        DatagramPacket(
                            packet,
                            0,
                            4,
                            InetSocketAddress(params[0], client.port)
                        )
                    )
                }

                val deviceSet = HashSet<String>()
                // now wait for response
                var timeout = 10
                while (!isCancelled && timeout > 0) {
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
                                with( DvrClient() ) {
                                    if (connect(device, port)) {
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

            val progress = Bundle()
            progress.putBoolean("Complete", false)
            progress.putString("deviceIP", values[0])
            progress.putString("deviceName", values[1])
            mlistener(progress)

        }
    }

    fun getDeviceList(listener: ( Bundle ) -> Unit , host: String) {
        DeviceListTask(listener).execute(host)
    }

    // Get Web Setup URL
    private inner class WebUrlTask(listener: ( Bundle ) -> Unit ) : PWTask<String>(listener) {

        override fun doInBackground(vararg params: String): Bundle? {
            result.putString("URL", client.httpUrl)
            result.putBoolean("Complete", true)
            return result
        }

    }

    fun getWebUrl(complete: ( Bundle ) -> Unit ) {
        WebUrlTask(complete).execute()
    }

    // Get Video Clip List by disknumber
    private inner class ClipListTask(listener: ( Bundle ) -> Unit ) : PWTask<Int>(listener) {
        override fun doInBackground(vararg params: Int?): Bundle? {

            val disk = params[0]!!
            var daylist = IntArray(0)

            // REQDAYLIST   (238)
            var ans = client.request(238)

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
                ans = client.request(237, disk, reqDate)

                // ANS:
                // ANSDAYCLIPLIST   (222)
                if (ans.code == 222 && ans.size > 0) {
                    val clips = String(ans.dataBuffer.array(), 0, ans.size)
                    val cliplist =
                        clips.split(",")
                    publishProgress(*cliplist.toTypedArray())
                }

            }


            result.putBoolean("Complete", true)
            return result
        }

        override fun onProgressUpdate(vararg values: String?) {
            super.onProgressUpdate(*values)
            val progress = Bundle()
            progress.putBoolean("Complete", false)
            progress.putStringArray("clips", values)
            mlistener(progress)
        }

    }

    fun getClipList(listener: ( Bundle ) -> Unit , disk: Int) {
        ClipListTask(listener).execute(disk)
    }


    // Get Video Clip List by disknumber
    private inner class DayListTask(listener: ( Bundle ) -> Unit ) : PWTask<Int>(listener) {

        override fun doInBackground(vararg params: Int?): Bundle? {

            // REQDAYLIST   (238)
            val ans = client.request(238)
            // ANSDAYLIST   (223)
            if (ans.code == 223 && ans.size > 0) {
                val daybuffer = ans.dataBuffer
                daybuffer.order(ByteOrder.LITTLE_ENDIAN)
                var daylist = IntArray(ans.size / 4)
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
        const val PW_VK_TM_UP = 0xe0
        const val PW_VK_TM_DOWN = 0x1e0
        const val PW_VK_LP_UP = 0xe1
        const val PW_VK_LP_DOWN = 0x1e1
        const val PW_VK_C1_UP = 0xe6
        const val PW_VK_C1_DOWN = 0x1e6
        const val PW_VK_C2_UP = 0xe7
        const val PW_VK_C2_DOWN = 0x1e7
    }

}
