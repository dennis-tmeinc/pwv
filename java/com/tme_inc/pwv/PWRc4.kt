package com.tme_inc.pwv

import kotlin.experimental.xor

/**
 * Created by dennis on 24/11/15.
 * These codes are broken RC4, but has been used all over TME dvr codes
 */
class PWRc4 {

    private val s = ByteArray(256)
    private var si: Int = 0
    private var sj: Int = 0

    // RC4 key-scheduling algorithm
    //     k: key array, 256 bytes
    // initialize RC4 seed with key block
    fun RC4_KSA(k: ByteArray) {
        for( n in 0..255) {
            s[n] = n.toByte()
        }
        var i : Int
        var j : Int = 0
        for (n in k.indices) {
            i = n and 0xff
            j = (j + s[i] + k[n]) and 0xff

            // swap(s[i], s[j])
            val swap = s[i]
            s[i] = s[j]
            s[j] = swap
        }
        sj = 0
        si = 0
    }

    // *** RC4 block cryption.
    //   Since RC4 is a stream cryption, not a block cryption.
    // So we use RC4 PRGA to generate a block of pesudo random data, encrypt/decrypt
    // by xor original message with this data.

    // RC4 PRGA
    //   The pseudo-random generation algorithm
    fun RC4_PRGA(): Byte {
        si = (si + 1) and 0xff
        sj = (sj + s[si]) and 0xff

        // swap( s[i], s[j])
        val swap = s[si]
        s[si] = s[sj]
        s[sj] = swap

        // The correct RC4 should be:  s[ (s[si] + s[sj]) & 0xff ] ;
        return (swap + s[si]).toByte()
    }

    // RC4 stream data cryption.
    //     text: data to be encrypt/decrypt
    //     offset, len: data size
    // to decrypt/encrypt text, both seed and text will be changed.
    fun RC4_crypt(text: ByteArray, offset: Int, len: Int) {
        // PRGA
        for (n in 0 until len) {
            text[offset + n] = text[offset + n] xor RC4_PRGA()
        }
    }

    companion object {

        // Generate RC4 cryption table
        //      crypt_table: cryption table for block encryption
        //      k: initial key
        fun RC4_crypt_table(crypt_table: ByteArray, k: ByteArray) {
            val rc4 = PWRc4()
            rc4.RC4_KSA(k)            // generate seed ;
            for (i in crypt_table.indices) {
                crypt_table[i] = rc4.RC4_PRGA()
            }
        }


        // RC4 block cryption
        //    text: data to be encrypt/decrypt
        //    textsize: size of data
        //    textoffset: offset of data from start of file (0 for start of file or independent data)
        //    crypt_table: cryption table
        //    table_size: cryption table size
        fun RC4_block_crypt(
            text: ByteArray,
            offset: Int,
            textsize: Int,
            textoffset: Int,
            crypt_table: ByteArray
        ) {
            for (i in 0 until textsize) {
                text[offset + i] = text[offset + i] xor
                        crypt_table[(i + textoffset) % crypt_table.size]
            }
        }
    }


}
