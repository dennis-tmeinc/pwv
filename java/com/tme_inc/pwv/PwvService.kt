package com.tme_inc.pwv

import android.app.Service
import android.content.Intent
import android.os.IBinder
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.*

/**
 * Created by dennis on 09/06/15.
 */
class PwvService : Service() {

    private val vServerSet = HashSet<VirtualServer>()
    private val connSet = HashSet<AdbConn>()

    // virtual server support
    private inner class VirtualServer(val targetDid: String, val targetPort: String) {

        var running: Boolean = false
        private var server: ServerSocket? = null

        private var mThread = Thread({
            while (running && server != null && !server!!.isClosed) {
                try {
                    val s = server!!.accept()
                    val nconn = AdbConn(s)
                    synchronized(connSet) {
                        for (conn in connSet) {
                            if (conn.phase == 2 && conn.did == targetDid) {
                                // request reversed connection
                                nconn.phase = 3
                                connSet.add(nconn)
                                conn.sendLine("connect ${nconn.did} * $targetPort * 0\n")
                                break
                            }
                        }
                    }
                    if (nconn.phase == 3) {
                        nconn.start()
                    } else {
                        nconn.close()
                    }
                } catch (e: IOException) {
                    running = false
                }
            }
            running = false
            try {
                if (server != null)
                    server!!.close()
            } catch (e: IOException) {
            }
        })

        init {
            try {
                server = ServerSocket(0)
                server!!.soTimeout = 1800000           // time out in half an hour
                running = true
            } catch (e: IOException) {
                server = null
                running = false
            }
            mThread.start()
        }

        val port: Int
            get() =
                if (running && server != null && !server!!.isClosed)
                    server!!.localPort
                else
                    0

        fun close() {
            running = false
            if (server != null) {
                try {
                    server!!.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }

                server = null
            }
            mThread.interrupt()
        }

    }

