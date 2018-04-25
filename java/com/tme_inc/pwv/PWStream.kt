package com.tme_inc.pwv

import android.media.MediaCodec
import android.os.SystemClock
import android.util.Log
import java.io.FileFilter
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import java.util.Arrays.copyOfRange
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.experimental.and

/**
 * Created by dennis on 1/21/15.
 */
open class PWStream(channel: Int) {

    protected var mVideoFrameQueue: Queue<MediaFrame> = ConcurrentLinkedQueue()
    protected var mAudioFrameQueue: Queue<MediaFrame> = ConcurrentLinkedQueue()
    protected var mTextFrameQueue: Queue<MediaFrame> = ConcurrentLinkedQueue()

    var resolution = 0                           // channel resolution
    protected var mMaxQueue = 1000
    protected var mStarted = false

    protected var x_textframe: MediaFrame? = null
    protected var refFramePts: Long = 0          // reference frame PTS, -1 = not available
    protected var refFrameTS: Long = 0           // reference frame TimeStamp
    protected var frameTS: Long = 0               // last frame TimeStamp

    private var file_encrypted: Boolean = false

    var audio_codec = 1
    var audio_samplerate = 8000

    var devicetype = 0
    var video_height = 0
    var video_width = 0

    var active_timeMillis = SystemClock.uptimeMillis()

    val isActive: Boolean
        get() = SystemClock.uptimeMillis() - active_timeMillis < 30000

    open val isRunning: Boolean
        get() = false

    var totalChannels = 1               // total channel
    val mChannel = channel              // current channel

    protected var mChannelNames = ArrayList<String>()
    val channelName: String
        get() =
            if ( mChannel >= 0 && mChannel < mChannelNames.size)
                mChannelNames[mChannel]
            else
                "Camera ${mChannel + 1}"

    val videoFrame: MediaFrame?
        get() = mVideoFrameQueue.poll()

    val audioFrame: MediaFrame?
        get() = mAudioFrameQueue.poll()

    val textFrame: MediaFrame?
        get() = mTextFrameQueue.poll()

    open fun start() {}

    open fun release() {
        clearQueue()
    }

    protected fun checkTvsKey(connection: DvrClient): Boolean {
        if (connection.isConnected) {
            var ans: DvrClient.Ans

            var tvskey: ByteArray?

            tvskey = readFile("tvskey")
            if (tvskey != null) {
                // send REQCHECKKEY packet
                //          REQCHECKKEY, 303
                ans = connection.request(303, 0, ByteBuffer.wrap(tvskey))
                if (ans.code == 2) {       // ANSOK
                    resetheader(tvskey)
                    return true
                }
            }

            // check tvskey MF5000
            tvskey = readResFile(R.raw.tvskey_mf5000)

            // send REQCHECKKEY packet
            //          REQCHECKKEY, 303
            ans = connection.request(303, 0, ByteBuffer.wrap(tvskey))
            if (ans.code == 2) {       // ANSOK
                saveFile("tvskey", tvskey)
                resetheader(tvskey)
                return true
            }

            // check tvskey MF5001
            tvskey = readResFile(R.raw.tvskey_mf5001)

            // send REQCHECKKEY packet
            //          REQCHECKKEY, 303
            ans = connection.request(303, 0, ByteBuffer.wrap(tvskey))
            if (ans.code == 2) {       // ANSOK
                saveFile("tvskey", tvskey)
                resetheader(tvskey)
                return true
            }

            // check external tvskey
            val extkey = appCtx!!.getExternalFilesDir(null)
            if (extkey!!.isDirectory) {
                val keyfiles = extkey.listFiles(FileFilter { true })
                for (keyfile in keyfiles) {
                    try {
                        tvskey = ByteArray(8000)
                        val fi = FileInputStream(keyfile)
                        val r = fi.read(tvskey)

                        // send REQCHECKKEY packet
                        //          REQCHECKKEY, 303
                        ans = connection.request(303, 0, ByteBuffer.wrap(tvskey, 0, r))
                        if (ans.code == 2) {       // ANSOK
                            saveFile("tvskey", tvskey)
                            resetheader(tvskey)
                            return true
                        }

                        fi.close()

                    } catch (e: FileNotFoundException) {
                        e.printStackTrace()
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }

                }
            }

        }
        return false
    }

