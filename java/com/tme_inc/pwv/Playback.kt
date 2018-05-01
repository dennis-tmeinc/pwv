package com.tme_inc.pwv

// Kotlin imports
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.view.*
import android.widget.ImageButton
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_playback.*
import java.util.*


class Playback : PwViewActivity() {

    private var m_playSpeed =  1000        // 1000 : normal speed, 0: paused, 1: one frame forward than paused
    private var m_loading = true

    private lateinit var timeBar: TimeBar

    internal var refTime: Long = 0          // ref time
    internal var refFrameTime: Long = 0     // ref frame time stamp
    internal var idleTime: Long = 0         // idleing time (no av output)

    private var uiHandler = Handler { msg: Message ->
        if( mstream!= null )
        when (msg.what) {
            MSG_PW_CONNECT -> {
                // PW stream connected
                timeBar.setDateList(mstream!!.dayList)
                playerSeek(timeBar.pos)
            }

            MSG_PW_SEEK -> {
                // PW Stream seek complete

                // to flush player if play is running
                mplayer?.flush()

                refFrameTime = 0       // reset frame reference time
                if (m_playSpeed == 0)
                    m_playSpeed = 1    // try display one frame
            }

            MSG_PW_GETCLIPLIST -> {
                if (msg.arg2 == 1) {   // clip info
                    timeBar.setClipInfo(msg.arg1, msg.obj as IntArray)
                } else if (msg.arg2 == 2) {   //lock info
                    timeBar.setLockInfo(msg.arg1, msg.obj as IntArray)
                }
            }

            MSG_TB_GETCLIPLIST -> {
                (mstream as PWPlaybackStream).getClipList(msg.arg1)
            }

            MSG_TB_SCROLL -> {
                if (timeBar.isSeekPending) {
                    playerSeek(timeBar.seekPos)
                }
            }

            MSG_VRI_SELECTED -> {
                val vri = msg.obj as String

                // split date/time from vri
                val strArray = vri.split("-")
                if (strArray.size > 1 && mstream != null) {
                    val datetime = strArray[strArray.size-1]
                    val dt = java.lang.Long.parseLong(datetime)
                    val date = (dt / 10000L).toInt()
                    val time = (dt % 10000L).toInt()
                    val calendar = Calendar.getInstance()
                    calendar.clear()
                    calendar.set(Calendar.YEAR, 2000 + date / 10000)
                    calendar.set(Calendar.MONTH, date % 10000 / 100 - 1 + Calendar.JANUARY)
                    calendar.set(Calendar.DATE, date % 100)

                    calendar.set(Calendar.HOUR_OF_DAY, time / 100)
                    calendar.set(Calendar.MINUTE, time % 100)
                    playerSeek(calendar.timeInMillis)
                }
            }


            MSG_DATE_SELECTED -> {
                val date = msg.arg1
                val calendar = Calendar.getInstance()
                calendar.clear()
                calendar.set(Calendar.YEAR, date / 10000)
                calendar.set(Calendar.MONTH, date % 10000 / 100 - 1 + Calendar.JANUARY)
                calendar.set(Calendar.DATE, date % 100)

                playerSeek(calendar.timeInMillis)
            }
        }
        true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_playback)

        // setup player screen
        setupScreen()

        timeBar = findViewById<View>(R.id.timebar) as TimeBar
        timeBar.uiHandler = uiHandler

        // restore timebar info
        timeBar.pos = PwViewActivity.tb_pos
        timeBar.viewportWidth = PwViewActivity.tb_width

        button_tag.setOnClickListener {
            //TagEventDialog tagDialog = new TagEventDialog();
            //tagDialog.setDvrTime(timeBar.getPos());
            //tagDialog.show(getFragmentManager(), "tagTagEvent");
            savePref()
            val intent = Intent(baseContext, TagEventActivity::class.java)
            intent.putExtra("DvrTime", timeBar.pos)
            startActivity(intent)
        }

