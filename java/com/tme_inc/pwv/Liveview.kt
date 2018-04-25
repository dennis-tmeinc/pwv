package com.tme_inc.pwv

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.view.Menu
import android.view.MenuItem
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import kotlinx.android.synthetic.main.activity_liveview.*
import java.util.Scanner

class Liveview : PwViewActivity() {
    private var m_covertmode = false
    private var m_loading: Boolean = false

    private val m_RecId = intArrayOf(
        R.id.rec1,
        R.id.rec2,
        R.id.rec3,
        R.id.rec4,
        R.id.rec5,
        R.id.rec6,
        R.id.rec7,
        R.id.rec8
    )
    private val m_RecImage = intArrayOf(
        R.drawable.rec1,
        R.drawable.rec2,
        R.drawable.rec3,
        R.drawable.rec4,
        R.drawable.rec5,
        R.drawable.rec6,
        R.drawable.rec7,
        R.drawable.rec8
    )
    private val m_NoRecImage = intArrayOf(
        R.drawable.norec1,
        R.drawable.norec2,
        R.drawable.norec3,
        R.drawable.norec4,
        R.drawable.norec5,
        R.drawable.norec6,
        R.drawable.norec7,
        R.drawable.norec8
    )

    private var mStatusTime: Long = 0

    private var m_diskwaringflash = false   // to flash message

    internal var xappmod = 0