    // next frame will be set as reference frame
    protected fun resetTimestamp(Timestamp: Long) {
        refFrameTS = Timestamp
        frameTS = refFrameTS
        refFramePts = 0      // to refresh reference PTS
    }

    // return frame time stamp in milliseconds
    protected fun timeStamp(frame: ByteBuffer): Long {
        // get time stamp from video PES packet
        val pos = frame.position()
        var pts = frame.get(pos + 9).toLong() and 0x0e shl 29
        pts = pts or (frame.getShort(pos + 10).toLong() and 0x0fffe shl 14)
        pts = pts or (frame.getShort(pos + 12).toLong() and 0x0fffe shr 1)
        pts /= 90             // convert 90k hz counter to 1k hz

        if (refFramePts == 0L) {     // renew ref Frame PTS
            refFramePts = pts
        }

        return refFrameTS + (pts - refFramePts)
    }

    protected fun onReceiveFrame(frame: ByteBuffer) {
        active_timeMillis = SystemClock.uptimeMillis()

        while (frame.remaining() >= 40) {
            val pos = frame.position()
            val startcode = frame.getInt(pos)
            val startcodeMasked = startcode and -0x100
            var framelen: Int

            // check packet types
            if (startcode == 0x000001c0) {
                framelen = addAudioFrame(frame)
            } else if (startcode == 0x000001e0) {
                framelen = addVideoFrame(frame)
            } else if (startcode == 0x000001ba) {
                // ps packet header, always 20 bytes
                framelen = (frame.get(pos + 13) and 0x7) + 14
            } else if (startcodeMasked == 0x00000100) {
                // other PES packet, skip it  (0x000001bc)
                framelen = 6 + (frame.getShort(pos + 4).toInt() and 0x0ffff)
            } else if (startcodeMasked == 0x54585400) {   // 'TXT'
                // text
                framelen = addTextFrame(frame)
            } else if (startcodeMasked == 0x47505300) {   // 'GPS'
                frame.order(ByteOrder.LITTLE_ENDIAN)
                framelen = 8 + frame.getShort(pos + 6).toInt()
                frame.order(ByteOrder.BIG_ENDIAN)
            } else if (startcode == file_tag) {
                // a stream header (not encryped)
                file_encrypted = false
                framelen = onHeader(frame)
            } else if (startcode == file_tag_enc) {
                // a stream header , encryped!
                file_encrypted = true
                framelen = onHeader(frame)
            } else {
                Log.d(logTag, "Unrecognized package")
                break
            }
            if (framelen > 0 && framelen <= frame.remaining()) {
                frame.position(pos + framelen)
            } else {
                break
            }
        }
    }

    @Synchronized
    protected fun clearQueue() {
        mVideoFrameQueue.clear()
        mAudioFrameQueue.clear()
        mTextFrameQueue.clear()
        mStarted = false
        // resetTimestamp( 0 );
    }

    @Synchronized
    fun peekVideoFrame(): MediaFrame? {
        return mVideoFrameQueue.peek()
    }

    @Synchronized
    fun videoAvailable(): Boolean {
        return mVideoFrameQueue.isNotEmpty()
    }

    @Synchronized
    fun peekAudioFrame(): MediaFrame? {
        return mAudioFrameQueue.peek()
    }

    @Synchronized
    fun audioAvailable(): Boolean {
        return mAudioFrameQueue.isNotEmpty()
    }

    @Synchronized
    fun peekTextFrame(): MediaFrame? {
        return mTextFrameQueue.peek()
    }

    @Synchronized
    fun textAvailable(): Boolean {
        return mTextFrameQueue.isNotEmpty()
    }


