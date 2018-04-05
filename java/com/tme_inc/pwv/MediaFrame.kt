/**
 * MeidaFrame
 * Created by dennis on 12/22/14.
 */

package com.tme_inc.pwv

import java.nio.ByteBuffer

class MediaFrame(aframe: ByteArray, off: Int, l: Int, pTimestamp: Long, pFlags: Int) {

    constructor(frame: ByteBuffer, off: Int, len: Int, pTimestamp: Long, pFlags: Int) : this(
        frame.array(),
        frame.arrayOffset() + off,
        len,
        pTimestamp,
        pFlags
    ) {
    }

    // key frame flags
    val flags: Int = pFlags

    // frame time stamp in milliseconds
    var timestamp: Long = pTimestamp

    @JvmField val array: ByteArray? = aframe

    @JvmField val pos = off

    @JvmField val len = l

    operator fun get(index: Int): Byte {
        return array!![pos + index]
    }

    fun geti(idx: Int): Int {
        return get(idx).toInt() and 0xff
    }

}