    private var pwStatusListener = fun( result: Bundle) {
        mStatusTime = 0
        val pwStatus = result.getByteArray("PWStatus")
        if (pwStatus != null) {
            var i1: Int
            var i2: Int
            i1 = -1
            i2 = -1

            // update Rec icons
            for( i in m_RecId.indices ) {
                val recIcon= findViewById<ImageView>(m_RecId[i])
                if (recIcon != null) {
                    if (i < pwStatus.size) {
                        val iStatus = pwStatus[i].toInt()
                        if (iStatus and 4 != 0) {
                            recIcon.setImageResource(m_RecImage[i])
                        } else {
                            recIcon.setImageResource(m_NoRecImage[i])
                        }
                        recIcon.visibility = View.VISIBLE

                        val forcechannel = iStatus shr 4 and 3
                        if (i1 < 0) {
                            if (forcechannel == 0)
                                i1 = i
                        }
                        if (i2 < 0) {
                            if (forcechannel == 1)
                                i2 = i
                        }
                    }
                    else {
                        recIcon.visibility = View.INVISIBLE
                    }
                }
            }

            // update PAN/BACK button image
            if (pwcontrol.visibility == View.VISIBLE) {     // button bar visible?
                var rec: Boolean
                var frec: Boolean

                // var button_cam = findViewById<View>(R.id.button_cam1) as ImageButton
                if (i1 >= 0) {
                    val iStatus = pwStatus[i1].toInt()
                    rec = iStatus and 4 != 0
                    frec = iStatus and 8 != 0
                    if (rec == frec) {
                        if (rec) {
                            button_cam1.setImageResource(R.drawable.pw_cam1_light)
                        } else {
                            button_cam1.setImageResource(R.drawable.pw_cam1)
                        }
                    } else {
                        button_cam1.setImageResource(R.drawable.pw_cam1_trans)
                    }
                } else {
                    button_cam1.setImageResource(R.drawable.pw_cam1)
                }

                if (i2 >= 0) {
                    val iStatus = pwStatus[i2].toInt()
                    rec = iStatus and 4 != 0
                    frec = iStatus and 8 != 0
                    if (rec == frec) {
                        if (rec) {
                            button_cam2.setImageResource(R.drawable.pw_cam2_light)
                        } else {
                            button_cam2.setImageResource(R.drawable.pw_cam2)
                        }
                    } else {
                        button_cam2.setImageResource(R.drawable.pw_cam2_trans)
                    }
                } else {
                    button_cam2.setImageResource(R.drawable.pw_cam2)
                }
            }
        }

        var msg = ""
        val DiskInfo = result.getString("DiskInfo", "")
        val di = DiskInfo.split("\n").dropLastWhile { it.isEmpty() }

        var flashing = false   // to flash message
        // disk info:   idx,mounted,totalspace,freespace,full,llen,nlen
        var d1avail = false
        var disk = -1
        var mounted = 0
        var totalspace: Int
        var freespace: Int
        var full: Int
        var llen = 0
        var nlen: Int
        var reserved: Int
        var msgcolor: Int
        var xfree = 0.0f
        var lspace = 0.0f

        var diskl1 = false
        var diskl2 = false
        var appmode = 0

        if (di.size > 3 && di[3].length > 4) {     // PWZ6 app mode
            val scanner = Scanner(di[3])
            scanner.useDelimiter(",")
            if (scanner.hasNextInt()) disk = scanner.nextInt()

            if (disk == 100) {
                if (scanner.hasNextInt()) appmode = scanner.nextInt()
            }
            // urn screen off (auto off)
            if (appmode != xappmod) {
                if (appmode <= 2) {
                    screenKeepOn(true)               // put screen to sleep if device pass shutdown delay
                } else {
                    screenKeepOn(false)               // put screen to sleep if device pass shutdown delay
                }
                xappmod = appmode
            }
        }

        m_diskwaringflash = !m_diskwaringflash

        // diskinfo:  disk,mounted,total,free,full,l_len,n_len,reserved

        /// DISPLAY DISK1
        if (di.isNotEmpty() && di[0].length > 10) {
            val scanner = Scanner(di[0])
            scanner.useDelimiter(",")

            disk = -1
            mounted = 0
            totalspace = 1
            freespace = 0
            full = 1
            llen = 0
            nlen = 0
            reserved = 1000
            flashing = true
            xfree = 0.0f
            lspace = 0.0f
            msgcolor = resources.getColor(R.color.diskmsg_red)

            if (scanner.hasNextInt()) disk = scanner.nextInt()
            if (scanner.hasNextInt()) mounted = scanner.nextInt()
            if (scanner.hasNextInt()) totalspace = scanner.nextInt()
            if (scanner.hasNextInt()) freespace = scanner.nextInt()
            if (scanner.hasNextInt()) full = scanner.nextInt()
            if (scanner.hasNextInt()) llen = scanner.nextInt()
            if (scanner.hasNextInt()) nlen = scanner.nextInt()
            if (scanner.hasNextInt()) reserved = scanner.nextInt()

            if (disk == 0 && mounted != 0) {
                if (full == 0) {
                    if (llen + nlen <= 0) {
                        llen = 0
                        nlen = 1
                    }
                    lspace = (totalspace.toFloat() - freespace.toFloat()) * llen / (llen + nlen)
                    if (lspace < 0.0) lspace = 0.0f
                    xfree = totalspace.toFloat() - lspace - reserved.toFloat()
                    if (xfree < 0.1) xfree = 0.1f
                    msg = String.format("Disk1 : %.1fG", xfree / 1000.0f)

                    val freerate = xfree / (xfree + lspace)
                    if (freerate < 0.1f) {
                        msgcolor = resources.getColor(R.color.diskmsg_red)
                    } else if (freerate < 0.3f) {
                        flashing = false
                        msgcolor = resources.getColor(R.color.diskmsg_amber)
                    } else {
                        flashing = false
                        msgcolor = resources.getColor(R.color.diskmsg_green)
                    }
                    d1avail = true
                } else {
                    msg = "Disk1 Full"
                }
            } else {
                msg = "Disk1 Not Available!"
                llen = 0
            }

            if (llen > 0) {
                diskl1 = true
            }

            disk1msg.text = if (flashing && m_diskwaringflash)
                ""
            else
                msg

            disk1msg.setTextColor(msgcolor)
        }

        /// DISPLAY DISK2
        if (di.size > 1 && di[1].length > 10) {
            val scanner = Scanner(di[1])
            scanner.useDelimiter(",")

            disk = -1
            mounted = 0
            totalspace = 1
            freespace = 0
            full = 1
            llen = 0
            nlen = 0
            reserved = 1000
            flashing = true
            xfree = 0.0f
            lspace = 0.0f
            msgcolor = resources.getColor(R.color.diskmsg_red)

            if (scanner.hasNextInt()) disk = scanner.nextInt()
            if (scanner.hasNextInt()) mounted = scanner.nextInt()
            if (scanner.hasNextInt()) totalspace = scanner.nextInt()
            if (scanner.hasNextInt()) freespace = scanner.nextInt()
            if (scanner.hasNextInt()) full = scanner.nextInt()
            if (scanner.hasNextInt()) llen = scanner.nextInt()
            if (scanner.hasNextInt()) nlen = scanner.nextInt()
            if (scanner.hasNextInt()) reserved = scanner.nextInt()

            if (disk == 1 && mounted != 0) {
                if (full == 0) {
                    if (llen + nlen <= 0) {
                        llen = 0
                        nlen = 1
                    }
                    lspace = (totalspace.toFloat() - freespace.toFloat()) * llen / (llen + nlen)
                    if (lspace < 0.0) lspace = 0.0f
                    xfree = totalspace.toFloat() - lspace - reserved.toFloat()
                    if (xfree < 0.1) xfree = 0.1f
                    msg = String.format("Disk2 : %.1fG", xfree / 1000.0f)

                    val freerate = xfree / (xfree + lspace)
                    if (freerate < 0.1f) {
                        msgcolor = resources.getColor(R.color.diskmsg_red)
                    } else if (freerate < 0.3f) {
                        flashing = false
                        msgcolor = resources.getColor(R.color.diskmsg_amber)
                    } else {
                        flashing = false
                        msgcolor = resources.getColor(R.color.diskmsg_green)
                    }
                } else {
                    msg = "Disk2 Full"
                }
            } else {
                msg = "Disk2 Not Available!"
                llen = 0
            }

            if (llen > 0) {
                diskl2 = true
            }

            if (d1avail) msg = ""

            disk2msg.text  = if (flashing && m_diskwaringflash)
                ""
            else
                msg

            disk2msg.setTextColor(msgcolor)
        }

        if (appmode >= 2 && (diskl1 || diskl2)) {
            // remove msg2
            disk2msg.text = ""
            msgcolor = resources.getColor(R.color.diskmsg_white)
            disk1msg.setTextColor(msgcolor)

            msg = "Video available on "
            if (diskl1) {
                msg += "DISK1"
            }
            if (diskl2) {
                if (diskl1) msg += " & "
                msg += "DISK2"
            }
            msg += " !"
            disk1msg.text = msg

        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_liveview)

        // setup player screen
        setupScreen()

        button_tag.setOnClickListener {
            //TagEventDialog tagDialog = new TagEventDialog();
            //tagDialog.show(getFragmentManager(), "tagTagEvent");
            savePref()
            val intent = Intent(baseContext, TagEventActivity::class.java)
            //Intent intent = new Intent(getBaseContext(), TagEvent.class);
            if (mplayer != null) {
                intent.putExtra("DvrTime", mplayer!!.videoTimestamp)
            }
            startActivity(intent)
        }

        button_covert.setOnClickListener {
            m_covertmode = true

            mPwProtocol.setCovertMode(true)

            // Show Covert Screen
            val intent = Intent(baseContext, CovertScreenActivity::class.java)
            startActivity(intent)
        }

        button_officer.setOnClickListener {
            val officerIdDialog = OfficerIdDialog()
            officerIdDialog.show(fragmentManager, "tagOfficerId")
        }

        button_cam1.setOnClickListener {
            mPwProtocol.sendPWKey(PWProtocol.PW_VK_C1_DOWN, {})
            (it as ImageButton).setImageResource(R.drawable.pw_cam1_trans)
            mStatusTime = 0
        }

        button_cam2.setOnClickListener {
            mPwProtocol.sendPWKey(PWProtocol.PW_VK_C2_DOWN, {})
            (it as ImageButton).setImageResource(R.drawable.pw_cam2_trans)
            mStatusTime = 0
        }

        button_tm.setOnClickListener {
            mPwProtocol.sendPWKey(PWProtocol.PW_VK_TM_DOWN, {})
            mStatusTime = 0
        }

        button_lp.setOnClickListener {
            val selected = (it as ImageButton).isSelected
            it.isSelected = !selected

            if (it.isSelected) {
                mPwProtocol.sendPWKey(PWProtocol.PW_VK_LP_DOWN, {})
                m_UIhandler?.sendEmptyMessageDelayed(MSG_PW_LPOFF, 5000)
            } else {
                m_UIhandler?.removeMessages(MSG_PW_LPOFF)
                mPwProtocol.sendPWKey(PWProtocol.PW_VK_LP_UP)
            }

            mStatusTime = 0
        }

        btPlayMode.setOnClickListener {
            val intent = Intent(baseContext, Playback::class.java)
            startActivity(intent)
            finish()
        }

        m_UIhandler = Handler({ msg:Message ->
            if (msg.what == MSG_UI_HIDE) {
                hideUI()
            } else if (msg.what == MSG_PW_LPOFF) {
                if ( button_lp.isSelected ) {
                    button_lp.isSelected = false
                    mPwProtocol.sendPWKey(PWProtocol.PW_VK_LP_UP)
                }
            }
            true
        })

    }