    @Synchronized
    protected fun onHeader(frame: ByteBuffer): Int {
        val pos = frame.position()
        if (file_encrypted) {
            PWRc4.RC4_block_crypt(
                frame.array(),
                frame.arrayOffset() + pos,
                40,
                0,
                file_encrypt_RC4_table
            )
        }

        val bo = frame.order()
        frame.order(ByteOrder.LITTLE_ENDIAN)
        audio_codec = frame.getShort(pos + 12).toInt()
        audio_samplerate = frame.getInt(pos + 16)
        devicetype = frame.getShort(pos + 6).toInt()
        video_width = frame.getShort(pos + 24).toInt()
        video_height = frame.getShort(pos + 26).toInt()
        // restore frame bit order
        frame.order(bo)
        return 40
    }

    private fun h264sync(frame: ByteBuffer, pos: Int): Int {
        var off: Int
        val limit = frame.limit() - pos - 8
        off = 0
        while (off < limit) {
            if (frame.get(pos + off).toInt() == 0 &&
                frame.get(pos + off + 1).toInt() == 0 &&
                frame.get(pos + off + 2).toInt() == 0 &&
                frame.get(pos + off + 3).toInt() == 1
            ) {
                return pos + off
            }
            off++
        }
        return -1
    }

    private fun h264sync(frame: ByteBuffer, pos: Int, code: Byte): Int {
        var off: Int
        val limit = frame.limit() - pos - 8
        off = 0
        while (off < limit) {
            if (frame.get(pos + off).toInt() == 0 &&
                frame.get(pos + off + 1).toInt() == 0 &&
                frame.get(pos + off + 2).toInt() == 0 &&
                frame.get(pos + off + 3).toInt() == 1 &&
                frame.get(pos + off + 4) == code
            ) {
                return pos + off
            }
            off++
        }
        return -1
    }

    //private synchronized int addVideoFrame( byte[] frameBuffer, int offset, int len){
    @Synchronized
    protected fun addVideoFrame(frame: ByteBuffer): Int {
        var pos = frame.position()

        // PES header length
        val pesHeaderLen = (frame.get(pos + 8).toInt() and 0xff) + 9

        var pesSize = frame.getShort(pos + 4).toInt() and 0x0ffff
        if (pesSize > 0) {        // big frame, use len as frame size
            pesSize += 6
        } else if (pesHeaderLen > 18) {
            // large PES packet size
            pesSize = frame.getInt(pos + 15) + pesHeaderLen
        }

        if (pesSize > frame.remaining() || pesSize < pesHeaderLen) {
            frame.position(frame.limit())      // invalidate frame buffer
            return 0
        }

        // decrypt video frame
        if (file_encrypted) {
            val fa = frame.array()
            var o = frame.arrayOffset() + pos + pesHeaderLen
            var rem = pesSize - pesHeaderLen
            while (rem > 10) {
                if (fa[o].toInt() == 0 && fa[o + 1].toInt() == 0 && fa[o + 2].toInt() == 1 &&
                    fa[o + 3] and 0x9b.toByte() == 1.toByte()
                ) {
                    // found enc start point
                    o += 4
                    rem -= 4
                    if (rem > 1024) rem = 1024
                    PWRc4.RC4_block_crypt(fa, o, rem, 0, file_encrypt_RC4_table)
                    break
                }
                o++
                rem--
            }
        }

        // get time stamp from video PES packet
        frameTS = timeStamp(frame)
        if (x_textframe != null) {
            // patch osd frame timestamp
            x_textframe!!.timestamp = frameTS
            x_textframe = null
        }

        var flags = 0
        if (frame.get(pos + pesHeaderLen + 4) and 0x0f != 1.toByte()) {
            flags = MediaCodec.BUFFER_FLAG_KEY_FRAME
            if (mVideoFrameQueue.size > mMaxQueue) {
                clearQueue()
            }
        }

        pos += pesHeaderLen
        val len = pesSize - pesHeaderLen
        if (!mStarted) {
            val sync0x67 = h264sync(frame, pos, 0x67.toByte())
            if (sync0x67 >= 0) {
                var syncend = h264sync(frame, sync0x67 + 4)
                if (syncend > 0 && frame.get(syncend + 4).toInt() == 0x68) {
                    syncend = h264sync(frame, syncend + 4)
                }
                if (syncend > sync0x67 + 8 && syncend < pos + len) {
                    mStarted = true
                    mVideoFrameQueue.offer(
                        MediaFrame(
                            frame,
                            sync0x67,
                            syncend - sync0x67,
                            frameTS,
                            MediaCodec.BUFFER_FLAG_CODEC_CONFIG
                        )
                    )
                    mVideoFrameQueue.offer(
                        MediaFrame(
                            frame,
                            syncend,
                            len - (syncend - pos),
                            frameTS,
                            flags
                        )
                    )
                }
            }
        } else {
            mVideoFrameQueue.offer(MediaFrame(frame, pos, len, frameTS, flags))
        }
        return pesSize
    }

