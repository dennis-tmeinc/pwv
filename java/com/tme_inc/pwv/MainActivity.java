package com.tme_inc.pwv;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.CursorJoiner;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Scroller;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MainActivity extends Activity {
    private int     m_connectMode ;
    private String  m_dvrIp ;
    private PWProtocol m_pwProtocol = null ;

    protected final String googleDownload = "https://drive.google.com/uc?export=download&id=" ;
    protected final String PWVFileId = "0B3EjTUrzgBepalpMbG1GdGJvMlU" ;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);

        m_dvrIp = "192.168.1.100" ;
        m_connectMode = DvrClient.Companion.getCONN_USB();

        ListView listView = (ListView)findViewById( R.id.devicelist ) ;
        listView.setAdapter(new ArrayAdapter(this,
                    android.R.layout.simple_list_item_1, m_DeviceNameList));
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {
                onSelectDevice(position);
            }
        });


        Button button = (Button)findViewById(R.id.button_usb);
        button.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                //Intent intent = new Intent(getBaseContext(), Playback.class);
                //intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                //startActivity(intent);
                m_connectMode = DvrClient.Companion.getCONN_USB();
                updateDeviceList(true);

            }
        });

        button = (Button)findViewById(R.id.button_local);
        button.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                //Intent intent = new Intent(getBaseContext(), Liveview.class);
                //intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                //startActivity(intent);
                m_connectMode = DvrClient.Companion.getCONN_DIRECT();
                updateDeviceList(true);
            }
        });

        button = (Button)findViewById(R.id.button_remote);
        button.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                if( m_connectMode == DvrClient.Companion.getCONN_REMOTE()) {
                    Intent intent = new Intent(getBaseContext(), LoginActivity.class);
                    startActivity(intent);
                }
                else {
                    m_connectMode = DvrClient.Companion.getCONN_REMOTE();
                    updateDeviceList(true);
                }
            }
        });


        Intent i = new Intent( this, PwvService.class);
        startService(i);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        updateDeviceList( true );
    }

    void showMessage(String msg) {
        Toast toast = Toast.makeText(this, msg, Toast.LENGTH_LONG);
        toast.setGravity(Gravity.CENTER, 0, 0);
        toast.show();
    }

    class UpdateTask extends AsyncTask {

        public boolean manual = false ;
        JSONObject newversion = null;
        String error = null ;

        @Override
        protected Object doInBackground(Object[] params) {
            try {
                HttpURLConnection.setFollowRedirects(true);
                HttpURLConnection gdConnection = (HttpURLConnection) new URL(googleDownload + PWVFileId).openConnection();
                gdConnection.setConnectTimeout(10000);           //set timeout to 10 seconds
                if ( gdConnection.getResponseCode() == HttpURLConnection.HTTP_OK ) {
                    InputStream in = gdConnection.getInputStream();
                    byte[] content = new byte[8000];
                    int r = in.read(content);
                    if (r > 5) {
                        newversion = new JSONObject(new String(content, 0, r));
                    }
                }
                gdConnection.disconnect();
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
                error = "Please Connect To Internet!" ;
            } catch (JSONException e) {
                e.printStackTrace();
            }

            return null;
        }

        @Override
        protected void onPostExecute(Object o) {
            super.onPostExecute(o);
            if( newversion!=null ) {
                try {
                    int latestVersion = newversion.getInt("VersionCode");
                    if (latestVersion > BuildConfig.VERSION_CODE) {

                        final String pwvId = newversion.getString("Id");
                        final String pwvFile = newversion.getString("File");
                        final String pwvVersion = newversion.getString("Version");

                        AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
                        alertDialog.setTitle("Info");
                        alertDialog.setMessage("A new version of PW Controller is available, click OK to update.");
                        alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();

                                        // we have a new version apk availab !
                                        final DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                                        Uri downloadUri = Uri.parse(googleDownload + pwvId);
                                        DownloadManager.Request request = new DownloadManager.Request(downloadUri);
                                        request.allowScanningByMediaScanner();
                                        request.setTitle("PW Controller : version "+pwvVersion)
                                                .setDescription(pwvFile)
                                                .setVisibleInDownloadsUi(true)
                                                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, pwvFile);
                                        long pwvDownloadId = 0 ;
                                        try {
                                            pwvDownloadId = dm.enqueue(request);
                                        }catch (Exception e) {
                                            e.printStackTrace();
                                        }

                                        /*
                                        if(pwvDownloadId != 0) {
                                            registerReceiver(new BroadcastReceiver() {
                                                @Override
                                                public void onReceive(Context context, Intent intent) {
                                                    intent = new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS);
                                                    startActivity(intent);
                                                }
                                            }, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
                                        }
                                        */

                                    }
                                });
                        alertDialog.show();
                    }
                    else {
                        if( manual ) {
                            showMessage("You have latest software installed!") ;
                        }
                    }
                }catch(JSONException e){
                }
            }
            else {
                if( manual && error!=null ) {
                    showMessage(error);
                }
            }

            // update last check time
            SharedPreferences prefs = getSharedPreferences("pwv", 0);
            SharedPreferences.Editor prefEdit = prefs.edit();
            int currentHour = (int)( System.currentTimeMillis() / 3600000);
            prefEdit.putInt("UpdateCheckTime", currentHour);
            prefEdit.commit();

            updateTask = null ;
        }
    } ;

    UpdateTask updateTask = null ;

    void checkForUpdates( boolean manual ) {
        // start auto update task
        if( updateTask == null ) {
            updateTask = new UpdateTask();
            updateTask.manual = manual ;
            updateTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);

        SharedPreferences prefs = getSharedPreferences("pwv", 0);
        m_connectMode = prefs.getInt("connMode", DvrClient.Companion.getCONN_DIRECT());
        m_dvrIp = prefs.getString("dvrIp", "192.168.1.100");

        if( m_pwProtocol == null ) {
            m_pwProtocol = new PWProtocol();
        }
        updateDeviceList(true);

        boolean autoCheckUpdate = prefs.getBoolean("autoCheckUpdate", true);
        int autoCheckInterval = Integer.parseInt( prefs.getString("autoCheckInterval", "24") );
        int updchecktime = prefs.getInt("UpdateCheckTime", 0) ;
        int currentHour = (int)( System.currentTimeMillis() / 3600000);

        if( autoCheckUpdate && currentHour - updchecktime > autoCheckInterval ) {
            checkForUpdates( false ) ;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if(m_pwProtocol!=null ) {
            m_pwProtocol.cancel();
            m_pwProtocol = null ;
        }

        SharedPreferences prefs = getSharedPreferences("pwv", 0);
        SharedPreferences.Editor prefEdit = prefs.edit();
        prefEdit.putInt("connMode", m_connectMode);
        prefEdit.commit() ;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.mainmenu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            // Intent intent = new Intent(getBaseContext(), Launcher.class);
            Intent intent = new Intent(getBaseContext(), SettingsActivity.class);
            startActivity(intent);
            return true;
        }
        else if( id == R.id.action_checkupdate ) {
            checkForUpdates( true ) ;
        }
        else if( id == R.id.action_about ) {
            AboutDialogFragment aboutDialog = new AboutDialogFragment();
            aboutDialog.show(getFragmentManager(), "tagAbout");
        }
        return super.onOptionsItemSelected(item);
    }

    protected void alertLoginError(){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Login error!")
                .setTitle("Error!")
                .show();
    }

    protected void alertNoDevice(){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("No device detected!")
                .setTitle("Error!")
                .show();
    }

    String m_sessionId ;
    ArrayList<String> m_DeviceIdList =new ArrayList<String>() ;          // device IP for local, device ID for remote
    ArrayList<String> m_DeviceNameList =new ArrayList<String>() ;        // device name list

    protected void onSelectDevice( int which ) {
        // The 'which' argument contains the index position
        SharedPreferences pref = getSharedPreferences("pwv", 0);
        SharedPreferences.Editor prefEdit = pref.edit();

        String id = m_DeviceIdList.get(which);
        if( m_connectMode == DvrClient.Companion.getCONN_DIRECT()) {
            prefEdit.putString("deviceIp", id);
        }
        else {
            prefEdit.putString("loginTargetId", id);
            prefEdit.putString("loginSession", m_sessionId);
        }
        prefEdit.putInt("connMode", m_connectMode);

        prefEdit.commit();

        // Start Live View
        Intent intent = new Intent(getBaseContext(), Liveview.class);
        startActivity(intent);

        // delete Protocol so not to rescan
        if(m_pwProtocol!=null ) {
            m_pwProtocol.cancel();
            m_pwProtocol = null ;
        }

    }

    private String detectString ;
    protected void updateNameList()
    {
        int ndevs = m_DeviceNameList.size();
        String devs = "";
        if(ndevs==0) {
            devs = "No device detected from "+detectString+"!" ;
        }
        else if( ndevs == 1 ) {
            devs = "1 device detected from "+detectString+"." ;
        }
        else {
            devs = "Total "+ndevs+ " devices detected from "+detectString+"." ;
        }
        ((TextView) findViewById(R.id.deviceheader)).setText(devs);
        findViewById(R.id.devicedetect).setVisibility(View.INVISIBLE);

        findViewById( R.id.devicelist ).invalidate();

        if( ndevs == 1 && m_connectMode == DvrClient.Companion.getCONN_USB()) {
            onSelectDevice(0) ;
        }

    }

    void setButtonColor()
    {
        int color1 , color2, color3 ;
        color1=color2=color3=R.color.button_main ;
        if( m_connectMode == DvrClient.Companion.getCONN_USB()) {
            color1 = R.color.button_selected ;
            detectString="USB" ;
        }
        else if( m_connectMode == DvrClient.Companion.getCONN_REMOTE()) {
            color2 =R.color.button_selected ;
            detectString="Internet" ;
        }
        else {
            color3 = R.color.button_selected ;
            detectString="local network" ;
        }

        ((Button) findViewById(R.id.button_usb)).setBackgroundResource(color1);
        ((Button) findViewById(R.id.button_remote)).setBackgroundResource(color2);
        ((Button) findViewById(R.id.button_local)).setBackgroundResource(color3);
    }

    protected Runnable runUpdateList = new Runnable() {
        @Override
        public void run() {
            updateNameList();
        }
    };

    protected void postUpdateList( )
    {
        ListView listView = (ListView)findViewById( R.id.devicelist ) ;
        listView.removeCallbacks(runUpdateList);
        listView.postDelayed(runUpdateList, 1000);
    }

    private void updateLocalDevice(){
        m_pwProtocol.DeviceList(new PWProtocol.PWListener() {
            @Override
            public void onPWEvent(Bundle result) {
                if (result == null) {
                    return;            // from cancelled protocol
                }

                if ( result.getBoolean("Complete", true) ) {
                    // complete
                    ListView listView = (ListView) findViewById(R.id.devicelist);
                    listView.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            updateDeviceList(false);
                        }
                    }, 15000);
                } else {
                    String ip = result.getString("deviceIP");
                    String name = result.getString("deviceName");

                    if (!m_DeviceIdList.contains(ip)) {
                        m_DeviceIdList.add(ip);
                        m_DeviceNameList.add(name);
                    }
                }

                postUpdateList();

            }
        }, m_dvrIp );
    }

    private void updateRemoteDevice(){
        SharedPreferences prefs = getSharedPreferences("pwv", 0);

        String user ;
        if( m_connectMode == DvrClient.Companion.getCONN_USB()) {
            user = "usb" ;
        }
        else {
            user = prefs.getString("loginUser", "nouser");
        }
        if( user.length()==0 ) {
            user="EMPTY" ;
        }
        String pass = prefs.getString("loginKey", "nokey" );
        if( pass.length()==0 ) {
            pass="EMPTY" ;
        }
        String accessKey = prefs.getString("accessKey", "" );
        if( accessKey.length()==0 ) {
            accessKey="EMPTY" ;
        }

        m_pwProtocol.setConnectMode(m_connectMode);

        m_pwProtocol.RemoteLogin(new PWProtocol.PWListener() {
                                 @Override
                                 public void onPWEvent(Bundle result) {
                                     if (result == null) {
                                         return ;
                                     }
                                     int numDevice = 0;
                                     m_DeviceIdList.clear();
                                     m_DeviceNameList.clear();

                                     if( result.getString("sessionId", "0").length() < 2) {
                                         numDevice = 0 ;
                                     }
                                     else {
                                         numDevice = result.getInt("numberOfDevices", 0);
                                     }

                                     if( numDevice > 0 ) {
                                         m_sessionId = result.getString("sessionId");
                                         for (int i = 0; i < numDevice; i++) {
                                             m_DeviceIdList.add(result.getString("id" + i));
                                             m_DeviceNameList.add(result.getString("name" + i));
                                         }
                                     }
                                     ListView listView = (ListView)findViewById( R.id.devicelist ) ;
                                     listView.postDelayed(new Runnable() {
                                         @Override
                                         public void run() {
                                             updateDeviceList(false);
                                         }
                                     }, 15000);

                                     postUpdateList();

                                     return;
                                 }
                             },
                user, pass, accessKey);
    }

    protected void updateDeviceList( boolean refresh ) {

        if(m_pwProtocol==null ) {
            return ;
        }

        if( refresh ) {
            m_DeviceIdList.clear();
            m_DeviceNameList.clear();
            ((TextView) findViewById(R.id.deviceheader)).setText("Detecting device...");
            findViewById(R.id.devicedetect).setVisibility(View.VISIBLE);
            setButtonColor();
            m_pwProtocol.cancel();
        }

        if( m_pwProtocol.isBusy() ) {
            // do this again later
            ListView listView = (ListView)findViewById( R.id.devicelist ) ;
            listView.postDelayed(new Runnable() {
                @Override
                public void run() {
                    updateDeviceList(false);
                }
            }, 5000);
            return ;
        }

        if (m_connectMode == DvrClient.Companion.getCONN_DIRECT()) {
            updateLocalDevice();
        } else {
            updateRemoteDevice();
        }
    }

}
