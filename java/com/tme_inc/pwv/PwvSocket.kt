package com.tme_inc.pwv

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel

/**
 * Created by dennis on 09/06/15.
 * Socket wrapper for PW connections
 */
open class PwvSocket( protected var s: Socket? = null) : java.io.Closeable {

    constructor( sc: SocketChannel ) : this(sc.socket())

    private var iStream: InputStream? = null
        get() {
            if( field == null ) {
                field = s?.getInputStream()
            }
            return field
        }

    private var oStream: OutputStream? = null
        get() {
            if( field == null )
                field = s?.getOutputStream()
            return field
        }

    val isConnected: Boolean
        get() = s?.isConnected?:false

    // tcp connect
    fun connect1(host: String, port: Int): Boolean {
        close()

        try {
            val ss = Socket()
            ss.connect(InetSocketAddress(host, port), 15000)
            if( ss.isConnected ) {
                ss.soTimeout = 60000
                s = ss
            }
            else {
                ss.close()
            }
        } catch (e: IOException) {
            s = null
        }

        return s!=null
    }

    fun connect(host: String, port: Int): Boolean {
        close()

        try {
            val sc = SocketChannel.open()
            sc.connect(InetSocketAddress(host, port))
            sc.configureBlocking(true)

            if( sc.isConnected ) {
                s = sc.socket()
            }
            else {
                sc.close()
            }
        } catch (e: IOException) {
            s = null
        }

        return s!=null
    }

    @Synchronized
    override fun close() {
        try {
            s?.close()
        }
        finally {
            iStream = null
            oStream = null
            s = null
        }
    }

    fun available(): Int {
        if (isConnected ) {
            return try {
                iStream?.available()?:0
            } catch (e: IOException) {
                0
            }
        }
        return 0
    }

    // one shot read, wait if no data available
    fun recv(rbuf: ByteArray, offset: Int = 0, rsize: Int = rbuf.size - offset): Int {
        if (isConnected ) {
            return try {
                iStream?.read(rbuf, offset, rsize)?:0
            } catch (e: IOException) {
                0
            }
        }
        return 0
    }

    // (block) read until buffer is filled, or socket closed
    fun recvAll(rbuf: ByteArray, offset: Int = 0, rsize: Int = rbuf.size - offset ): Int {
        var tr = 0         // total read bytes
        while (rsize > tr) {
            val r = recv(rbuf, offset + tr, rsize - tr)
            if (r > 0) {
                tr += r
            } else
                break;
        }
        return tr
    }

    // receive all data upto remainaing buffer
    fun recvBuf(rbuf: ByteBuffer) : Int {
        val r = recvAll( rbuf.array(), rbuf.arrayOffset()+rbuf.position(), rbuf.remaining() )
        if( r>0 ) {
            rbuf.limit(rbuf.position()+r)
        }
        return r
    }

    // receive one line of input
    fun recvLine(): String {
        val buffer = ByteArray(8192)
        var r: Int = 0                  // total read bytes
        while (r < buffer.size && recv(buffer, r, 1) > 0) {
            if (buffer[r] == '\n'.toByte() || buffer[r] == 0.toByte()) {
                // received a line
                r++
                break
            }
            r++
        }
        return String(buffer, 0, r)
    }

    fun send(buffer: ByteArray, offset: Int = 0, count: Int = buffer.size - offset): Int {
        if( isConnected ) {
            return try {
                oStream?.write(buffer, offset, count)
                count
            } catch (e: IOException) {
                0
            }
        }
        return 0
    }

    fun send( buffer: ByteBuffer): Int =
        if (buffer.hasArray() && buffer.hasRemaining())
            send(
                buffer.array(),
                buffer.arrayOffset() + buffer.position(),
                buffer.remaining()
            )
        else
            0

    fun sendLine(line: String): Int {
        return send(line.toByteArray())
    }

}