    // adb reverse connection
    private inner class AdbConn(s: Socket) {

        var phase = 1      // 0: close, 1: init cmd, 2: unit wait, 3: connecting, 4: connected
        var did = s.remoteSocketAddress.toString()
        var unitname: String = ""

        private var target: AdbConn? = null         // target connection
        private var xoffflag = false
        private var sock = PwvSocket(s)

        private val mThread = Thread({
            while (phase > 0 && sock.isConnected) {
                if (phase <= 2) {
                    do_cmd()
                } else if (phase == 3) {        // connecting, wait to be connected or closed
                    try {
                        Thread.sleep(10000)     // 10 seconds wait to be connected
                    } catch (e: InterruptedException) {
                    }

                    if (phase == 3) {          // not connected in 10s
                        break
                    }
                } else if (phase == 4) {        // connected
                    if (xoffflag) {
                        try {
                            Thread.sleep(100)
                        } catch (e: InterruptedException) {
                        }

                    } else {
                        do_tunneling()
                    }
                } else {
                    break
                }
            }
            close()
            phase = 0
        })

        fun start() {
            mThread.start()
        }

        fun close() {
            // clean up peers
            if (phase == 2) {
                synchronized(connSet) {
                    val it = connSet.iterator()
                    while (it.hasNext()) {
                        val conn = it.next()
                        if (conn.phase != 2) {
                            conn.closepeer(this)
                        }
                    }
                }
            } else if (target != null) {
                target!!.closepeer(this)
                target = null
            }

            if (sock.isConnected) {
                sock.close()
            }

            phase = 0
            // interrupt the thread if possible
            mThread.interrupt()
        }

        fun closepeer(peer: AdbConn) {
            if (phase == 4 && target === peer) {
                target = null
                close()
            } else if (phase == 2) {
                sendLine("close ${peer.did}\n")
            }
        }

        @Synchronized
        fun sendfrom(data: ByteArray, dsize: Int, from: AdbConn): Int {
            if (phase == 4 && target === from)
                return sock.send(data, 0, dsize)
            else if (phase == 2) {
                sock.sendLine("mdata ${from.did} $dsize\n")
                return sock.send(data, 0, dsize)
            }
            return 0
        }

        @Synchronized
        fun sendLine(line: String): Int {
            return sock.sendLine(line)
        }

        fun xoff() {
            xoffflag = true
        }

        fun xon() {
            if (xoffflag) {
                xoffflag = false
                mThread.interrupt()
            }
        }

        protected fun do_cmd() {
            val fields = sock.recvLine().split("\\s+".toRegex()).dropLastWhile { it.isBlank() }
            if (fields.size > 0) {

                // support command list:
                //    list:        list	sessionid	*|hostname  ( request a list of PW unit )
                //    remote:      remote sessionid idx port  ( request remote connection )
                //    unit:        unit name uuid   		( PW unit register )
                //    vserver:     vserver idx port         ( request virtual server to remote **** to be support in future )
                //    connected:   connected id             ( reverse connection from PW )
                //    close:       close id					( reverse connection failure )
                //    p:           ping                     ( null ping, ignor it)

                when (fields[0]) {
                    "list" -> {
                        sendLine("rlist\n")
                        synchronized(connSet) {
                            val it = connSet.iterator()
                            while (it.hasNext()) {
                                val conn = it.next()
                                if (conn.phase == 2) {
                                    sendLine("${conn.did} ${conn.unitname}\n")
                                }
                            }
                        }
                        sendLine("\n")
                    }

                    "session" -> {
                        // cmd: session user key accesskey
                        if (fields.size > 1 && fields[1] != "usb") {
                            sendLine("Error\n")
                        } else {
                            sendLine("ok usb\n")     // session is not used on usb connection
                        }
                    }

                    "validate" -> {
                        // cmd: session user key accesskey
                        sendLine("ok usb\n")     // session is not used on usb connection
                    }

                    "remote" -> {
                        // cmd:  remote sessionid did port
                        if (fields.size >= 4) {
                            synchronized(connSet) {
                                val it = connSet.iterator()
                                while (it.hasNext()) {
                                    val conn = it.next()
                                    if (conn.phase == 2 && conn.did == fields[2]) {
                                        // request reversed connection
                                        conn.sendLine("connect $did * ${fields[3]} * 0\n")
                                        phase = 3     // connecting
                                        return
                                    }
                                }
                            }
                        }
                        sendLine("Error\n")

                    }

                    "mremote" -> {
                        // cmd:  remote sessionid did port
                        if (fields.size >= 4) {
                            synchronized(connSet) {
                                val it = connSet.iterator()
                                while (it.hasNext()) {
                                    val conn = it.next()
                                    if (conn.phase == 2 && fields[2] == conn.did) {
                                        // request reversed connection
                                        conn.sendLine("mconn $did * ${fields[3]} * 0\n")
                                        phase = 3     // connecting
                                        return
                                    }
                                }
                            }
                        }
                        sendLine("Error\n")

                    }

                    "unit" -> {
                        if (fields.size >= 3) {
                            synchronized(connSet) {
                                val it = connSet.iterator()
                                while (it.hasNext()) {
                                    val conn = it.next()
                                    if (conn === this)
                                        continue
                                    if (conn.phase == 2) {
                                        conn.close()
                                        it.remove()
                                    }
                                }
                            }
                            phase = 2
                            unitname = fields[1]
                            did = fields[2]
                        }
                    }

                    "vserver" -> {
                        // cmd: vserver <sessionid> <deviceid> <port>
                        if (fields.size >= 4) {
                            var line: String = ""
                            synchronized(vServerSet) {
                                var vs: VirtualServer? = null
                                // look for existing vserver
                                for (v in vServerSet) {
                                    if (fields[2] == v.targetDid && fields[3] == v.targetPort) {
                                        vs = v
                                        break
                                    }
                                }
                                if (vs == null) {
                                    vs = VirtualServer(fields[2], fields[3])
                                    vServerSet.add(vs)
                                }
                                line = String.format("ok * %d\n", vs.port)
                            }
                            if (!line.isEmpty())
                                sendLine(line)
                        } else {
                            sendLine("Error\n")
                        }

                    }

                    "connected" -> {

                        if (fields.size > 1) {
                            // cmd: connected id
                            // find target
                            synchronized(connSet) {
                                val it = connSet.iterator()
                                while (it.hasNext()) {
                                    val conn = it.next()
                                    if (conn.phase == 3 && fields[1] == conn.did) {  // connecting
                                        if (phase != 2) {
                                            target = conn
                                            phase = 4             // connected
                                        }
                                        conn.target = this
                                        conn.phase = 4                 // connected
                                        conn.mThread.interrupt()       // to wait up target thread
                                        return
                                    }
                                }
                            }
                            // id not found!
                            if (phase == 2) {
                                // response with closed connect
                                sendLine("close ${fields[1]}\n")
                            }
                        }
                    }

                    "close" -> {
                        if (fields.size > 1) {
                            synchronized(connSet) {
                                val it = connSet.iterator()
                                while (it.hasNext()) {
                                    val conn = it.next()
                                    if (fields[1] == conn.did) {  // connecting
                                        conn.target = null
                                        if (conn.phase == 3) {
                                            conn.phase = 0
                                            conn.mThread.interrupt()          // wakeup target conn
                                        }
                                        conn.phase = 0
                                        break
                                    }
                                }
                            }
                        }
                        if (phase != 2) {
                            phase = 0
                        }
                    }

                    "mdata" -> {
                        // multi use connection data
                        if (phase == 2 && fields.size > 2) {
                            val si = Integer.parseInt(fields[2])
                            if (si > 0 && si < 65536) {
                                val buffer = ByteArray(si)
                                val r = sock.recv(buffer, 0, si)
                                if (r > 0) {
                                    var f: AdbConn? = null
                                    synchronized(connSet) {
                                        val it = connSet.iterator()
                                        while (it.hasNext()) {
                                            val conn = it.next()
                                            if (conn.did == fields[1] && conn.target === this) {
                                                f = conn
                                                break
                                            }
                                        }
                                    }
                                    if (f != null) {
                                        f!!.sendfrom(buffer, r, this)
                                    }
                                }
                            }
                        }
                    }

                    "xon" -> {
                        if (fields.size > 1) {
                            synchronized(connSet) {
                                val it = connSet.iterator()
                                while (it.hasNext()) {
                                    val conn = it.next()
                                    if (fields[1] == conn.did && conn.target === this) {
                                        conn.xon()
                                        break
                                    }
                                }
                            }
                        }
                    }

                    "xoff" -> {
                        // multi use connection xoff
                        if (fields.size > 1) {
                            synchronized(connSet) {
                                for (conn in connSet) {
                                    if (conn.did == fields[1] && conn.target === this) {
                                        conn.xoff()
                                    }
                                }
                            }
                        }
                    }

                    "p" -> {
                        sendLine("e\n")
                    }

                    "hole" -> {
                        if (fields.size > 2) {
                            try {
                                var s = Socket()
                                s.reuseAddress = true
                                s.bind(InetSocketAddress(15601))
                                try {
                                    s.connect(
                                        InetSocketAddress(
                                            fields[1],
                                            Integer.valueOf(fields[2])
                                        ), 50
                                    )
                                } catch (e: IOException) {
                                    e.printStackTrace()
                                }

                                s.close()

                                val ss = ServerSocket(15601)
                                ss.soTimeout = 600
                                try {
                                    s = ss.accept()
                                    s.close()
                                } catch (e: IOException) {
                                }

                                ss.close()

                            } catch (e: SocketException) {
                                e.printStackTrace()
                            } catch (e: IOException) {
                                e.printStackTrace()
                            }
                        }
                        Unit
                    }

                    "kill-service" -> {      // debugging
                        stopSelf()
                    }

                    else -> {
                        sendLine("Unknown cmd\n")
                    }

                }
            }
        }

        // tunnelling data
        private fun do_tunneling() {
            if (sock.isConnected && target != null) {
                val buffer = ByteArray(8192)
                val r = sock.recv1(buffer, 0, buffer.size)
                if (r > 0) {
                    if (target != null) {
                        if (target!!.sendfrom(buffer, r, this) > 0)
                            return         // success
                    }
                }
            }
            // tunnel closed or target closed
            close()
        }
    }