    override fun onResume() {
        super.onResume()

        // set Police ID
        val prefs = getSharedPreferences("pwv", 0)
        val useLogin = prefs.getBoolean("login", false)
        val officerId = prefs.getString("officerId", "")
        if (!useLogin && officerId.isNotBlank()) {
            mPwProtocol.setOfficerId(officerId)
        }

        // save live screen active flag
        prefs.edit()
            .putBoolean("live", true)           // stop using remote login on launch
            .apply()

        if (m_covertmode) {
            m_covertmode = false
            mPwProtocol.setCovertMode(false)
        }

    }

    override fun onPause() {
        super.onPause()

        savePref()
    }

    override fun onAnimate(totalTime: Long, deltaTime: Long) {
        super.onAnimate(totalTime, deltaTime)

        if (mstream == null) {
            mstream = PWLiveStream(m_channel)
            mstream!!.start()
            m_loading = true
            loadingBar.visibility = View.VISIBLE
            loadingText.text = "Loading..."
            loadingText.visibility = View.VISIBLE
            return
        }
        else if (mplayer == null) {

            loadingText.text = mstream!!.channelName

            if (mstream!!.videoAvailable()) {
                m_totalChannel = mstream!!.totalChannels
                //startActivity(new Intent(getBaseContext(), Playback.class));
                val textureView = liveScreen
                if (textureView != null && textureView.isAvailable) {
                    val surface = textureView.surfaceTexture
                    if (surface != null && mstream!!.resolution >= 0) {
                        //mplayer = PWPlayer.CreatePlayer(this, new Surface(surface), mstream.mRes );
                        mplayer = PWPlayer(this, Surface(surface), true)
                        if (mstream!!.video_width > 50 && mstream!!.video_height > 50) {
                            mplayer!!.setFormat(mstream!!.video_width, mstream!!.video_height)
                        } else {
                            mplayer!!.setFormat(mstream!!.resolution)
                        }
                        mplayer!!.setAudioFormat(mstream!!.audio_codec, mstream!!.audio_samplerate)
                        mplayer!!.start()
                    }
                }
            } else if (!mstream!!.isRunning) {
                goNextChannel()
            }

            return
        }

        while (mplayer!!.videoInputReady() && mstream!!.videoAvailable()) {
            mplayer!!.videoInput(mstream!!.videoFrame)
        }

        while (mstream!!.audioAvailable()) {
            mplayer!!.audioInput(mstream!!.audioFrame)
        }

        // Render output buffer if available
        if (mplayer!!.isVideoOutputReady) {
            val ats = mplayer!!.audioTimestamp
            val vts = mplayer!!.videoTimestamp
            if (vts <= ats || ats == 0L) {
                if (mplayer!!.popOutput(true)) {
                    if (m_loading) {
                        loadingBar.visibility = View.INVISIBLE
                        loadingText.visibility = View.INVISIBLE
                        m_loading = false
                    }
                }

                var txtFrame: MediaFrame? = null
                while (mstream!!.textAvailable()) {
                    txtFrame = mstream!!.textFrame
                    if (txtFrame!!.timestamp >= vts) {
                        break
                    }
                }
                if (txtFrame != null) {
                    displayOSD(txtFrame)
                }
            }
        }

        // PW status update
        mStatusTime += deltaTime
        if (mStatusTime > 1000 && !mPwProtocol.isBusy) {       // every second
            mStatusTime = 0
            mPwProtocol.getPWStatus(pwStatusListener)
        }
        return
    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_liveview, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        when( item.itemId ) {
            R.id.action_settings -> {
                //Intent intent = new Intent(getBaseContext(), Launcher.class);
                val intent = Intent(baseContext, SettingsActivity::class.java)
                startActivity(intent)
            }

            R.id.action_playback -> {
                val intent = Intent(baseContext, Playback::class.java)
                startActivity(intent)
                finish()
            }

            R.id.device_setup -> {
                mPwProtocol.getWebUrl { result ->
                    val url = result.getString("URL")
                    if (url != null) {
                        val intent = Intent(baseContext, PwWebView::class.java)
                        intent.putExtra("URL", url + "login.html")
                        intent.putExtra("TITLE", "Device Setup")
                        startActivity(intent)
                    }
                }
            }

            R.id.video_archive -> {
                val intent = Intent(baseContext, ArchiveActivity::class.java)
                startActivity(intent)
            }

            else -> {
                return super.onOptionsItemSelected(item)
            }
        }
        return true
    }

    private fun savePref() {
        // save current channel
        PwViewActivity.channel = m_channel
    }

}