    @Synchronized
    protected fun addAudioFrame(frame: ByteBuffer): Int {
        val pos = frame.position()

        // PES header length
        val pesHeaderLen = (frame.get(pos + 8).toInt() and 0xff) + 9
        val pesSize = 6 + (frame.getShort(pos + 4).toInt() and 0x0ffff)

        if (pesSize > frame.remaining() || pesSize < pesHeaderLen) {
            frame.position(frame.limit())      // invalidate frame buffer
            return 0
        }

        // decrypt audio frame
        if (file_encrypted) {
            val fa = frame.array()
            val o = frame.arrayOffset() + pos + pesHeaderLen
            var rem = pesSize - pesHeaderLen
            if (rem > 0) {
                if (rem > 1024) rem = 1024
                PWRc4.RC4_block_crypt(fa, o, rem, 0, file_encrypt_RC4_table)
            }
        }

        // get time stamp from video PES packet
        frameTS = timeStamp(frame)
        mAudioFrameQueue.offer(
            MediaFrame(
                frame,
                pos + pesHeaderLen,
                pesSize - pesHeaderLen,
                frameTS,
                0
            )
        )

        return pesSize
    }

    @Synchronized
    protected fun addTextFrame(frame: ByteBuffer): Int {
        val pos = frame.position()
        val xorder = frame.order()
        frame.order(ByteOrder.LITTLE_ENDIAN)
        val txtLen = frame.getShort(pos + 6).toInt()
        frame.order(xorder)

        x_textframe = MediaFrame(frame, pos + 8, txtLen, frameTS, 0)
        mTextFrameQueue.offer(x_textframe)
        return 8 + txtLen
    }

    companion object {

        private val logTag = "PWStream"

        private val file_encrypt_RC4_table = ByteArray(1024)
        private var file_tag: Int = 0
        private var file_tag_enc: Int = 0

        init {
            // initial file decryption table
            val tvskey = readResFile(R.raw.tvskey_mf5000)
            resetheader(tvskey)
        }

        fun setheader(fileheader: ByteArray) {
            val bf = ByteBuffer.wrap(fileheader)
            file_tag = bf.getInt(0)

            // encrypted header tag
            val fh = Arrays.copyOf(fileheader, 4)
            PWRc4.RC4_block_crypt(fh, 0, 4, 0, file_encrypt_RC4_table)
            file_tag_enc = ByteBuffer.wrap(fh).getInt(0)
        }

        fun resetheader(tvskey: ByteArray?) {
            // initial file decryption table
            PWRc4.RC4_crypt_table(file_encrypt_RC4_table, copyOfRange(tvskey!!, 684, 940))

            // default non encrypted header tag
            val file_header = byteArrayOf(0x46, 0x45, 0x4d, 0x54)
            setheader(file_header)
        }
    }

}