    // clean up closed sockets
    protected fun cleanupSockets() {

        synchronized(connSet) {
            val it = connSet.iterator()
            while (it.hasNext()) {
                val conn = it.next()
                if (conn.phase <= 0) {       // clean finished connection
                    conn.close()
                    it.remove()
                }
            }
        }

        synchronized(vServerSet) {
            val it = vServerSet.iterator()
            while (it.hasNext()) {
                val vs = it.next()
                if (!vs.running) {
                    vs.close()
                    it.remove()
                }
            }
        }
    }

    // main server for PwvService
    private inner class PwvServer {
        private val server: ServerSocket?

        init {
            val sPort = getSharedPreferences("pwv", 0).getInt("loginPort", 15600)
            server =
                    try {
                        ServerSocket(sPort)
                    } catch (e: IOException) {
                        null
                    }
            server?.soTimeout = 1800000
        }

        protected var mThread = Thread({
            while (server != null && !server.isClosed) {
                try {
                    val s = server.accept()
                    if (s != null) {
                        val conn = AdbConn(s)
                        synchronized(connSet) {
                            connSet.add(conn)
                        }
                        conn.start()
                    }
                    cleanupSockets()
                } catch (e: IOException) {
                    cleanupSockets()
                    if (connSet.isEmpty()) {
                        break
                    }
                }

            }
            try {
                if (server != null && !server.isClosed)
                    server.close()
            } catch (e: IOException) {
            }

            stopSelf()
        })

        fun start() {
            if (!mThread.isAlive)
                mThread.start()
        }

        fun close() {
            try {
                server?.close()
            } catch (e: IOException) {
            }

            if (mThread.isAlive) {
                mThread.interrupt()
            }

            synchronized(connSet) {
                for (vs in connSet) {
                    vs.close()
                }
                connSet.clear()
            }

            synchronized(vServerSet) {
                for (vs in vServerSet) {
                    vs.close()
                }
                vServerSet.clear()
            }

        }
    }

    private var mServer: PwvServer? = null

    override fun onCreate() {
        super.onCreate()
        mServer = PwvServer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        mServer?.start()
        return Service.START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        // We don't provide binding, so return null
        return null
    }

    override fun onDestroy() {
        mServer?.close()
    }
}