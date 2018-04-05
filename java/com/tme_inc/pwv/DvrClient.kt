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
    protected var connectMode: Int
        get() = _connectMode
        set(mode: Int) = _setConnectMode(mode)
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
                    val fields = recvLine().split("\\s+".toRegex()).dropLastWhile { it.isEmpty() }
                        .toTypedArray()
                    if (fields.size >= 3 && fields[0] == "ok") {
                        if (fields[1] == "*") {
                            fields[1] = loginServer.toString()
                        }
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
        var data: Int = 0,
        var size: Int = 0,
        var databuf: ByteArray? = null
    )

    class Req (
        code: Int ,
        data: Int = 0,
        databuf: ByteArray? = null,
        var offset: Int = 0,
        size: Int = if (databuf == null) 0 else databuf.size - offset
    ) : Ans( code, data, size, databuf )

    init {
        val prefs = pwvApp.appCtx.getSharedPreferences("pwv", 0)

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

        val prefs = pwvApp.appCtx.getSharedPreferences("pwv", 0)

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
            val ba = ByteArray(12)
            val bb = ByteBuffer.wrap(ba)
            bb.order(ByteOrder.LITTLE_ENDIAN)
            bb.putInt(req.code)
            bb.putInt(req.data)
            bb.putInt(req.size)
            if (send(ba) > 0) {
                if (req.size > 0 && req.databuf != null) {
                    if (send(req.databuf!!, req.offset, req.size) > 0) {
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
        val r = recv(12)
        if (r != null) {
            val bb = ByteBuffer.wrap(r)
            bb.order(ByteOrder.LITTLE_ENDIAN)
            val ans = Ans(bb.int, bb.int, bb.int)
            if (ans.code > 0 && ans.size > 0 && ans.size < 10000000) {
                ans.databuf = recv(ans.size)
            }
            return ans;
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
        dataarray: ByteArray? = null,
        offset: Int = 0,
        reqsize: Int = 0
    ): Ans {
        return request(
            Req(
                reqcode,
                reqdata,
                dataarray,
                offset,
                if (reqsize == 0 && dataarray != null) dataarray.size - offset else reqsize
            )
        )
    }

    companion object {
        // Connect mode constance
        val CONN_DIRECT = 0
        val CONN_REMOTE = 1
        val CONN_USB = 2
    }
}
