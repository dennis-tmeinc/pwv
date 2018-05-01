package com.tme_inc.pwv

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_main.*

import org.json.JSONException
import org.json.JSONObject

import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import java.util.ArrayList

class MainActivity : Activity() {
    protected val googleDownload = "https://drive.google.com/uc?export=download&id="
    protected val PWVFileId = "0B3EjTUrzgBepalpMbG1GdGJvMlU"
    internal var updateTask: UpdateTask? = null
    internal var m_sessionId: String = ""
    internal val m_deviceList = ArrayList<Device>()

    private var m_connectMode: Int = 0
    private var m_dvrIp: String = ""
    private var m_autoStart = true
    private var m_pwProtocol = PWProtocol()

    private var m_run = false

    class Device( val name: String, val id : String ) {
        var update = true

        override fun toString(): String {
            return name
        }

        override fun hashCode(): Int {
            return "$name.$id".hashCode()
        }

        override fun equals(other: Any?): Boolean {
            return if( other is Device )
                id.equals(other.id) && name.equals(other.name)
            else
                false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )

        m_dvrIp = "192.168.1.100"
        m_connectMode = DvrClient.CONN_USB

        devicelist.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            m_deviceList
        )

        devicelist.setOnItemClickListener { parent, view, position, id ->
            onSelectDevice(position)
        }

        button_usb.setOnClickListener {
            m_connectMode = DvrClient.CONN_USB
            updateDeviceList(true)
        }

        button_local.setOnClickListener {
            m_connectMode = DvrClient.CONN_DIRECT
            updateDeviceList(true)
        }

        button_remote.setOnClickListener {
            if (m_connectMode == DvrClient.CONN_REMOTE) {
                val intent = Intent(baseContext, LoginActivity::class.java)
                startActivity(intent)
            } else {
                m_connectMode = DvrClient.CONN_REMOTE
                updateDeviceList(true)
            }
        }

