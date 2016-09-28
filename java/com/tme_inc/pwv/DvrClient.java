package com.tme_inc.pwv;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * DvrClient
 *      DVR protocol, communication with DVR
 * Created by dennis on 12/23/14.
 */
public class DvrClient extends PwvSocket {

    // Connect mode constance
    public static final int CONN_DIRECT   =  0 ;
    public static final int CONN_REMOTE   =  1 ;
    public static final int CONN_USB      =  2 ;
    private int connectMode ;

    static public class Ans {
        public int code ;
        public int data ;
        public int size ;

        protected byte [] toByteArray() {
            byte [] ba = new byte [12] ;
            ByteBuffer bb = ByteBuffer.wrap(ba);
            bb.order(ByteOrder.LITTLE_ENDIAN);
            bb.putInt(code);
            bb.putInt(data);
            bb.putInt(size);
            return ba ;
        }

        protected void fromByteArray( byte [] buffer ) {
            if( buffer != null ) {
                ByteBuffer bb = ByteBuffer.wrap(buffer);
                bb.order(ByteOrder.LITTLE_ENDIAN);
                code = bb.getInt();
                data = bb.getInt();
                size = bb.getInt();
            }
            else {
                code = 0 ;
                data = 0 ;
                size = 0 ;
            }
        }
    }

    static public class Req extends Ans {
        public Req( int c, int d, int s ){
            code = c ;
            data = d ;
            size = s ;
        }
    }


    protected int mPort;
    private String mHost;

    // remote login connections
    protected String loginServer;
    protected int    loginPort;

    protected String loginSessionId;
    protected String loginTargetId;
    protected String mId;          // my device id

    public DvrClient() {
        SharedPreferences prefs =  pwvApp.appCtx.getSharedPreferences("pwv", 0);

        mHost = prefs.getString("deviceIp", "192.168.1.100");
        mPort = prefs.getInt("dvrPort", 15114);
        connectMode = prefs.getInt("connMode", CONN_DIRECT);

        if (connectMode == CONN_USB ) {
            loginServer = "127.0.0.1";          // local service
        } else {      // default for direct connection (local lan)
            loginServer = prefs.getString("loginServer", "pwrev.us.to");
        }

        loginPort = prefs.getInt("loginPort", 15600);
        loginSessionId = prefs.getString("loginSession", "0");
        loginTargetId = prefs.getString("loginTargetId", "0");
        mId = prefs.getString("aid", "android");          // my device id
    }

    // connect to DVR
    public boolean connect() {
        if (isConnected()) {
            return true;
        }

        close();

        if (connectMode == CONN_DIRECT) {
            return connect(mHost, mPort);
        }
        else {
            connect(loginServer, loginPort);
            if (isConnected()) {
                // use login remote connection (internet)
                sendLine(String.format("remote %s %s %d\n", loginSessionId, loginTargetId, mPort));
            }
            else {
                close();
            }
        }
        return isConnected();
    }

    public String getHttpUrl() {

        if (connectMode == CONN_DIRECT) {
            return "http://" + mHost + "/" ;
        }
        else {
            String r = null;
            connect(loginServer, loginPort);
            if (isConnected()) {
                // cmd: vserver <sessionid> <deviceid> <port>
                sendLine(String.format("vserver %s %s %d\n", loginSessionId, loginTargetId, 80));
                String[] fields = recvLine().split("\\s+");
                if( fields.length>=3 && fields[0].equals("ok") ) {
                    if( fields[1].equals("*") ) {
                        fields[1] = loginServer ;
                    }
                    r = "http://" + fields[1] + ":" + fields[2] + "/" ;
                }
            }
            close();
            return r ;

        }
    }

    public void setConnectMode(int mode) {
        connectMode = mode;

        SharedPreferences prefs =  pwvApp.appCtx.getSharedPreferences("pwv", 0);

        mHost = prefs.getString("deviceIp", "192.168.1.100");
        mPort = prefs.getInt("dvrPort", 15114);

        if ( connectMode == CONN_USB ) {
            loginServer = "127.0.0.1";          // local service
        } else {      // default for direct connection (local lan)
            loginServer = prefs.getString("loginServer", "pwrev.us.to");
        }

        loginPort = prefs.getInt("loginPort", 15600);
        loginSessionId = prefs.getString("loginSession", "0");
        loginTargetId = prefs.getString("loginTargetId", "0");
        mId = prefs.getString("aid", "android");          // my device id
    }

    public boolean sendReq( Req req )
    {
        return send(req.toByteArray())>0 ;
    }

    public boolean sendReq( int reqcode, int data, int reqsize )
    {
        return sendReq(new Req(reqcode, data, reqsize));
    }

    public boolean sendReq( int reqcode, int data, byte[] dataarray )
    {
        int leng ;
        if( dataarray!=null )
            leng = dataarray.length ;
        else
            leng = 0 ;
        sendReq( reqcode, data, leng );
        if( leng>0 ) {
            send(dataarray, 0, leng);
        }
        return true ;
    }

    public boolean recvAns(Ans ans) {
        if( ans != null ) {
            ans.fromByteArray(recv(12));
            return true ;
        }
        else {
            return false ;
        }
    }

    public Ans recvAns() {
        Ans a = new Ans();
        if (recvAns(a)) {
            return a;
        } else {
            return null;
        }
    }

}
