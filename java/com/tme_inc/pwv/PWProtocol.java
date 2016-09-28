package com.tme_inc.pwv;

import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.TreeSet;

/**
 * Created by dennis on 1/13/15.
 *   Use AsyncTask to complete DVR requests
 */
public class PWProtocol extends DvrClient {

    // PW virtual key
    public static final int PW_VK_TM_UP     = 0xe0 ;
    public static final int PW_VK_TM_DOWN   = 0x1e0 ;
    public static final int PW_VK_LP_UP     = 0xe1 ;
    public static final int PW_VK_LP_DOWN   = 0x1e1 ;
    public static final int PW_VK_C1_UP     = 0xe6 ;
    public static final int PW_VK_C1_DOWN   = 0x1e6 ;
    public static final int PW_VK_C2_UP     = 0xe7 ;
    public static final int PW_VK_C2_DOWN   = 0x1e7 ;

    protected PWTask mTask = null ;

    public boolean isBusy() {
        return mTask != null ;
    }

    public void cancel() {
        close();
        if( mTask!=null ) {
            mTask.cancel(true);
            mTask.mlistener = null ;    // disable listener
            mTask = null ;
        }
    }
    //
    // Callback when media output format changes.
    public interface PWListener {
        void onPWEvent(Bundle result);
    }

    abstract class PWTask <T1> extends AsyncTask <T1,String,Bundle> {

        protected PWListener mlistener ;

        public PWTask( PWListener listener ) {
            mlistener = listener ;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            mTask = this ;
        }

        @Override
        protected void onPostExecute(Bundle result) {
            super.onPostExecute(result);
            mTask = null;
            if ( mlistener != null && result != null ) {
                mlistener.onPWEvent(result);
            }
        }
    }

    private class GetVriTask extends PWTask<Void> {

        public GetVriTask( PWListener listener ){
            super(listener);
        }

        @Override
        protected Bundle doInBackground(Void... avoid) {
            Bundle result = null;
            if( connect() ) {
                int vri_s = 0 ;
                int vri_rsize = 468 ;

                result = new Bundle() ;
                Ans ans = new Ans() ;
                // REQ:
                // REQGETDATA   (302)
                // PROTOCOL_PW_GETVRILISTSIZE	(1004)
                sendReq( 302, 1004, 0 );
                // ANS:
                // ANSGETDATA   (302)
                if( recvAns(ans) && ans.code==302 && ans.size>0 ){
                    byte [] buf = recv( ans.size );
                    if( buf != null ) {
                        String vrisize = new String(buf, 0, ans.size).split("\0")[0];
                        String vris[] = vrisize.split(",");
                        if( vris.length>0 ) {
                            vri_s = Integer.parseInt(vris[0]);
                            result.putInt("VriListSize",vri_s );
                        }
                        if( vris.length>1 ) {
                            vri_rsize = Integer.parseInt(vris[1]) ;
                            result.putInt("VriItemSize", vri_rsize);
                        }
                    }
                }
                // REQ:
                // REQGETDATA   (302)
                // PROTOCOL_PW_GETVRILIST	(1005)
                sendReq( 302, 1005, 0 );
                // ANS:
                // ANSGETDATA   (302)
                if( recvAns(ans) && ans.code==302 && ans.size>=vri_rsize ){
                    byte [] buf = recv( ans.size );
                    if( buf != null ) {
                        vri_s = ans.size / vri_rsize ;
                        result.putInt("VriListSize",vri_s );
                        result.putByteArray("VriList", buf);
                    }
                }

            }
            return result;
        }
    }

    public void GetVri( PWListener complete ){
        new GetVriTask(complete).execute() ;
    }

    private class SetVriTask extends PWTask<byte[]> {

        public SetVriTask( PWListener listener ){
            super(listener);
        }

