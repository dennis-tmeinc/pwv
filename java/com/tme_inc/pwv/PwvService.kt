package com.tme_inc.pwv

import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.net.LocalServerSocket
import android.net.LocalSocket
import android.os.BatteryManager
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.widget.Toast

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketAddress
import java.net.SocketException
import java.util.HashSet

/**
 * Created by dennis on 09/06/15.
 */
class PwvService : Service() {

    protected var vServerSet = HashSet<vServer>()

    protected var connSet = HashSet<adConn>()

    protected var mServer: PwvServer? = null

    // virtual server support
    protected inner class vServer(var targetDid: String, var targetPort: String) {

        var running: Boolean = false

        protected var mThread: Thread
        protected var server: ServerSocket? = null

        init {
            try {
                server = ServerSocket(0)
                server!!.soTimeout = 1800000           // time out in half an hour
                running = true
            } catch (e: IOException) {
                server = null
                running = false
            }

            mThread = Thread({
                while (running && server != null && !server!!.isClosed) {
                    try {
                        val s = server!!.accept()
                        val nconn = adConn(s)
                        synchronized(connSet) {
                            val it = connSet.iterator()
                            while (it.hasNext()) {
                                val conn = it.next()
                                if (conn.phase == 2 && conn.did.compareTo(targetDid) == 0) {
                                    // request reversed connection
                                    nconn.phase = 3
                                    connSet.add(nconn)
                                    conn.sendLine(
                                        String.format(
                                            "connect %s * %s * 0\n",
                                            nconn.did,
                                            targetPort
                                        )
                                    )
                                    break
                                }
                            }
                        }
                        if (nconn.phase == 3) {
                            nconn.start()
                        } else {
                            nconn.close()
                            break
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
            mThread.start()
        }

        internal fun close() {
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

        internal fun port(): Int {
            return if (server != null && !server!!.isClosed) {
                server!!.localPort
            } else 0
        }

    }

    // adb reverse connection
    protected inner class adConn internal constructor(s: Socket) {
        var phase: Int = 0      // 0: close, 1: init cmd, 2: unit wait, 3: connecting, 4: connected
        var mThread: Thread? = null

        internal var target: adConn? = null         // target connection
        internal var did: String = ""
        internal var unitname: String = ""

        protected var sock: PwvSocket
        internal var xoffflag: Boolean = false

        init {
            sock = PwvSocket(s)
            phase = 1     // init
            xoffflag = false
            //did = Integer.toString(s.getPort());
            did = s.remoteSocketAddress.toString()
            mThread = null
        }

        fun start() {
            mThread = Thread(Runnable {
                while (phase > 0 && sock.isConnected) {
                    if (phase <= 2) {
                        do_cmd()
                    } else if (phase == 3) {        // connecting, wait to be connected or closed
                        try {
                            Thread.sleep(10000)
                        } catch (e: InterruptedException) {
                        }

                        if (phase == 3) {          // not been connected in 10s
                            break
                        }
                    } else if (phase == 4) {        // connected
                        if (xoffflag) {
                            try {
                                Thread.sleep(10000)
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
            mThread!!.start()
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
            if (mThread != null && mThread!!.isAlive) {
                mThread!!.interrupt()
            }

        }

        fun closepeer(peer: adConn) {
            if (phase == 4 && target === peer) {
                target = null
                close()
            } else if (phase == 2) {
                sendLine(String.format("close %s\n", peer.did))
            }
        }

        fun sendfrom(data: ByteArray, dsize: Int, from: adConn): Int {
            synchronized(this) {
                if (phase == 4 && target === from)
                    return sock.send(data, 0, dsize)
                else if (phase == 2) {
                    sendLine(String.format("mdata %s %d\n", from.did, dsize))
                    return sock.send(data, 0, dsize)
                }
            }
            return 0
        }

        fun sendLine(line: String): Int {
            synchronized(this) {
                return sock.sendLine(line)
            }
        }

        fun xoff() {
            xoffflag = true
        }

        fun xon() {
            if (xoffflag) {
                xoffflag = false
                mThread!!.interrupt()
            }
        }

        protected fun do_cmd() {
            val fields = sock.recvLine().split("\\s+".toRegex())
            if (fields.size > 0) {

                // support command list:
                //    list:        list	sessionid	*|hostname  ( request a list of PW unit )
                //    remote:      remote sessionid idx port  ( request remote connection )
                //    unit:        unit name uuid   		( PW unit register )
                //    vserver:     vserver idx port         ( request virtual server to remote **** to be support in future )
                //    connected:   connected id             ( reverse connection from PW )
                //    close:       close id					( reverse connection failure )
                //    p:           ping                     ( null ping, ignor it)

                if (fields[0].compareTo("list") == 0) {
                    sendLine("rlist\n")
                    synchronized(connSet) {
                        val it = connSet.iterator()
                        while (it.hasNext()) {
                            val conn = it.next()
                            if (conn.phase == 2) {
                                sendLine(String.format("%s %s\n", conn.did, conn.unitname))
                            }
                        }
                    }
                    sendLine("\n")
                } else if (fields[0].compareTo("session") == 0) {
                    // cmd: session user key accesskey
                    if (fields.size > 1 && fields[1].compareTo("usb") != 0) {
                        sendLine("Error\n")
                    } else {
                        sendLine("ok usb\n")     // session is not used on usb connection
                    }
                } else if (fields[0].compareTo("validate") == 0) {
                    // cmd: session user key accesskey
                    sendLine("ok usb\n")     // session is not used on usb connection
                } else if (fields[0].compareTo("remote") == 0) {
                    // cmd:  remote sessionid did port
                    if (fields.size >= 4) {
                        synchronized(connSet) {
                            val it = connSet.iterator()
                            while (it.hasNext()) {
                                val conn = it.next()
                                if (conn.phase == 2 && conn.did.compareTo(fields[2]) == 0) {
                                    // request reversed connection
                                    conn.sendLine(
                                        String.format(
                                            "connect %s * %s * 0\n",
                                            did,
                                            fields[3]
                                        )
                                    )
                                    phase = 3     // connecting
                                    return
                                }
                            }
                        }
                    }
                    sendLine("Error\n")

                } else if (fields[0].compareTo("mremote") == 0) {
                    // cmd:  remote sessionid did port
                    if (fields.size >= 4) {
                        synchronized(connSet) {
                            val it = connSet.iterator()
                            while (it.hasNext()) {
                                val conn = it.next()
                                if (conn.phase == 2 && conn.did.compareTo(fields[2]) == 0) {
                                    // request reversed connection
                                    conn.sendLine(
                                        String.format(
                                            "mconn %s * %s * 0\n",
                                            did,
                                            fields[3]
                                        )
                                    )
                                    phase = 3     // connecting
                                    return
                                }
                            }
                        }
                    }
                    sendLine("Error\n")

                } else if (fields[0].compareTo("unit") == 0) {
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
                } else if (fields[0].compareTo("vserver") == 0) {
                    // cmd: vserver <sessionid> <deviceid> <port>
                    if (fields.size >= 4) {
                        var line : String = ""
                        synchronized(vServerSet) {
                            var vs: vServer? = null
                            // look for existing vserver
                            for( v in vServerSet ) {
                                if (v.targetDid.compareTo(fields[2]) == 0 && v.targetPort.compareTo(fields[3]) == 0 ) {
                                    vs = v
                                    break
                                }
                            }
                            if (vs == null) {
                                vs = vServer(fields[2], fields[3])
                                vServerSet.add(vs)
                            }
                            if( vs != null )
                                line = String.format("ok * %d\n", vs!!.port())
                        }
                        if( !line.isEmpty())
                            sendLine(line)
                    } else {
                        sendLine("Error\n")
                    }

                } else if (fields[0].compareTo("connected") == 0 && fields.size > 1) {
                    // cmd: connected id
                    // find target
                    synchronized(connSet) {
                        val it = connSet.iterator()
                        while (it.hasNext()) {
                            val conn = it.next()
                            if (conn.phase == 3 && conn.did.compareTo(fields[1]) == 0) {  // connecting
                                if (phase != 2) {
                                    target = conn
                                    phase = 4             // connected
                                }
                                conn.target = this
                                conn.phase = 4                 // connected
                                conn.mThread!!.interrupt()       // to wait up target thread
                                return
                            }
                        }
                    }
                    // id not found!
                    if (phase == 2) {
                        // response with closed connect
                        sendLine(String.format("close %s\n", fields[1]))
                    }
                } else if (fields[0].compareTo("close") == 0) {
                    if (fields.size > 1) {
                        synchronized(connSet) {
                            val it = connSet.iterator()
                            while (it.hasNext()) {
                                val conn = it.next()
                                if (conn.did.compareTo(fields[1]) == 0) {  // connecting
                                    conn.target = null
                                    if (conn.phase == 3) {
                                        conn.phase = 0
                                        conn.mThread!!.interrupt()          // wakeup target conn
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
                } else if (fields[0].compareTo("mdata") == 0) {
                    // multi use connection data
                    if (phase == 2 && fields.size > 2) {
                        val si = Integer.parseInt(fields[2])
                        if (si > 0 && si < 65536) {
                            val buffer = ByteArray(si)
                            val r = sock.recv(buffer, 0, si)
                            if (r > 0) {
                                var f: adConn? = null
                                synchronized(connSet) {
                                    val it = connSet.iterator()
                                    while (it.hasNext()) {
                                        val conn = it.next()
                                        if (conn.did.compareTo(fields[1]) == 0 && conn.target === this) {
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
                } else if (fields[0].compareTo("xon") == 0) {
                    if (fields.size > 1) {
                        synchronized(connSet) {
                            val it = connSet.iterator()
                            while (it.hasNext()) {
                                val conn = it.next()
                                if (conn.did.compareTo(fields[1]) == 0 && conn.target === this) {
                                    conn.xon()
                                    break
                                }
                            }
                        }
                    }
                } else if (fields[0].compareTo("xoff") == 0 && fields.size > 1) {
                    // multi use connection xoff
                    if (fields.size > 1) {
                        synchronized(connSet) {
                            val it = connSet.iterator()
                            while (it.hasNext()) {
                                val conn = it.next()
                                if (conn.did.compareTo(fields[1]) == 0 && conn.target === this) {
                                    conn.xoff()
                                    break
                                }
                            }
                        }
                    }
                } else if (fields[0].compareTo("p") == 0) {
                    sendLine("e\n")
                } else if (fields[0].compareTo("hole") == 0 && fields.size > 2) {
                    try {
                        var s = Socket()
                        s.reuseAddress = true
                        s.bind(InetSocketAddress(15601))
                        try {
                            s.connect(InetSocketAddress(fields[1], Integer.valueOf(fields[2])), 50)
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

                } else if (fields[0].compareTo("kill-service") == 0) {      // debugging
                    stopSelf()
                } else {
                    sendLine("Unknown cmd\n")
                }
            }
        }

        // tunnelling data
        protected fun do_tunneling() {
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
    protected inner class PwvServer {
        protected var mThread: Thread
        protected var server: ServerSocket? = null

        init {

            val prefs = getSharedPreferences("pwv", 0)
            val sPort = prefs.getInt("loginPort", 15600)

            try {
                server = ServerSocket(sPort)
                server!!.soTimeout = 1800000
            } catch (e: IOException) {
                server = null
            }

            mThread = Thread(Runnable {
                while (server != null && !server!!.isClosed) {
                    try {
                        val s = server!!.accept()
                        if (s != null) {
                            val conn = adConn(s)
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
                    if (server != null && !server!!.isClosed)
                        server!!.close()
                } catch (e: IOException) {
                }

                stopSelf()
            })
        }

        internal fun start() {
            if (!mThread.isAlive) {
                mThread.start()
            }
        }

        internal fun close() {
            try {
                if (server != null && !server!!.isClosed)
                    server!!.close()
            } catch (e: IOException) {
            }

            server = null
            if (mThread.isAlive) {
                mThread.interrupt()
            }

            synchronized(connSet) {
                val it = connSet.iterator()
                while (it.hasNext()) {
                    it.next().close()
                    it.remove()
                }
            }

            synchronized(vServerSet) {
                val it = vServerSet.iterator()
                while (it.hasNext()) {
                    it.next().close()
                    it.remove()
                }
            }

        }

    }

    override fun onCreate() {
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