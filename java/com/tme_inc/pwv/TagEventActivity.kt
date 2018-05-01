package com.tme_inc.pwv

import android.app.Activity
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.preference.EditTextPreference
import android.preference.ListPreference
import android.preference.Preference
import android.preference.PreferenceFragment
import android.preference.PreferenceManager
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import kotlinx.android.synthetic.main.activity_tag.*

import java.util.Arrays
import java.util.Calendar


class TagEventActivity : Activity() {

    protected var mPwProtocol: PWProtocol? = null
    protected var VriItemSize = 0

    // DVR Date/Time in BCD
    protected var DvrDate: Long = 0
    protected var DvrTime: Long = 0

    protected var tagFrag: SettingsFragment? = null

    private val uiHandler = Handler()
    private val autoClose = Runnable { finish() }

    protected var m_vri: String = ""

    class SettingsFragment : PreferenceFragment() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)

            val prefMan = preferenceManager
            prefMan.sharedPreferencesName = "tag_event_prefs"
            val sPref = prefMan.sharedPreferences

            // Empty all prefs
            sPref.edit()
            .putString("tag_incident_classification", "")
            .putString("tag_case_number", "")
            .putString("tag_priority", "")
            .putString("tag_notes", "")
            .apply()

            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.pref_tag_event)

            // add Change Listener to set summary & init summary
            prefMan.findPreference("tag_incident_classification")!!.onPreferenceChangeListener =
                    prefChangeListener
            prefMan.findPreference("tag_case_number")!!.onPreferenceChangeListener =
                    prefChangeListener
            prefMan.findPreference("tag_priority")!!.onPreferenceChangeListener = prefChangeListener
            prefMan.findPreference("tag_notes")!!.onPreferenceChangeListener = prefChangeListener

        }

        fun setPref(key: String, value: Any) {
            val pref = findPreference(key)
            if (pref != null) {
                var v = value.toString()
                if (pref is ListPreference) {
                    pref.value = v
                    if (v.length == 0) {
                        v = "(Not Set)"
                    }
                } else if (pref is EditTextPreference) {
                    pref.text = v
                    if (v.length == 0) {
                        v = "(Please Enter)"
                    }
                } else {
                    pref.editor
                        .putString(key, v)
                        .apply()
                }
                pref.summary = v
            }
        }

        fun getPref(key: String): String? {
            val pref = findPreference(key)
            if (pref != null) {
                if (pref is ListPreference) {
                    return pref.value
                } else if (pref is EditTextPreference) {
                    return pref.text
                } else {
                    val prefMan = preferenceManager
                    val sPref = prefMan.sharedPreferences
                    return sPref.getString(key, "")
                }
            }
            return ""
        }

        companion object {

            // bind summary to value
            private val prefChangeListener =
                Preference.OnPreferenceChangeListener { preference, value ->
                    preference.summary = value.toString()
                    true
                }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val timestamp = intent.getLongExtra("DvrTime", 0L)

        val calendar = Calendar.getInstance()
        if (timestamp > 0L) {
            calendar.clear()
            calendar.timeInMillis = timestamp
        }
        DvrDate = (calendar.get(Calendar.YEAR) * 10000 +
                (calendar.get(Calendar.MONTH) - Calendar.JANUARY + 1) * 100 +
                calendar.get(Calendar.DAY_OF_MONTH)).toLong()
        DvrTime = (calendar.get(Calendar.HOUR_OF_DAY) * 10000 +
                calendar.get(Calendar.MINUTE) * 100 +
                calendar.get(Calendar.SECOND)).toLong()

        setContentView(R.layout.activity_tag)

        // Display the fragment as the main content.
        tagFrag = SettingsFragment()
        fragmentManager.beginTransaction()
            .replace(R.id.tagfragment, tagFrag)
            .commit()


        // init PW event processor
        //
        mPwProtocol = PWProtocol()
        mPwProtocol!!.getVri { result -> onTagPWEvent(result) }


        var button = findViewById<View>(R.id.button_tag) as Button
        button.setOnClickListener { onTagEventClick() }

        button = findViewById<View>(R.id.button_cancel) as Button
        button.setOnClickListener { finish() }

        // keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // add auto close message
        uiHandler.postDelayed(autoClose, 300000)

    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        // extent to another 5 min
        uiHandler.removeCallbacks(autoClose)
        uiHandler.postDelayed(autoClose, 300000)
    }

    protected fun onTagPWEvent(result: Bundle?) {
        if (result != null) {
            VriItemSize = result.getInt("VriItemSize", 0)
            val VriSize = result.getInt("VriListSize", 0)
            val VriList = result.getByteArray("VriList")
            if (VriSize > 0 && VriItemSize > 0 && VriList != null) {

                // vri extension
                fun ByteArray.getVriItem(offset: Int, len: Int ) : String {
                    return CString(
                        this,
                        offset,
                        len
                    ).trim()
                }

                var offset = 0

                val searchdatetime = (DvrDate - 20000000) * 10000 + DvrTime / 100
                //search for current index
                var vr_p: Long = 0
                var vr_pi = 0
                var vr_n: Long = 0
                var vr_ni = 0
                var o: Int

                // Search matching vri
                o = 0
                while (o < VriSize * VriItemSize) {
                    var vridatetime: Long = 0

                    // Split vri date/time
                    val vriarray = VriList.getVriItem(o,64).split("-")
                    if (vriarray.size > 2) {
                        try {
                            vridatetime = java.lang.Long.parseLong(vriarray[vriarray.size - 2])
                        } catch (e: NumberFormatException) {
                            vridatetime = 0
                        } catch (e: Exception) {
                            vridatetime = 0
                        }

                    }

                    if (vridatetime <= searchdatetime) {
                        if (vr_p == 0L) {
                            vr_p = vridatetime
                            vr_pi = o
                        } else if (vridatetime > vr_p) {
                            vr_p = vridatetime
                            vr_pi = o
                        }
                    } else {
                        if (vr_n == 0L) {
                            vr_n = vridatetime
                            vr_ni = o
                        } else if (vridatetime < vr_n) {
                            vr_n = vridatetime
                            vr_ni = o
                        }
                    }
                    o += VriItemSize
                }

                if (vr_p != 0L) {
                    o = vr_pi
                } else if (vr_n != 0L) {
                    o = vr_ni
                } else {
                    o = 0
                }

                offset = o

                var OfficerId = ""
                m_vri = VriList.getVriItem(offset,64)
                offset += 64

                // set title
                title_vri.text = "VRI : $m_vri"

                // splite officer ID from vri
                val strarray = m_vri.split("-")
                if (strarray.size > 2) {
                    OfficerId = strarray[strarray.size - 1]
                }

                // incident classification
                tagFrag?.setPref("tag_incident_classification", VriList.getVriItem(offset,32))
                offset += 32

                // case number
                tagFrag?.setPref("tag_case_number",  VriList.getVriItem(offset,64))
                offset += 64

                // Priority
                tagFrag?.setPref("tag_priority", VriList.getVriItem(offset,20))
                offset += 20

                // Officer ID
                //val eOfficerId = findViewById<View>(R.id.tag_officerid) as EditText
                //eOfficerId?.setText(OfficerId)
                offset += 32    // skip officer ID

                // Notes
                tagFrag?.setPref("tag_notes", VriList.getVriItem(offset,255))

            }

        }
    }

    protected fun onTagEventClick() {
        if (VriItemSize > 0) {
            val vri = ByteArray(VriItemSize){
                ' '.toByte()
            }

            fun ByteArray.setVriItem(offset: Int, len: Int, sData: String?) : Int {
                if( sData!=null ) {
                    val bData = sData!!.toByteArray()
                    val blen = kotlin.math.min(bData.size, len)
                    System.arraycopy(bData, 0, this, offset, blen)
                }
                return len
            }

            var offset = 0

            // Vri
            offset += vri.setVriItem(offset, 64, m_vri)

            // incident classification
            offset += vri.setVriItem(offset, 32, tagFrag!!.getPref("tag_incident_classification"))

            // case number, size=64
            offset += vri.setVriItem(offset, 64, tagFrag!!.getPref("tag_case_number"))

            // Priority
            offset += vri.setVriItem(offset, 20, tagFrag!!.getPref("tag_priority"))

            // Officer ID
            offset += 32    // skip officer ID

            // Notes
            offset += vri.setVriItem(offset, VriItemSize - offset, tagFrag!!.getPref("tag_notes"))

            // set VRI
            if (mPwProtocol != null) {
                mPwProtocol!!.setVri(vri)
            }

        }
        finish()
    }

}
