package com.tme_inc.pwv

import android.app.Service
import android.content.Intent
import android.os.IBinder
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import kotlin.collections.ArrayList

/**
 * Created by dennis on 09/06/15.
 */
class PwvService : Service() {

    private val vServerSet = ArrayList<VirtualServer>()
    private val connSet = ArrayList<AdbConn>()

      // virtual server support
    private inner class VirtualServer(val targetDid: String, val targetPort: String) {

        var vsRun: Boolean = false
        private var server: ServerSocket? = null

        private var mThread = Thread {
            while (vsRun && server != null && !server!!.isClosed) {
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
                    vsRun = false
                }
            }
            try {
                synchronized(this) {
                    server?.close()
                }
            } catch (e: IOException) {
            }
            finally {
                vsRun = false
            }
        }

        init {
            try {
                server = ServerSocket(0)
                server!!.soTimeout = 30 * 60 * 1000           // time out in half an hour
                vsRun = true
                mThread.start()
            } catch (e: IOException) {
                server = null
                vsRun = false
            }
        }

        val port: Int
            get() = server?.localPort?:0

        fun close() {
            vsRun = false
            synchronized(this) {
                try {
                    server?.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
                finally {
                    server = null
                }
            }
            mThread.interrupt()
        }
    }

    // adb reverse connection
    private inner class AdbConn(s: Socket) {

        constructor(sc:SocketChannel):this(sc.socket())

        var phase = 1      // 0: close, 1: init cmd, 2: unit wait ( or in multi-conn mode ), 3: connecting, 4: connected
        var did = s.remoteSocketAddress.toString()
        var unitname: String = ""

        private var target: AdbConn? = null         // target connection
        private var xoffflag = false
        private var sock = PwvSocket(s)

        private val mThread = Thread{
            while (phase > 0 && sock.isConnected) {
                if (phase <= 2) {
                    processCmd()
                } else if (phase == 3) {        // connecting, wait to be connected or closed
                    try {
                        Thread.sleep(10000)     // 10 seconds wait to be connected
                    } catch (e: InterruptedException) {
                        Thread.interrupted()        // clear interrupted flag
                    }
                    finally {
                        if (phase == 3) {          // not connected in 10s
                            break
                        }
                    }
                } else if (phase == 4) {        // connected
                    if (xoffflag) {
                        try {
                            Thread.sleep(100)
                        } catch (e: InterruptedException) {
                            Thread.interrupted()        // clear interrupted flag
                        }
                    } else {
                        tunneling()
                    }
                } else {
                    phase = 0
                }
            }
            phase = 0
        }

        fun start() {
            mThread.start()
        }

        fun close() {
            // clean up peers
            if (phase == 2) {
                synchronized(connSet) {
                    for( conn in connSet) {
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
            if(mThread.isAlive)
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

        fun sendfrom(data: ByteArray, dsize: Int, from: AdbConn): Int {
            if (phase == 4 && target === from)
                return sock.send(data, 0, dsize)
            else if (phase == 2) {
                sock.sendLine("mdata ${from.did} $dsize\n")
                return sock.send(data, 0, dsize)
            }
            return 0
        }

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

        private fun processCmd() {
            val fields = sock.recvLine().split("\\s+".toRegex()).dropLastWhile { it.isBlank() }
            if (fields.isNotEmpty()) {

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
                            for( conn in connSet ) {
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
                                for( conn in connSet ) {
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
                                for( conn in connSet){
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
                                for(conn in connSet){
                                    if (conn != this && conn.phase == 2) {
                                        conn.phase = 0
                                    }
                                }
                            }
                            phase = 2
                            unitname = fields[1]
                            did = fields[2]
                        }
                    }

                    "vserver" -> {
                        var rsp = "Error\n"

                        // cmd: vserver <sessionid> <deviceid> <port>
                        if (fields.size >= 4) {
                            var vsexist = false
                            synchronized(vServerSet) {
                                // look for existing vserver
                                for (v in vServerSet) {
                                    if (fields[2] == v.targetDid && fields[3] == v.targetPort) {
                                        rsp = "ok * ${v.port}\n"
                                        vsexist = true
                                        break
                                    }
                                }
                            }

                            if (!vsexist) {
                                val v = VirtualServer(fields[2], fields[3])
                                if( v.vsRun ) {
                                    synchronized(vServerSet) {
                                        vServerSet.add(v)
                                        rsp = "ok * ${v.port}\n"
                                    }
                                }
                            }
                        }

                        sendLine(rsp)

                    }

                    "connected" -> {

                        if (fields.size > 1) {
                            // cmd: connected id
                            // find target
                            synchronized(connSet) {
                                for( conn in connSet ) {
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
                                for( conn in connSet ) {
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
                            if (si > 0) {
                                val buffer = ByteArray(si)
                                val r = sock.recvAll(buffer, 0, si)
                                if (r > 0) {
                                    synchronized(connSet) {
                                        for( conn in connSet ) {
                                            if (conn.did == fields[1] && conn.target === this) {
                                                conn.sendfrom(buffer, r, this)
                                                break
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    "xon" -> {
                        if (fields.size > 1) {
                            synchronized(connSet) {
                                for (conn in connSet) {
                                    if (fields[1] == conn.did && conn.target === this) {
                                        conn.xon()
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
                                    if (fields[1] == conn.did && conn.target === this) {
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
        private fun tunneling() {
            if (sock.isConnected && target != null) {
                val buffer = ByteArray(8192)
                val r = sock.recv(buffer)
                if (r > 0) {
                    if (target!!.sendfrom(buffer, r, this) > 0)
                        return         // success
                }
            }
            // tunnel closed or target closed
            phase=0
        }
    }

    // clean up closed sockets
    private fun cleanupSockets() {

        synchronized(connSet) {
            for( i in connSet.lastIndex downTo 0) {
                // clean finished connection
                if( connSet[i].phase <= 0 ) {
                    connSet[i].close()
                    connSet.removeAt(i)
                }
            }
        }

        synchronized(vServerSet) {
            for( i in vServerSet.lastIndex downTo 0) {
                // clean finished connection
                if( !vServerSet[i].vsRun ) {
                    vServerSet[i].close()
                    vServerSet.removeAt(i)
                }
            }
        }
    }

    private lateinit var pwvServer : ServerSocket

    private val pwvThread = Thread {
        while(!pwvServer.isClosed) {
            try {
                val s = pwvServer.accept()
                if (s != null) {
                    val conn = AdbConn(s)
                    synchronized(connSet) {
                        connSet.add(conn)
                    }
                    conn.start()
                }
            } catch (e: IOException) {
            }
            finally {
                cleanupSockets()
            }
        }

        try {
            pwvServer.close()
        } catch (e: IOException) {
        }

        synchronized(connSet) {
            connSet.forEach { it.close() }
            connSet.clear()
        }

        synchronized(vServerSet) {
            vServerSet.forEach { it.close() }
            vServerSet.clear()
        }
    }

    override fun onCreate() {
        super.onCreate()

        val sPort = getSharedPreferences("pwv", 0).getInt("loginPort", 15600)
        pwvServer = ServerSocket(sPort)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if( pwvThread.state == Thread.State.NEW )
            pwvThread.start()

        return Service.START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        // We don't provide binding, so return null
        return null
    }

    override fun onDestroy() {
        pwvServer.close()
        if( pwvThread.isAlive )
            pwvThread.interrupt()
    }
}