/**
 * DvrClient
 * DVR protocol, communication with DVR
 * Created by dennis on 12/23/14.
 * 01/18/17, add retry on request()
 */

package com.tme_inc.pwv

import java.nio.ByteBuffer
import java.nio.ByteOrder


open class DvrClient : PwvSocket() {

    var host: String = "192.168.1.100"
    var port: Int = 15114

    // remote login connections
    var loginServer: String = "localhost"
    var loginPort: Int = 0
    var mId: String = "android"          // my device id

    private var loginSessionId: String = "0"
    private var loginTargetId: String = "0"

    // cmd: vserver <sessionid> <deviceid> <port>
    val httpUrl: String
        get() {
            var r = ""
            if (connectMode == CONN_DIRECT) {
                return "http://$host/"
            }
            else {
                with( PwvSocket() ) {
                    connect(loginServer, loginPort)
                    if (isConnected) {
                        sendLine("vserver $loginSessionId $loginTargetId 80\n")
                        val fields = recvLine().split(Regex("\\s+")).dropLastWhile { it.isBlank() }
                        if (fields.size >= 3 && fields[0] == "ok") {
                            r = if (fields[1] == "*")
                                "http://$loginServer:${fields[2]}/"
                            else
                                "http://${fields[1]}:${fields[2]}/"
                        }
                    }
                    close()
                }
            }
            return r
        }

    var connectMode: Int = 0
        set(mode) {
            field = mode

            val prefs = appCtx!!.getSharedPreferences("pwv", 0)

            host = prefs.getString("deviceIp", "192.168.1.100")
            port = prefs.getInt("dvrPort", 15114)

            loginServer = if (connectMode == CONN_USB) {
                "localhost"                                 // local service
            } else {      // default for direct connection (local lan)
                prefs.getString("loginServer", "pwrev.us.to")
            }

            loginPort = prefs.getInt("loginPort", 15600)
            loginSessionId = prefs.getString("loginSession", "0")
            loginTargetId = prefs.getString("loginTargetId", "0")
            mId = prefs.getString("aid", "android")          // my device id
        }

    init {
        // to call connectMode settor
        connectMode = appCtx!!.getSharedPreferences("pwv", 0).getInt("connMode", CONN_DIRECT)
    }

    open class Req (
        var code: Int ,
        var data: Int = 0,
        var dataBuffer: ByteBuffer = ByteBuffer.allocate(0)
    ) {
        val size
            get() = dataBuffer.remaining()
    }

    class Ans(
        code: Int = 0,
        data: Int = 0
    ) : Req( code, data )

    // connect to DVR
    fun connect(): Boolean {
        if (isConnected) {
            return true
        }

        if (connectMode == CONN_DIRECT) {
            return connect(host, port)
        } else {
            connect(loginServer, loginPort)
            if (isConnected) {
                // use login remote connection (internet)
                sendLine("remote $loginSessionId $loginTargetId $port\n" )
            } else {
                close()
            }
        }
        return isConnected
    }

    private fun sendReq(req: Req): Boolean {
        if (connect()) {
            val buffer = ByteBuffer.allocate(12)
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            buffer.putInt(req.code)
            buffer.putInt(req.data)
            buffer.putInt(req.size)
            buffer.flip()
            if (send(buffer) > 0) {
                if (req.size > 0) {
                    if (send(req.dataBuffer) > 0) {
                        return true
                    }
                } else {
                    return true
                }
            }
        }
        close()
        return false
    }

    fun recvAns(): Ans {
        val bb = ByteBuffer.allocate(12)
        if (recvBuf(bb) >= 12) {
            bb.order(ByteOrder.LITTLE_ENDIAN)
            val ans = Ans(bb.int, bb.int)
            val anssize = bb.int
            if (ans.code > 0 && anssize > 0 && anssize < 10000000) {
                //ans.databuf = recv(ans.size)
                ans.dataBuffer = ByteBuffer.allocate(anssize)
                if (recvBuf(ans.dataBuffer) >= anssize) {
                    return ans
                }
            }
            else {
                return ans
            }
        }
        return Ans()
    }

    fun request(
        reqcode: Int,
        reqdata: Int = 0,
        dataBuffer: ByteBuffer = ByteBuffer.allocate(0)
    ): Ans {
        val req = Req(reqcode, reqdata, dataBuffer)
        for (retry in 0..2) {
            if (sendReq(req)) {
                return recvAns()
            }
            close()
        }
        return Ans()      // empty ans (error)
    }

    companion object {
        // Connect mode constance
        const val CONN_DIRECT = 0
        const val CONN_REMOTE = 1
        const val CONN_USB = 2
    }
}
