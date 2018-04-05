package com.tme_inc.pwv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;


public class LoginActivity extends Activity {

    protected String m_sessionId ;
    protected int m_numDevice ;
    protected String [] m_DeviceName ;
    protected String [] m_DeviceId ;

    protected void onSelectDevice( int which ) {
        // The 'which' argument contains the index position
        SharedPreferences pref = getSharedPreferences("pwv", 0);
        SharedPreferences.Editor prefEdit = pref.edit();

        prefEdit.putInt("connMode", DvrClient.Companion.getCONN_REMOTE());     // to use remote login
        prefEdit.putString("loginSession", m_sessionId);
        prefEdit.putString("loginTargetId", m_DeviceId[which]);
        prefEdit.commit();

        // Start Live View
        Intent intent = new Intent(getBaseContext(), Liveview.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    protected void selectDevice(   ) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Device")
                .setItems(m_DeviceName, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        onSelectDevice(which);
                    }
                })
                .show();
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

    protected void onSignIn(){
        String user = ((EditText)findViewById(R.id.edit_username)).getText().toString();
        String pass = ((EditText)findViewById(R.id.edit_password)).getText().toString();
        String accessKey = "";

        PWProtocol protocol = new PWProtocol();
        protocol.RemoteLogin( new PWProtocol.PWListener() {
                @Override
                public void onPWEvent(Bundle result) {
                    if( result == null || result.getString("sessionId","0").length()<2) {
                        alertLoginError();
                        return ;
                    }

                    m_numDevice = result.getInt("numberOfDevices", 0);
                    if( m_numDevice<1 ) {
                        alertNoDevice();
                        return ;
                    }

                    m_sessionId = result.getString("sessionId");
                    m_DeviceId = new String [m_numDevice];
                    m_DeviceName = new String [m_numDevice] ;
                    for( int i=0 ; i<m_numDevice; i++ ) {
                        m_DeviceId[i] = result.getString("id"+i);
                        m_DeviceName[i] = result.getString("name"+i);
                    }
                    selectDevice();

                    return;
                }
            },
        user, pass, accessKey);

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        SharedPreferences pref = getSharedPreferences("pwv", 0);
        String user = pref.getString("loginUser", "");
        boolean remember = pref.getBoolean("loginRememberKey", false);
        String pass = "";
        if( remember )
            pass = pref.getString("loginKey", "");
        String accessKey = pref.getString("accessKey", "");

        ((EditText)findViewById(R.id.edit_username)).setText(user);
        ((EditText)findViewById(R.id.edit_password)).setText(pass);
        ((CheckBox)findViewById(R.id.check_rememberPassword)).setChecked(remember);
        ((EditText)findViewById(R.id.edit_accesskey)).setText(accessKey);

        Button button= (Button)findViewById(R.id.button_connect) ;
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // onSignIn();

                String user = ((EditText)findViewById(R.id.edit_username)).getText().toString();
                String pass = ((EditText)findViewById(R.id.edit_password)).getText().toString();
                boolean remember = ((CheckBox)findViewById(R.id.check_rememberPassword)).isChecked();
                String accessKey = ((EditText)findViewById(R.id.edit_accesskey)).getText().toString();

                // remember password?
                SharedPreferences pref = getSharedPreferences("pwv", 0);
                SharedPreferences.Editor prefEdit = pref.edit();
                prefEdit.putString("loginUser", user);
                prefEdit.putString("loginKey", pass);
                prefEdit.putBoolean("loginRememberKey", remember);
                prefEdit.putString("accessKey", accessKey);
                prefEdit.commit();

                finish();
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

}
