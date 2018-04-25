package com.tme_inc.pwv

import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import kotlinx.android.synthetic.main.activity_login.*

class LoginActivity : Activity() {

    protected var m_sessionId: String = ""
    protected var m_numDevice: Int = 0
    protected var m_DeviceName =  ArrayList<String>()
    protected var m_DeviceId = ArrayList<String>()

    protected fun onSelectDevice(which: Int) {
        // The 'which' argument contains the index position
        this.getSharedPreferences("pwv", 0)
            .edit()
            .putInt("connMode", DvrClient.CONN_REMOTE)     // to use remote login
            .putString("loginSession", m_sessionId)
            .putString("loginTargetId", m_DeviceId[which])
            .apply()

        // Start Live View
        val intent = Intent(baseContext, Liveview::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    protected fun selectDevice() {
        AlertDialog.Builder(this)
            .setTitle("Select Device")
            .setItems(m_DeviceName.toArray(Array<String>(0, {""}))) { dialog, which -> onSelectDevice(which) }
            .show()
    }

    protected fun alertLoginError() {
        val builder = AlertDialog.Builder(this)
        builder.setMessage("Login error!")
            .setTitle("Error!")
            .show()
    }

    protected fun alertNoDevice() {
        val builder = AlertDialog.Builder(this)
        builder.setMessage("No device detected!")
            .setTitle("Error!")
            .show()
    }

    protected fun onSignIn() {
        val protocol = PWProtocol()
        protocol.remoteLogin( {
                result : Bundle? ->
                if (result == null || result.getString("sessionId", "0").length < 2) {
                    alertLoginError()
                    return@remoteLogin
                }

                m_numDevice = result.getInt("numberOfDevices", 0)
                if (m_numDevice < 1) {
                    alertNoDevice()
                    return@remoteLogin
                }

                m_sessionId = result.getString("sessionId")
                for (i in 0 until m_numDevice) {
                    m_DeviceId.add(result.getString("id$i"))
                    m_DeviceName.add(result.getString("name$i"))
                }
                selectDevice()
            },
            edit_username.text.toString(),
            edit_password.text.toString(),
            ""
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_login)

        val pref = this.getSharedPreferences("pwv", 0)

        edit_username.setText( pref.getString("loginUser", "") )
        var pass: String? = ""
        val remember = pref.getBoolean("loginRememberKey", false)
        if (remember)
            pass = pref.getString("loginKey", "")
        edit_password.setText(pass)
        check_rememberPassword.isChecked = remember
        edit_accesskey.setText( pref.getString("accessKey", ""))

        button_connect.setOnClickListener {
            pref.edit()
                .putString("loginUser", edit_username.text.toString())
                .putString("loginKey", edit_password.text.toString())
                .putBoolean("loginRememberKey", check_rememberPassword.isChecked)
                .putString("accessKey", edit_accesskey.text.toString())
                .apply()

            finish()
        }
    }

}