        button_search.setOnClickListener {
            //VriDialog vriDialog = new VriDialog();
            //vriDialog.setUIHandler(m_handler);
            //vriDialog.show(getFragmentManager(), "tagVriEvent");
            if( mstream != null ) {
                val dSearch = VideoDatesDialog()
                dSearch.dateList = mstream!!.dayList
                dSearch.uiHandler = uiHandler
                dSearch.show(fragmentManager, "tagVideoDates")
            }
        }

        // Play button
        button_play.setOnClickListener {
            if (m_playSpeed > 1) {
                m_playSpeed = 1
                button_play.setImageResource(R.drawable.play_play)
                showHint("Paused")
            }
            else {
                m_playSpeed = 1000         // normal play speed
                button_play.setImageResource(R.drawable.play_pause)
                showHint("Play")
            }
        }

        // Slow button
        button_slow.setOnClickListener {
            if (m_playSpeed > 125) {
                m_playSpeed /= 2
            }
            showHint("Play speed: X" + m_playSpeed.toFloat() / 1000)
        }

        // fast button
        button_fast.setOnClickListener {
            if (m_playSpeed < 100) {
                m_playSpeed = 1000
            }
            if (m_playSpeed < 8000) {
                m_playSpeed *= 2
            }
            showHint("Play speed: X" + m_playSpeed.toFloat() / 1000)
        }

        // backward button
        button_backward.setOnClickListener { playerSeek(timeBar.pos - 20000) }

        // forward button
        button_forward.setOnClickListener { playerSeek(timeBar.pos + 20000) }

        // forward step
        button_step.setOnClickListener {
            m_playSpeed = 1
            button_play.setImageResource(R.drawable.play_pause)
        }

