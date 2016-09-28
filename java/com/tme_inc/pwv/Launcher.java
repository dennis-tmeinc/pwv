package com.tme_inc.pwv;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;

public class Launcher extends Activity {

    int mdvrPort = 15114 ;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launcher);

        SharedPreferences prefs = getSharedPreferences("pwv",0);
        String officerId = prefs.getString("officerId", "00001");
        String dvrIp = prefs.getString("dvrIp","192.168.1.100");
        mdvrPort = prefs.getInt("dvrPort", 15114);
        boolean autoStart = prefs.getBoolean("AutoStart", false) ;

        EditText editText = (EditText)findViewById(R.id.OfficerID);
        editText.setText(officerId);
        editText = (EditText)findViewById(R.id.DVRIPAddress);
        editText.setText(dvrIp);

        CheckBox cbAutoStart = (CheckBox) findViewById(R.id.autostart);
        cbAutoStart.setChecked(autoStart);

        Button button = (Button)findViewById(R.id.button_start);
        button.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                savePref();
                Intent intent = new Intent(getBaseContext(), Liveview.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
        });

    }

    private void savePref()
    {
        SharedPreferences prefs = getSharedPreferences("pwv",0);
        SharedPreferences.Editor ed = prefs.edit();
        EditText editText = (EditText) findViewById(R.id.OfficerID);
        String str = editText.getText().toString();
        ed.putString("officerId", str);

        editText = (EditText) findViewById(R.id.DVRIPAddress);
        str = editText.getText().toString();
        ed.putString("dvrIp", str);

        ed.putInt("dvrPort", mdvrPort);

        ed.putInt("channel",0);

        CheckBox cbAutoStart = (CheckBox) findViewById(R.id.autostart);
        boolean autoStart = cbAutoStart.isChecked();
        ed.putBoolean("AutoStart", autoStart) ;

        ed.commit();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        savePref();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
//++        getMenuInflater().inflate(R.menu.menu_launcher, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