        @Override
        protected Bundle doInBackground(byte[]... vridata) {
            Bundle result = null;
            if( connect() && vridata.length>0 && vridata[0].length>0 ) {
                result = new Bundle();
                Ans ans = new Ans() ;
                // REQ:
                // REQSENDDATA   (301)
                // PROTOCOL_PW_SETVRILIST		(1006)
                sendReq( 301, 1006, vridata[0] );

                // ANS:
                // ANSOK   (2)
                if( recvAns(ans) && ans.code==2 ){
                    result.putInt("Result", 1);
                }

            }
            return result;
        }
    }

    public void SetVri( byte [] vridata, PWListener complete ){
        new SetVriTask(complete).execute(vridata) ;
    }


    private class GetOfficerIDListTask extends PWTask<Void> {

        public GetOfficerIDListTask( PWListener listener ){
            super(listener);
        }

        @Override
        protected Bundle doInBackground(Void... aVoid) {
            Bundle result = null;
            if( connect() ) {
                Ans ans = new Ans() ;

                // REQ:
                // REQGETDATA   (302)
                // PROTOCOL_PW_GETPOLICEIDLIST	(1002)
                sendReq(302, 1002, 0);

                // ANS:
                // ANSGETDATA   (302)
                if( recvAns(ans) && ans.code==302 && ans.size>0 ){
                    byte[] data = recv(ans.size) ;
                    if( data!=null ) {
                        int nid = ans.data ;
                        int idlen = ans.size/nid ;
                        result = new Bundle();
                        result.putInt("policeId_number", ans.data);
                        result.putInt("policeId_size", ans.size );
                        result.putByteArray("policeId_list", data);
                    }
                }

            }
            return result;
        }

    }

    public void GetOfficerIDList( PWListener complete ){
        new GetOfficerIDListTask(complete).execute() ;
    }


    private class SetOfficerIdTask extends PWTask<String> {

        public SetOfficerIdTask( PWListener listener ){
            super(listener);
        }

        @Override
        protected Bundle doInBackground(String ... oids) {
            Bundle result = null;
            if( connect() && oids.length>0 && oids[0]!=null ) {
                String officerId = oids[0]+"\u0000" ;   // append null terminate
                byte [] oidarray = officerId.getBytes();

                result = new Bundle();
                Ans ans = new Ans() ;
                // REQ:
                // REQSENDDATA   (301)
                // PROTOCOL_PW_SETPOLICEID		(1003)
                sendReq( 301, 1003, oidarray.length );
                send(oidarray, 0, oidarray.length);

                // ANS:
                // ANSOK   (2)
                if( recvAns(ans) && ans.code==2 ){
                    result.putInt("Result", 1);
                }
            }
            return result;
        }
    }

    public void SetOfficerId( String officerId, PWListener complete ){
        new SetOfficerIdTask(complete).execute(officerId) ;
    }

    //
    private class SendPWKeyTask extends PWTask<Integer> {

        public SendPWKeyTask( PWListener listener ){
            super(listener);
        }

        @Override
        protected Bundle doInBackground(Integer ... keys) {
            Bundle result = null;
            if( connect() && keys.length>0 ) {
                result = new Bundle();
                Ans ans = new Ans() ;
                // REQ:
                // REQ2KEYPAD   (230)
                sendReq(230, keys[0], 0);

                // ANS:
                // ANSOK   (2)
                if( recvAns(ans) && ans.code==2 ){
                    result.putInt("Result", 1);
                }
            }
            return result;
        }
    }

    public void SendPWKey( int keycode, PWListener complete ){
        new SendPWKeyTask(complete).execute(keycode) ;
    }

    //
    private class SetCovertTask extends PWTask<Boolean> {

        public SetCovertTask(){
            super(null);
        }

        @Override
        protected Bundle doInBackground(Boolean ... covs) {
            if( connect() && covs.length>0 ) {
                Ans ans = new Ans() ;
                byte [] cov = new byte[4] ;
                if( covs[0] ) {
                    cov[0] = 1 ;
                }
                else {
                    cov[0] = 0 ;
                }

                // REQ:
                // REQSENDDATA   (301)
                // PROTOCOL_PW_SETCOVERTMODE		(1009)
                sendReq(301, 1009, cov);

                // ANS:
                // ANSOK   (2)\
                recvAns(ans) ;
            }
            return null;
        }
    }

