/**
 * MeidaFrame
 * Created by dennis on 12/22/14.
 */

package com.tme_inc.pwv;

import java.nio.ByteBuffer;

public class MediaFrame {

    private byte [] m_frame;
    private int m_pos ;
    private int m_len ;

    public long timestamp ;         // frame time stamp in milliseconds
    public int flags ;              // key frame flags

    public MediaFrame( ByteBuffer frame, int off, int len, long pTimestamp, int pFlags ){
        m_frame = frame.array();
        m_pos = off + frame.arrayOffset() ;
        m_len = len ;
        timestamp = pTimestamp ;
        flags = pFlags ;
    }

    public MediaFrame( byte[] frame, int off, int len, long pTimestamp, int pFlags ){
        m_frame = frame;
        m_pos = off ;
        m_len = len ;
        timestamp = pTimestamp ;
        flags = pFlags ;
    }

    public byte get(int idx) {
        return m_frame[m_pos+idx] ;
    }
    public int  geti(int idx) { return ((int)get(idx)) & 0xff ; }
    public byte [] array() {
        return m_frame ;
    }
    public int pos() {
        return m_pos ;
    }
    public int len() {
        return m_len;
    }

}
