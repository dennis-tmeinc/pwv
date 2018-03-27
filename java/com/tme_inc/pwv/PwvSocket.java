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
 * Socket wrapper for PW connections
 */
public class PwvSocket implements Closeable {
    private Socket mSocket ;
    private InputStream is ;
    private OutputStream os ;

    public PwvSocket() {
        is = null ;
        os = null ;
        mSocket = null ;
    }

    public PwvSocket(Socket s) {
        is = null ;
        os = null ;
        mSocket = s ;
    }

    // tcp connect
    public boolean connect(String host, int port) {
        try {
            close();
            mSocket = new Socket();
            mSocket.connect(new InetSocketAddress(host, port), 15000);
            mSocket.setSoTimeout(15000);
        } catch (IOException e) {
            mSocket = null;
        }
        return isConnected();
    }

    @Override
    public void close()  {
        is = null ;
        os = null ;
        if( mSocket!=null ) {
            try {
                mSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                mSocket = null ;
            }
        }
    }

    public boolean isConnected(){
        return mSocket!=null && mSocket.isConnected()  ;
    }

    public int available() {
        try {
            if( mSocket!=null ) {
                if( is == null )
                    is = mSocket.getInputStream() ;
                return is.available();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return 0;
    }

    // one shot read, wait if no data available
    public int recv1( byte [] rbuf, int offset, int rsize) {
        int r = 0 ;
        if (mSocket != null) {
            try {
                if( is == null )
                    is = mSocket.getInputStream() ;
                r = is.read(rbuf, offset, rsize);
            } catch (IOException e) {
                r = 0 ;
            }
            if( r<=0 ) {
                close();
            }
        }
        return r;
    }

    // (block) read until buffer is filled, or socket closed
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
        if( recv( b, 0, size ) == size ) {
            return b ;
        }
        return null;
    }

    // receive one line of input
    public String recvLine() {
        byte[] buffer = new byte[8192];
        int tr = 0;         // total read bytes
        while(tr < buffer.length  && recv1( buffer, tr, 1)>0 ) {
            if( buffer[tr] == '\n' || buffer[tr] == 0 ) {
                // received a line
                break;
            }
            tr++ ;
        }
        return new String(buffer, 0, tr);
    }

    public int send( byte [] buffer, int offset, int count ) {
        if (mSocket != null) {
            try {
                if( os==null )
                    os = mSocket.getOutputStream() ;
                os.write(buffer, offset, count);
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