    public void SetCovertMode( boolean covert ) {
        new SetCovertTask().execute(covert) ;
    }

    //
    private class GetPWStatusTask extends PWTask<Void> {

        public GetPWStatusTask( PWListener listener ){
            super(listener);
        }

        @Override
        protected Bundle doInBackground(Void ... avoid) {
            Bundle result = null;
            if( connect() ) {
                result = new Bundle();
                Ans ans = new Ans() ;

                // REQ:
                // REQGETDATA   (302)
                // PROTOCOL_PW_GETSTATUS   	(1001)
                sendReq(302, 1001, 0);

                // ANS:
                // ANSGETDATA   (302)
                if( recvAns(ans) && ans.code==302 && ans.size>0 ){
                    byte[] data = recv(ans.size) ;
                    if( data!=null ) {
                        result.putByteArray("PWStatus", data);
                    }
                }

                // REQ:
                // REQGETDATA   (302)
                // PROTOCOL_PW_GETDISKINFO   	(1010)
                sendReq(302, 1010, 0);

                // ANS:
                // ANSGETDATA   (302)
                if( recvAns(ans) && ans.code==302 && ans.size>0 ){
                    byte[] data = recv(ans.size) ;
                    if( data!=null ) {
                        String warning = new String(data);
                        result.putString("DiskInfo", warning.trim());
                    }
                }

                /*
                // REQ:
                // REQGETDATA   (302)
                // PROTOCOL_PW_GETWARNINGMSG   	(1008)
                sendReq(302, 1008, 0);

                // ANS:
                // ANSGETDATA   (302)
                if( recvAns(ans) && ans.code==302 && ans.size>0 ){
                    byte[] data = recv(ans.size) ;
                    if( data!=null ) {
                        String warning = new String(data);
                        result.putString("Warningmsg", warning.trim());
                    }
                }
                */

            }
            return result;
        }
    }

    public void GetPWStatus(PWListener complete){
        new GetPWStatusTask(complete).execute() ;
    }

    // Remote Login
    private class RemoteLoginTask extends PWTask<String> {

        public RemoteLoginTask( PWListener listener ){
            super(listener);
        }

        @Override
        protected Bundle doInBackground(String ... params) {
            Bundle result = new Bundle();
            boolean res = true ;

            // connect to remote server directly
            if( connect(loginServer, loginPort) ) {
                String[] fields = null ;
                String nonce = "nonce" ;

                if( res ) {
                    sendLine("session\n");
                    fields = recvLine().split("\\s+");
                    if( fields.length>=2 && fields[0].compareTo("try")==0  ) {
                        nonce = fields[1];
                    }
                }

                if( res ) {
                    // key = md5("nonce+pass+access+mid")
                    String key = "" ;
                    try {
                        MessageDigest digester = MessageDigest.getInstance("MD5");
                        digester.update( nonce.getBytes());
                        digester.update( params[1].getBytes() );
                        digester.update( params[2].getBytes() );
                        digester.update( mId.getBytes());
                        byte[] digest = digester.digest();
                        for(int i=0; i<digest.length; i++ ) {
                            key+=String.format("%02x", digest[i]) ;
                        }
                    } catch (NoSuchAlgorithmException e) {}
                    sendLine(String.format("session %s %s %s %s\n", params[0], key, params[2], mId));
                    fields = recvLine().split("\\s+");
                    if( fields.length<2 || fields[0].compareTo("ok")!=0  ) {
                        res = false ;
                    }
                }

                if( res ) {
                    result.putString("sessionId", fields[1]);
                    sendLine(String.format("list %s *\n", fields[1]));
                    fields = recvLine().split("\\s+");
                    if( fields.length>0 && fields[0].compareTo("rlist")==0  ) {
                        int i = 0 ;
                        while( i<1000 ) {
                            fields = recvLine().split("\\s+");
                            if( fields.length < 2 ) {
                                break;
                            }
                            result.putString("id"+i, fields[0] );
                            result.putString("name"+i, fields[1] );
                            i++ ;
                        }
                        result.putInt("numberOfDevices", i);
                    }
                }
            }

            close();
            result.putBoolean("Result", res);
            return result;
        }
    }

