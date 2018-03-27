/**
 * DvrClient
 *      DVR protocol, communication with DVR
 * Created by dennis on 12/23/14.
 * 01/18/17, add retry on request()
 */

package com.tme_inc.pwv;

import android.content.SharedPreferences;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;


public class DvrClient extends PwvSocket {

    // Connect mode constance
    public static final int CONN_DIRECT   =  0 ;
    public static final int CONN_REMOTE   =  1 ;
    public static final int CONN_USB      =  2 ;

    static public class Ans {
        public int code = 0;
        public int data = 0;
        public int size = 0;
        public byte [] databuf = null ;
    }

    static public class Req extends Ans {

        public int offset = 0 ;

        public Req ( int c, int d, byte [] dbuf, int o, int s ){
            code = c ;
            data = d ;
            databuf = dbuf ;
            if( databuf!=null ) {
                offset = o ;
                size = s ;
            }
        }

        public Req ( int c, int d, byte [] dbuf ){
            this(c,d,dbuf,0,dbuf.length) ;
        }

        public Req ( int c, int d ){
            this(c,d,null,0,0);
        }

        public Req ( int c ){
            this(c,0,null,0,0);
        }
    }

    protected int connectMode ;
    protected String mHost;
    protected int mPort;

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

    protected boolean sendReq( Req req )
    {
        if( connect() ) {
            byte [] ba = new byte [12] ;
            ByteBuffer bb = ByteBuffer.wrap(ba);
            bb.order(ByteOrder.LITTLE_ENDIAN);
            bb.putInt(req.code);
            bb.putInt(req.data);
            bb.putInt(req.size);
            if( send( ba ) > 0 ) {
                if (req.size > 0 && req.databuf != null ) {
                    if (send(req.databuf, req.offset, req.size) > 0) {
                        return true;
                    }
                }
                else {
                    return true;
                }
            }
        }
        close();
        return false ;
    }

    public Ans recvAns() {
        Ans ans = new Ans();
        byte[] r = recv(12);
        if (r != null) {
            ByteBuffer bb = ByteBuffer.wrap(r);
            bb.order(ByteOrder.LITTLE_ENDIAN);
            ans.code = bb.getInt();
            ans.data = bb.getInt();
            ans.size = bb.getInt();

            if (ans.code > 0 && ans.size > 0 && ans.size < 10000000) {
                ans.databuf = recv(ans.size);
            }
        }
        return ans;
    }

    public Ans request( Req req )
    {
        int retry ;
        for( retry =0 ; retry<3 ; retry++ ) {
            if( sendReq( req ) ) {
                Ans ans = recvAns() ;
                if( ans.code>0 ) {
                    return ans ;
                }
            }
            close();
        }
        return new Ans() ;      // empty ans (error)
    }

    public Ans request( int reqcode, int reqdata, byte[] dataarray, int offset,  int reqsize )
    {
       return request( new Req(reqcode, reqdata, dataarray, offset, reqsize) );
    }

    public Ans request( int reqcode, int reqdata, byte[] dataarray )
    {
        if( dataarray == null ) {
            return request( new Req(reqcode, reqdata) );
        }
        else {
            return request( new Req(reqcode, reqdata, dataarray) );
        }
    }

    public Ans request( int reqcode, int reqdata )
    {
        return request( new Req(reqcode, reqdata) );
    }

    public Ans request( int reqcode )
    {
        return request( new Req(reqcode) );
    }
}
