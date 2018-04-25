package com.tme_inc.pwv

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem

// kotlin synthetic extension
import kotlinx.android.synthetic.main.activity_launcher.*

class Launcher : Activity() {

    private var mdvrPort = 15114

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_launcher)

        val prefs = getSharedPreferences("pwv", 0)
        mdvrPort = prefs.getInt("dvrPort", 15114)

        OfficerID.setText(prefs.getString("officerId", "00001"))
        DVRIPAddress.setText(prefs.getString("dvrIp", "192.168.1.100"))
        autostart.isChecked = prefs.getBoolean("AutoStart", false)

        button_start.setOnClickListener({
            savePref()
            val intent = Intent(baseContext, Liveview::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        })
    }

    private fun savePref() {
        getSharedPreferences("pwv", 0).edit()
            .putString("officerId", OfficerID.text.toString())
            .putString("dvrIp", DVRIPAddress.text.toString())
            .putInt("dvrPort", mdvrPort)
            .putInt("channel", 0)
            .putBoolean("AutoStart", autostart.isChecked)
            .apply()
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
        return if (item.itemId == R.id.action_settings)
            true
        else
            super.onOptionsItemSelected(item)

    }
}