    public void RemoteLogin(PWListener complete, String user, String pass, String accesskey){
        new RemoteLoginTask(complete).execute(user, pass, accesskey);
    }

    // Get local devices list
    private class DeviceListTask extends PWTask<String> {

        public DeviceListTask( PWListener listener ){
            super(listener);
        }

        @Override
        protected Bundle doInBackground(String ... params) {
            Bundle result = new Bundle();
            try {
                // data packet
                //  REQDVREX	0x7986a348
                //  DVRSVREX	0x95349b63

                DatagramSocket udpSocket = new DatagramSocket();
                udpSocket.setSoTimeout(1000);

                //      int reqsize;
                byte[] packet = new byte [4] ;
                ByteBuffer reqDvrEx = ByteBuffer.wrap(packet) ;
                reqDvrEx.order(ByteOrder.LITTLE_ENDIAN);
                reqDvrEx.putInt(0x7986a348);

                // enable Broadcast
                udpSocket.setBroadcast(true);

                // generic broadcast
                udpSocket.send(new DatagramPacket(packet, 0, 4, new InetSocketAddress("255.255.255.255", mPort)));

                // multicast for dvr device
                udpSocket.send(new DatagramPacket(packet, 0, 4, new InetSocketAddress("228.229.230.231", mPort)));

                // send to unicast to preset server ( over internet, maybe )
                if( params.length>0 && params[0]!=null && params[0].length()>1 ) {
                    udpSocket.send(new DatagramPacket(packet, 0, 4, new InetSocketAddress(params[0], mPort)));
                }

                HashSet <String> deviceSet = new HashSet <String> () ;
                // now wait for response
                int  timeout = 10 ;
                while( !isCancelled () && mlistener!=null && timeout > 0 ) {
                    DatagramPacket udpPacket = new DatagramPacket(new byte[64], 64);
                    try {
                        udpSocket.receive(udpPacket);
                    } catch (IOException e) {
                        // timeout catched
                        timeout-- ;
                        continue;
                    }
                    int len = udpPacket.getLength();
                    if (len >= 4) {
                        //  DVRSVREX	0x95349b63
                        ByteBuffer dvrSvrEx = ByteBuffer.wrap(udpPacket.getData(), udpPacket.getOffset(), udpPacket.getLength());
                        dvrSvrEx.order(ByteOrder.LITTLE_ENDIAN);
                        if (dvrSvrEx.getInt() == (int) 0x95349b63) {
                            // response from a PW device
                            String device = udpPacket.getAddress().getHostAddress();
                            if( deviceSet.add(device) ) {
                                if (connect(device, mPort)) {
                                    DvrClient.Ans ans = new DvrClient.Ans();
                                    // get host name
                                    //  REQSERVERNAME, 10
                                    sendReq(10, 0, 0);
                                    //  ANSSERVERNAME  12
                                    if (recvAns(ans) && ans.size > 0 && ans.code == 12) {
                                        byte[] hostname = recv(ans.size);
                                        publishProgress(device, (new String(hostname)).trim());
                                    }
                                }
                                close();
                            }
                        }
                    }
                }
                deviceSet.clear();

                udpSocket.close();

            } catch (SocketException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            result.putBoolean("Complete", true);
            return result;
        }

        @Override
        protected void onProgressUpdate(String... values) {
            super.onProgressUpdate(values);

            if( mlistener!=null ) {
                Bundle progress = new Bundle();
                progress.putBoolean("Complete", false);
                progress.putString("deviceIP", values[0]);
                progress.putString("deviceName", values[1]);
                mlistener.onPWEvent( progress );
            }

        }
    }

