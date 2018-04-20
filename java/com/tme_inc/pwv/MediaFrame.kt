/**
 * MeidaFrame
 * Created by dennis on 12/22/14.
 */

package com.tme_inc.pwv

import java.nio.ByteBuffer


class MediaFrame(frame: ByteBuffer, offset: Int, val len: Int, var timestamp: Long, val flags: Int) {
    // frame time stamp in milliseconds
    // key frame flags
    val array = frame.array()
    // position offset related to the array
    val pos = frame.arrayOffset() + offset

    operator fun get(i: Int): Byte {
        return array[pos + i]
    }

    fun getInt(i: Int): Int {
        return this[i].toInt() and 0xff
    }
}
