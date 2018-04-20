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

    protected var _connectMode: Int = 0
    public var connectMode: Int
        get() = _connectMode
        set(mode: Int) { _setConnectMode(mode) }

    protected var mHost: String? = null
    protected var mPort: Int = 0

    // remote login connections
    protected var loginServer: String? = null
    protected var loginPort: Int = 0

    protected var loginSessionId: String? = null
    protected var loginTargetId: String? = null
    protected var mId: String? = null          // my device id

    // cmd: vserver <sessionid> <deviceid> <port>
    val httpUrl: String?
        get() {

            if (connectMode == CONN_DIRECT) {
                return "http://$mHost/"
            } else if (loginServer != null) {
                var r: String? = null
                connect(loginServer!!, loginPort)
                if (isConnected) {
                    sendLine(String.format("vserver %s %s %d\n", loginSessionId, loginTargetId, 80))
                    val fields = recvLine().split(Regex("\\s+"))
                    if (fields.size >= 3 && fields[0] == "ok") {
                        if (fields[1] == "*")
                            r = "http://" + loginServer + ":" + fields[2] + "/"
                        else
                            r = "http://" + fields[1] + ":" + fields[2] + "/"
                    }
                }
                close()
                return r
            }
            return null
        }

    open class Ans(
        var code: Int = 0,
        var data: Int = 0
    ) {
        val size
            get() = dataBuffer.remaining()
        var dataBuffer: ByteBuffer = ByteBuffer.allocate(0)
    }

    class Req (
        code: Int ,
        data: Int = 0,
        databuf: ByteBuffer? = null
    ) : Ans( code, data ) {
        init {
            if( databuf != null ) {
                dataBuffer = databuf
            }
        }
    }

    init {
        val prefs = appCtx!!.getSharedPreferences("pwv", 0)

        mHost = prefs.getString("deviceIp", "192.168.1.100")
        mPort = prefs.getInt("dvrPort", 15114)
        connectMode = prefs.getInt("connMode", CONN_DIRECT)

        if (connectMode == CONN_USB) {
            loginServer = "127.0.0.1"          // local service
        } else {      // default for direct connection (local lan)
            loginServer = prefs.getString("loginServer", "pwrev.us.to")
        }

        loginPort = prefs.getInt("loginPort", 15600)
        loginSessionId = prefs.getString("loginSession", "0")
        loginTargetId = prefs.getString("loginTargetId", "0")
        mId = prefs.getString("aid", "android")          // my device id
    }

    // connect to DVR
    fun connect(): Boolean {
        if (isConnected) {
            return true
        }

        close()

        if (connectMode == CONN_DIRECT) {
            return connect(mHost!!, mPort)
        } else {
            connect(loginServer!!, loginPort)
            if (isConnected) {
                // use login remote connection (internet)
                sendLine(String.format("remote %s %s %d\n", loginSessionId, loginTargetId, mPort))
            } else {
                close()
            }
        }
        return isConnected
    }

    fun _setConnectMode(mode: Int) {
        _connectMode = mode

        val prefs = appCtx!!.getSharedPreferences("pwv", 0)

        mHost = prefs.getString("deviceIp", "192.168.1.100")
        mPort = prefs.getInt("dvrPort", 15114)

        if (connectMode == CONN_USB) {
            loginServer = "127.0.0.1"          // local service
        } else {      // default for direct connection (local lan)
            loginServer = prefs.getString("loginServer", "pwrev.us.to")
        }

        loginPort = prefs.getInt("loginPort", 15600)
        loginSessionId = prefs.getString("loginSession", "0")
        loginTargetId = prefs.getString("loginTargetId", "0")
        mId = prefs.getString("aid", "android")          // my device id
    }

    protected fun sendReq(req: Req): Boolean {
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
        if (recv(bb) >= 12) {
            bb.order(ByteOrder.LITTLE_ENDIAN)
            val ans = Ans(bb.int, bb.int)
            val anssize = bb.int
            if (ans.code > 0 && anssize > 0 && anssize < 10000000) {
                //ans.databuf = recv(ans.size)
                ans.dataBuffer = ByteBuffer.allocate(anssize)
                if (recv(ans.dataBuffer) >= anssize) {
                    return ans
                }
            }
            else {
                return ans
            }
        }
        return Ans()
    }

    fun request(req: Req): Ans {
        for (retry in 0..2) {
            if (sendReq(req)) {
                return recvAns()
            }
            close()
        }
        return Ans()      // empty ans (error)
    }

    @JvmOverloads
    fun request(
        reqcode: Int,
        reqdata: Int = 0,
        dataBuffer: ByteBuffer? = null
    ): Ans {
        return request(Req(reqcode, reqdata, dataBuffer))
    }

    companion object {
        // Connect mode constance
        val CONN_DIRECT = 0
        val CONN_REMOTE = 1
        val CONN_USB = 2
    }
}