    public void DeviceList(PWListener listener, String host){
        new DeviceListTask(listener).execute(host);
    }

    // Get Web Setup URL
    private class WebUrlTask extends PWTask<String> {

        public WebUrlTask( PWListener listener ){
            super(listener);
        }

        @Override
        protected Bundle doInBackground(String ... params) {
            Bundle result = new Bundle();
            result.putString("URL", getHttpUrl());
            result.putBoolean("Complete", true);
            return result;
        }

    }

    public void GetWebUrl(PWListener complete){
        new WebUrlTask(complete).execute() ;
    }

    // Get Video Clip List by disknumber
    private class ClipListTask extends PWTask<Integer> {

        public ClipListTask( PWListener listener ){
            super(listener);
        }

        @Override
        protected Bundle doInBackground(Integer ... params) {
            if( connect() ) {
                Ans ans = new Ans() ;

                int disk = params[0] ;
                int daylist[] = new int [0] ;

                // REQDAYLIST   (238)
                sendReq(238, 0, 0);

                // ANSDAYLIST   (223)
                if( recvAns(ans) && ans.code==223 && ans.size>0 ){
                    byte [] buf = recv( ans.size );
                    if( buf != null ) {
                        ByteBuffer daybuffer = ByteBuffer.wrap(buf);
                        daybuffer.order(ByteOrder.LITTLE_ENDIAN) ;
                        daylist = new int [ans.size/4] ;
                        for( int i=0; i<daylist.length; i++) {
                            daylist[i] = daybuffer.getInt(i*4);
                        }
                    }
                }

                for( int date : daylist) {

                    ByteBuffer request = ByteBuffer.allocate(4) ;
                    request.order(ByteOrder.LITTLE_ENDIAN) ;
                    request.putInt(0, date) ;

                    // REQ:
                    // REQDAYCLIPLIST   (237)
                    sendReq(237, disk, request.array() );

                    // ANS:
                    // ANSDAYCLIPLIST   (222)
                    if( recvAns(ans) && ans.code==222 && ans.size>0 ){
                        byte [] buf = recv( ans.size );
                        if( buf != null ) {
                            String clips = new String(buf, 0, ans.size);
                            String cliplist[] = clips.split(",");
                            publishProgress(cliplist);
                        }
                    }

                }


            }
            Bundle result = new Bundle();
            result.putBoolean("Complete", true);
            return result;
        }

        @Override
        protected void onProgressUpdate(String... values) {
            super.onProgressUpdate(values);
            if( mlistener!=null ) {
                Bundle progress = new Bundle();
                progress.putBoolean("Complete", false);
                progress.putStringArray("clips", values);
                mlistener.onPWEvent( progress );
            }
        }
    }

    public void getClipList(PWListener listener,  int disk){
        new ClipListTask(listener).execute(disk);
    }


    // Get Video Clip List by disknumber
    private class DayListTask extends PWTask<Integer> {

        public DayListTask( PWListener listener ){
            super(listener);
        }

        @Override
        protected Bundle doInBackground(Integer ... params) {
            Bundle result = new Bundle();
            if( connect() ) {
                Ans ans = new Ans() ;

                int daylist[] = new int [0] ;

                // REQDAYLIST   (238)
                sendReq(238, 0, 0);
                // ANSDAYLIST   (223)
                if( recvAns(ans) && ans.code==223 && ans.size>0 ){
                    byte [] buf = recv( ans.size );
                    if( buf != null ) {
                        ByteBuffer daybuffer = ByteBuffer.wrap(buf);
                        daybuffer.order(ByteOrder.LITTLE_ENDIAN) ;
                        daylist = new int [ans.size/4] ;
                        for( int i=0; i<daylist.length; i++) {
                            daylist[i] = daybuffer.getInt(i*4);
                        }
                        result.putIntArray("daylist", daylist);
                    }
                }
            }
            result.putBoolean("Complete", true);
            return result;
        }

    }

    public void getDayList(PWListener listener){
        new DayListTask(listener).execute();
    }

}