        startService(Intent(this, PwvService::class.java))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        updateDeviceList(true)
    }

    internal fun showMessage(msg: String?) {
        val toast = Toast.makeText(this, msg, Toast.LENGTH_LONG)
        toast.setGravity(Gravity.CENTER, 0, 0)
        toast.show()
    }

    private fun checkForUpdates(manual: Boolean) {
        // start auto update task
        if (updateTask == null) {
            updateTask = UpdateTask()
            updateTask!!.manual = manual
            updateTask!!.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
        }
    }

    override fun onResume() {
        super.onResume()

        m_run = true

        window.addFlags(
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )

        val prefs = getSharedPreferences("pwv", 0)
        m_connectMode = prefs.getInt("connMode", DvrClient.CONN_DIRECT)
        m_dvrIp = prefs.getString("dvrIp", "192.168.1.100")
        m_autoStart = prefs.getBoolean("AutoStart", true)

        updateDeviceList(true)

        val autoCheckUpdate = prefs.getBoolean("autoCheckUpdate", true)
        val autoCheckInterval = Integer.parseInt(prefs.getString("autoCheckInterval", "24"))
        val updchecktime = prefs.getInt("UpdateCheckTime", 0)
        val currentHour = (System.currentTimeMillis() / 3600000).toInt()

        if (autoCheckUpdate && currentHour - updchecktime > autoCheckInterval) {
            checkForUpdates(false)
        }
    }

    override fun onPause() {
        super.onPause()

        this.getSharedPreferences("pwv", 0)
            .edit()
            .putInt("connMode", m_connectMode)
            .apply()

        m_pwProtocol.cancel()
        m_run = false
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.mainmenu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        when ( item.itemId ) {

            R.id.action_settings -> {
                // Intent intent = new Intent(getBaseContext(), Launcher.class);
                val intent = Intent(baseContext, SettingsActivity::class.java)
                startActivity(intent)
                return true
            }

            R.id.action_checkupdate -> {
                checkForUpdates(true)
            }

            R.id.action_about -> {
                val aboutDialog = AboutDialogFragment()
                aboutDialog.show(fragmentManager, "tagAbout")
            }

            else -> {
                return super.onOptionsItemSelected(item)
            }
        }
        return true
    }

    protected fun alertLoginError() {
        AlertDialog.Builder(this)
            .setMessage("Login error!")
            .setTitle("Error!")
            .show()
    }

    protected fun alertNoDevice() {
        AlertDialog.Builder(this)
            .setMessage("No device detected!")
            .setTitle("Error!")
            .show()
    }

    protected fun onSelectDevice(which: Int) {
        if( which>=0 && which < m_deviceList.size ) {
            // The 'which' argument contains the index position
            val pref = getSharedPreferences("pwv", 0)
            val prefEdit = pref.edit()

            if (m_connectMode == DvrClient.CONN_DIRECT) {
                prefEdit.putString("deviceIp", m_deviceList[which].id)
            } else {
                prefEdit
                    .putString("loginTargetId", m_deviceList[which].id)
                    .putString("loginSession", m_sessionId)
            }
                .putInt("connMode", m_connectMode)
                .apply()

            // Start Live View
            startActivity(Intent(baseContext, Liveview::class.java))

            m_deviceList.clear()
        }
    }

    protected fun updateNameList() {
        if( !m_run )
            return

        var detectString = "Unknown?"
        if (m_connectMode == DvrClient.CONN_USB) {
            detectString = "USB"
        }
        else if (m_connectMode == DvrClient.CONN_REMOTE) {
            detectString = "Internet"
        }
        else if( m_connectMode == DvrClient.CONN_DIRECT) {
            detectString = "local network"
        }

        val ndevs = m_deviceList.size
        if (ndevs == 0) {
            deviceheader.text = "No device detected from $detectString!"
        } else if (ndevs == 1) {
            deviceheader.text = "1 device detected from $detectString."
        } else {
            deviceheader.text = "Total $ndevs devices detected from $detectString."
        }
        devicedetect.visibility = View.INVISIBLE
        devicelist.invalidate()

        if( m_autoStart && m_connectMode == DvrClient.CONN_USB && ndevs > 0 ) {
            devicelist.postDelayed({onSelectDevice(0)},2000)
        }
    }

    private fun setButtonColor() {
        var color1: Int
        var color2: Int
        var color3: Int
        color3 = R.color.button_main
        color2 = color3
        color1 = color2
        if (m_connectMode == DvrClient.CONN_USB) {
            color1 = R.color.button_selected
        }
        else if (m_connectMode == DvrClient.CONN_REMOTE) {
            color2 = R.color.button_selected
        }
        else {
            color3 = R.color.button_selected
        }

        button_usb.setBackgroundResource(color1)
        button_remote.setBackgroundResource(color2)
        button_local.setBackgroundResource(color3)
    }

    private fun updateLocalDevice() {
        m_pwProtocol.getDeviceList( { result ->
            if (result.getBoolean("Complete", true)) {
                // complete
                devicelist.postDelayed({ updateDeviceList() }, 15000)

                for( i in m_deviceList.size-1 downTo 0){
                    if( !m_deviceList[i].update )
                        m_deviceList.removeAt(i)
                }
            }
            else {
                val ip = result.getString("deviceIP")
                val name = result.getString("deviceName")

                val de = Device( name, ip )
                m_deviceList.remove(de)
                m_deviceList.add(de)
            }

            updateNameList()
        }, m_dvrIp)
    }

    private fun updateRemoteDevice() {
        val prefs = getSharedPreferences("pwv", 0)

        var user: String?
        if (m_connectMode == DvrClient.CONN_USB) {
            user = "usb"
        } else {
            user = prefs.getString("loginUser", "nouser")
        }
        if (user!!.isBlank()) {
            user = "EMPTY"
        }
        var pass = prefs.getString("loginKey", "nokey")
        if (pass!!.isBlank()) {
            pass = "EMPTY"
        }
        var accessKey = prefs.getString("accessKey", "")
        if (accessKey!!.isBlank()) {
            accessKey = "EMPTY"
        }

        // m_pwProtocol.connectMode = m_connectMode

        m_pwProtocol.remoteLogin(
            { result ->
                m_deviceList.clear()

                val numDevice = if (result.getString("sessionId", "0").length < 2) {
                    0
                } else {
                    result.getInt("numberOfDevices", 0)
                }

                if (numDevice > 0) {
                    m_sessionId = result.getString("sessionId")
                    for (i in 0 until numDevice) {
                        m_deviceList.add(
                            Device(
                                result.getString("name$i"),
                                result.getString("id$i")
                            )
                        )
                    }
                }

                updateNameList()
                devicelist.postDelayed({ updateDeviceList() }, 15000)
            },
            user,
            pass,
            accessKey,
            m_connectMode.toString()
        )
    }

    private fun updateDeviceList(refresh: Boolean = false ) {

        if( !m_run )
            return

        if (refresh) {
            m_deviceList.clear()
            deviceheader.text = getText(R.string.text_detecting_device)
            devicedetect.visibility = View.VISIBLE
            setButtonColor()
            m_pwProtocol.cancel()
        }
        else if( m_deviceList.isNotEmpty() ) {
            for( e in m_deviceList ) {
                e.update = false
            }
        }

        if (m_pwProtocol.isBusy) {
            // do this again later
            devicelist.postDelayed({ updateDeviceList(false) }, 5000)
            return
        }

        if (m_connectMode == DvrClient.CONN_DIRECT) {
            updateLocalDevice()
        } else {
            updateRemoteDevice()
        }
    }

    internal inner class UpdateTask : AsyncTask<Unit, Unit, Unit>() {

        var manual = false
        private var newversion: JSONObject? = null
        private var error: String? = null

        override fun doInBackground(vararg params: Unit?) {
            try {
                HttpURLConnection.setFollowRedirects(true)
                val gdConnection =
                    URL(googleDownload + PWVFileId).openConnection() as HttpURLConnection
                gdConnection.connectTimeout = 10000           //set timeout to 10 seconds
                if (gdConnection.responseCode == HttpURLConnection.HTTP_OK) {
                    val content = ByteArray(8000)
                    val r = gdConnection.inputStream.read(content)
                    if (r > 5) {
                        newversion = JSONObject(String(content, 0, r))
                    }
                }
                gdConnection.disconnect()
            } catch (e: MalformedURLException) {
                e.printStackTrace()
            } catch (e: IOException) {
                e.printStackTrace()
                error = "Please Connect To Internet!"
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }

        override fun onPostExecute(result: Unit?) {
            super.onPostExecute(result)
            if (newversion != null) {
                try {
                    val latestVersion = newversion!!.getInt("VersionCode")
                    if (latestVersion > BuildConfig.VERSION_CODE) {

                        val pwvId = newversion!!.getString("Id")
                        val pwvFile = newversion!!.getString("File")
                        val pwvVersion = newversion!!.getString("Version")

                        val alertDialog = AlertDialog.Builder(this@MainActivity).create()
                        alertDialog.setTitle("Info")
                        alertDialog.setMessage("A new version of PW Controller is available, click OK to update.")
                        alertDialog.setButton(
                            AlertDialog.BUTTON_NEUTRAL, "OK"
                        ) { dialog, which ->
                            dialog.dismiss()

                            // we have a new version apk availab !
                            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                            val downloadUri = Uri.parse(googleDownload + pwvId)
                            val request = DownloadManager.Request(downloadUri)
                            request.allowScanningByMediaScanner()
                            request.setTitle("PW Controller : version $pwvVersion")
                                .setDescription(pwvFile)
                                .setVisibleInDownloadsUi(true)
                                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                .setDestinationInExternalPublicDir(
                                    Environment.DIRECTORY_DOWNLOADS,
                                    pwvFile
                                )
                            try {
                                dm.enqueue(request)
                            } catch (e: Exception) {
                                e.printStackTrace()
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
                        alertDialog.show()
                    } else {
                        if (manual) {
                            showMessage("You have latest software installed!")
                        }
                    }
                } catch (e: JSONException) {
                }

            } else {
                if (manual && error != null) {
                    showMessage(error)
                }
            }

            // update last check time
            val currentHour = (System.currentTimeMillis() / 3600000).toInt()

            this@MainActivity.getSharedPreferences("pwv", 0).edit()
                .putInt("UpdateCheckTime", currentHour)
                .apply()

            updateTask = null
        }
    }

}
