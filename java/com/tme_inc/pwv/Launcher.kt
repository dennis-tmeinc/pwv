package com.tme_inc.pwv

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText

// kotlin synthetic extension
import kotlinx.android.synthetic.main.activity_launcher.*

class Launcher : Activity() {

    internal var mdvrPort = 15114

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_launcher)

        val prefs = getSharedPreferences("pwv", 0)
        mdvrPort = prefs.getInt("dvrPort", 15114)

        OfficerID.setText(prefs.getString("officerId", "00001"))
        DVRIPAddress.setText(prefs.getString("dvrIp", "192.168.1.100"))
        autostart.isChecked = prefs.getBoolean("AutoStart", false)

        button_start.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View) {
                savePref()
                val intent = Intent(baseContext, Liveview::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
        })

    }

    private fun savePref() {
        val prefs = getSharedPreferences("pwv", 0)
        val ed = prefs.edit()
        var editText = findViewById<View>(R.id.OfficerID) as EditText
        var str = editText.text.toString()
        ed.putString("officerId", str)

        editText = findViewById<View>(R.id.DVRIPAddress) as EditText
        str = editText.text.toString()
        ed.putString("dvrIp", str)

        ed.putInt("dvrPort", mdvrPort)

        ed.putInt("channel", 0)

        val cbAutoStart = findViewById<View>(R.id.autostart) as CheckBox
        val autoStart = cbAutoStart.isChecked
        ed.putBoolean("AutoStart", autoStart)

        ed.commit()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        savePref()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        //++        getMenuInflater().inflate(R.menu.menu_launcher, menu);
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        val id = item.itemId


        return if (id == R.id.action_settings) {
            true
        } else super.onOptionsItemSelected(item)

    }
}