        btPlayMode.setOnClickListener {
            val intent = Intent(baseContext, Liveview::class.java)
            startActivity(intent)
            finish()
        }
    }

    // player seek to calendar time
    protected fun playerSeek(seekTime: Long) {
        (mstream as PWPlaybackStream)?.seek(seekTime)
    }

    override fun showUI() {
        super.showUI()
        // display action bar and menu
        if( isScreenLandscape ) {
            timeBar.visibility = View.VISIBLE
        }
    }

    override fun hideUI() {
        super.hideUI()

        if (isScreenLandscape) {
            timeBar.visibility = View.INVISIBLE
        }
    }

    protected fun showHint(text: String) {
        val hint = Toast.makeText(this, text, Toast.LENGTH_SHORT)
            hint.setGravity(Gravity.CENTER, 0, 0)
            hint.show()
    }

    override fun onPause() {
        super.onPause()

        savePref()
    }

    override fun onAnimate(totalTime: Long, deltaTime: Long) {
        super.onAnimate(totalTime, deltaTime)

        if (mstream == null) {
            mstream = PWPlaybackStream(m_channel, uiHandler)
            mstream?.start()

            m_loading = true
            loadingBar.visibility = View.VISIBLE
            loadingText.text = "Loading..."
            loadingText.visibility = View.VISIBLE
            return

        } else if (mplayer == null) {
            loadingText.text = mstream!!.channelName
            if (mstream!!.videoAvailable()) {
                //startActivity(new Intent(getBaseContext(), Playback.class));
                if (playScreen.isAvailable) {
                    val surface = playScreen.surfaceTexture
                    if (surface != null && mstream!!.resolution >= 0) {
                        //mplayer = PWPlayer.CreatePlayer(this, new Surface(surface), mstream.mRes );
                        mplayer = PWPlayer(this, Surface(surface), false)
                        mplayer!!.setFormat(mstream!!.resolution)
                        if (mstream!!.video_width > 50 && mstream!!.video_height > 50) {
                            mplayer!!.setFormat(mstream!!.video_width, mstream!!.video_height)
                        }
                        mplayer!!.setAudioFormat(mstream!!.audio_codec, mstream!!.audio_samplerate)
                        mplayer!!.start()
                        m_totalChannel = mstream!!.totalChannels

                        m_playSpeed =
                                1000    // 1000 : normal speed, 0: pause, 1: one frame forward than pause
                        refTime = 0
                        refFrameTime = 0
                        idleTime = 0
                        button_play.setImageResource(R.drawable.play_pause)
                    }
                }
            } else if (m_loading) {
                if ((mstream as PWPlaybackStream).isEndOfStream) {
                    m_loading = false
                    loadingBar.visibility = View.INVISIBLE
                    loadingText.visibility = View.INVISIBLE
                }
            }

            return
        }

        var frame: MediaFrame?
        var showOSD = false

        if (refFrameTime == 0L) {
            // first video frame
            frame = mstream!!.peekVideoFrame()
            if (frame != null) {
                // reset reference timers
                refFrameTime = frame.timestamp
                refTime = totalTime
                mplayer!!.resetAudioTimestamp()
                showOSD = true
            } else {
                return
            }
        }

        // expected playing frame time
        var playFrameTime = (totalTime - refTime) * m_playSpeed / 1000 + refFrameTime

        if (m_playSpeed == 1000) {
            // sync with audio frame time
            val audioTimestamp = mplayer!!.audioTimestamp
            if (audioTimestamp > 1000) {
                playFrameTime = audioTimestamp
            }
        }

        // fill audio decoder buffer

        if (mplayer!!.audioReady()) {
            frame = mstream!!.peekAudioFrame()
            if (frame != null) {
                if (m_playSpeed == 1000) {
                    if (frame!!.timestamp - playFrameTime < 2000) {
                        mplayer!!.audioInput(mstream!!.audioFrame)
                    }
                } else if (frame!!.timestamp < playFrameTime) {
                    mstream!!.audioFrame
                    mplayer!!.resetAudioTimestamp()
                }
            }
        }

        // fill vidoe decoder buffer
        if (mplayer!!.videoInputReady() && mstream!!.videoAvailable()) {
            val vframe = mstream!!.videoFrame
            // fast play, only output key frames
            if (vframe != null && (m_playSpeed <= 4000 || vframe.flags != 0)) {
                mplayer!!.videoInput(vframe)
            }
        }

        // Render output buffer if available
        if (mplayer!!.isVideoOutputReady) {

            val frametime = mplayer!!.videoTimestamp
            var rendered = false

            if (m_playSpeed <= 0) {              // paused
                refTime = totalTime
                idleTime = 0                  // fake no idle
                timeBar?.pos = frametime
            } else if (m_playSpeed == 1) {
                rendered = mplayer!!.popOutput(true)        // pop one frame
                timeBar?.pos = frametime
                button_play.setImageResource(R.drawable.play_play)
                m_playSpeed = 0               // paused
                refFrameTime = frametime
                refTime = totalTime
                idleTime = 0                  // fake no idle
            } else {
                val diff = frametime - playFrameTime
                if (diff <= 0) {
                    rendered = mplayer!!.popOutput(true)
                    timeBar?.pos = frametime
                    idleTime = 0
                    if (diff < 100) {
                        refFrameTime = frametime
                        refTime = totalTime
                    }
                }
            }

            // first video frame available?
            if (m_loading && rendered) {
                loadingBar.visibility = View.INVISIBLE
                loadingText.visibility = View.INVISIBLE
                m_loading = false
            }
        }

        // Output text frame
        frame = null
        var xframe = mstream!!.peekTextFrame()
        while (xframe != null && xframe.timestamp <= playFrameTime) {
            frame = mstream!!.textFrame
            xframe = mstream!!.peekTextFrame()
        }

        if (frame == null && showOSD)
            displayOSD(xframe)
        else
            displayOSD(frame)

        idleTime += deltaTime
        if (idleTime > 3000) {
            // idling more then 3 seconds, restart sync
            refFrameTime = 0
            idleTime = 0
        }

        return
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_playback, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        val id = item.itemId


        if (id == R.id.action_settings) {
            // Intent intent = new Intent(getBaseContext(), Launcher.class);
            val intent = Intent(baseContext, SettingsActivity::class.java)
            startActivity(intent)
            return true
        } else if (id == R.id.action_liveview) {
            val intent = Intent(baseContext, Liveview::class.java)
            startActivity(intent)
            finish()
            return true
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        savePref()
    }

    private fun savePref() {
        // save current channel
        PwViewActivity.channel = m_channel
        PwViewActivity.tb_pos = timeBar.pos
        PwViewActivity.tb_width = timeBar.viewportWidth

    }

}
