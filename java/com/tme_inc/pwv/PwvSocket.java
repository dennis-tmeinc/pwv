package com.tme_inc.pwv;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;

/**
 * Created by dennis on 09/06/15.
 */
public class PwvSocket {
    private Socket mSocket ;

    public PwvSocket() {
        mSocket = null ;
    }

    public PwvSocket(Socket s) {
        mSocket = s ;
    }

    // tcp connect
    public boolean connect(String host, int port) {
        close();
        try {
            mSocket = new Socket();
            mSocket.connect(new InetSocketAddress(host, port), 15000);
            mSocket.setSoTimeout(15000);
        } catch (IOException e) {
            mSocket = null;
        }
        return isConnected();
    }

    public void close() {
        if( mSocket!=null ) {
            try {
                if( !mSocket.isClosed() ) {
                    mSocket.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            mSocket = null ;
        }
    }

    public boolean isConnected(){
        return mSocket!=null && mSocket.isConnected();
    }

    public int available() {
        try {
            if( mSocket!=null ) {
                return mSocket.getInputStream().available();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return 0;
    }

    // one call read, wait if no data available
    public int recv1( byte [] rbuf, int offset, int rsize) {
        int r = 0 ;
        if (mSocket != null) {
            try {
                r = mSocket.getInputStream().read(rbuf, offset, rsize);
            } catch (IOException e) {
                r = 0 ;
            }
            if( r<=0 ) {
                close();
            }
        }
        return r;
    }

    public int recv( byte [] rbuf, int offset, int rsize) {
        int tr = 0;         // total read bytes
        int r ;
        while( rsize>tr && (r = recv1(rbuf, offset+tr, rsize-tr)) > 0) {
            tr += r;
        }
        return tr;
    }

    public int recv( byte [] rbuf) {
        return recv(rbuf, 0, rbuf.length );
    }

    public byte[] recv( int size ) {
        byte [] b = new byte[size] ;
        if( recv( b ) == size ) {
            return b ;
        }
        return null;
    }

    // receive one line of input
    public String recvLine() {
        byte[] buffer = new byte[4096];
        int tr = 0;         // total read bytes
        while(tr < buffer.length  && recv1( buffer, tr, 1)>0 ) {
            if( buffer[tr] == '\n' ) {
                // received a new line
                break;
            }
            tr++ ;
        }
        return new String(buffer, 0, tr);
    }

    public int send( byte [] buffer, int offset, int count ) {
        if (mSocket != null) {
            try {
                mSocket.getOutputStream().write(buffer, offset, count);
                return count;
            }
            catch(IOException e){
                close();
            }
        }
        return 0 ;
    }

    public int send( byte [] buffer, int count ) {
        return send( buffer, 0, count );
    }

    public int send( byte [] buffer ) {
        return send( buffer, 0, buffer.length );
    }

    public int sendLine( String line ){
        return send( line.getBytes() );
    }

}
