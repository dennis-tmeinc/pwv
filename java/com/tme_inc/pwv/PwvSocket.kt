package com.tme_inc.pwv

import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer

/**
 * Created by dennis on 09/06/15.
 * Socket wrapper for PW connections
 */
open class PwvSocket(s: Socket? = null) : Closeable {
    private var iStream: InputStream? = null
    private var oStream: OutputStream? = null
    private var mSocket: Socket? = null

    init {
        mSocket = s
    }

    val isConnected: Boolean
        get() = mSocket != null && mSocket!!.isConnected

    // tcp connect
    fun connect(host: String, port: Int): Boolean {
        try {
            close()
            mSocket = Socket()
            mSocket!!.connect(InetSocketAddress(host, port), 15000)
            mSocket!!.soTimeout = 15000
        } catch (e: IOException) {
            mSocket = null
        }

        return isConnected
    }

    override fun close() {
        if (mSocket != null) {
            try {
                mSocket!!.close()
            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                mSocket = null
                iStream = null
                oStream = null
            }
        }
    }

    fun available(): Int {
        try {
            if (mSocket != null) {
                if (iStream == null)
                    iStream = mSocket!!.getInputStream()
                return iStream!!.available()
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }

        return 0
    }

    // one shot read, wait if no data available
    fun recv1(rbuf: ByteArray, offset: Int, rsize: Int): Int {
        var r = 0
        if (mSocket != null) {
            try {
                if (iStream == null)
                    iStream = mSocket!!.getInputStream()
                if( iStream != null )
                    r = iStream!!.read(rbuf, offset, rsize)
            } catch (e: IOException) {
                r = 0
            }

            if (r <= 0) {
                close()
            }
        }
        return r
    }

    // (block) read until buffer is filled, or socket closed
    fun recv(rbuf: ByteArray, offset: Int = 0, rsize: Int = rbuf.size - offset ): Int {
        var tr = 0         // total read bytes
        while (rsize > tr) {
            val r = recv1(rbuf, offset + tr, rsize - tr)
            if (r > 0) {
                tr += r
            } else
                break;
        }
        return tr
    }

    fun recv(rbuf: ByteBuffer) : Int {
        val r = recv( rbuf.array(), rbuf.arrayOffset()+rbuf.position(), rbuf.remaining() )
        if( r>0 ) {
            rbuf.limit(rbuf.position()+r)
        }
        return r
    }

    // receive one line of input
    fun recvLine(): String {
        val buffer = ByteArray(8192)
        var r: Int = 0                  // total read bytes
        while (r < buffer.size && recv1(buffer, r, 1) > 0) {
            if (buffer[r] == '\n'.toByte() || buffer[r].toInt() == 0) {
                // received a line
                break
            }
            r++
        }
        return String(buffer, 0, r)
    }

    @JvmOverloads
    fun send(buffer: ByteArray, offset: Int = 0, count: Int = buffer.size - offset): Int {
        if (mSocket != null) {
            try {
                if (oStream == null)
                    oStream = mSocket!!.getOutputStream()
                oStream!!.write(buffer, offset, count)
                return count
            } catch (e: IOException) {
                close()
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
