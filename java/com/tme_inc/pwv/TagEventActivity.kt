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
            val ed = sPref.edit()
            ed.putString("tag_incident_classification", "")
            ed.putString("tag_case_number", "")
            ed.putString("tag_priority", "")
            ed.putString("tag_notes", "")
            ed.commit()

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
                    val editor = pref.editor
                    editor.putString(key, v)
                    editor.commit()
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
        mPwProtocol!!.GetVri { result -> onTagPWEvent(result) }


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

                    //VRI
                    m_vri = String( VriList, o,64).split("\u0000")[0].trim()

                    // Split vri date/time
                    val vriarray = m_vri.split("-")
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
                m_vri = String(
                    VriList,
                    offset,
                    64
                ).split("\u0000")[0].trim()

                // set title
                val vriTitle = findViewById<View>(R.id.title_vri) as TextView
                if (vriTitle != null) {
                    vriTitle.text = "VRI : $m_vri"
                }

                // splite officer ID from vri
                val strarray = m_vri.split("-")
                if (strarray.size > 2) {
                    OfficerId = strarray[strarray.size - 1]
                }
                offset += 64

                // incident classification
                var str = String(
                    VriList,
                    offset,
                    32
                ).split("\u0000")[0].trim()
                tagFrag?.setPref("tag_incident_classification", str)
                offset += 32

                // case number
                str = String(
                    VriList,
                    offset,
                    64
                ).split("\u0000")[0].trim()
                tagFrag?.setPref("tag_case_number", str)
                offset += 64

                // Priority
                str = String(
                    VriList,
                    offset,
                    20
                ).split("\u0000")[0].trim()
                tagFrag?.setPref("tag_priority", str)
                offset += 20

                // Officer ID
                //val eOfficerId = findViewById<View>(R.id.tag_officerid) as EditText
                //eOfficerId?.setText(OfficerId)
                offset += 32    // skip officer ID

                // Notes
                str = String(
                    VriList,
                    offset,
                    255
                ).split("\u0000")[0].trim()
                tagFrag?.setPref("tag_notes", str)

            }

        }
        return
    }

    protected fun onTagEventClick() {
        if (VriItemSize > 0) {
            val vri = ByteArray(VriItemSize)
            Arrays.fill(vri, ' '.toByte())

            var str: String?
            var len: Int
            var offset = 0

            // Vri
            val bVri = m_vri.toByteArray()
            len = bVri.size
            if (len > 64) len = 64
            System.arraycopy(bVri, 0, vri, offset, len)
            offset += 64

            // incident classification
            str = tagFrag!!.getPref("tag_incident_classification")
            val bIncident = str!!.toByteArray()
            len = bIncident.size
            if (len > 32) len = 32
            System.arraycopy(bIncident, 0, vri, offset, len)
            offset += 32

            // case number
            str = tagFrag!!.getPref("tag_case_number")
            val bCase = str!!.toByteArray()
            len = bCase.size
            if (len > 64) len = 64
            System.arraycopy(bCase, 0, vri, offset, len)
            offset += 64

            // Priority
            str = tagFrag!!.getPref("tag_priority")
            val bPrio = str!!.toByteArray()
            len = bPrio.size
            if (len > 20) len = 20
            System.arraycopy(bPrio, 0, vri, offset, len)
            offset += 20

            // Officer ID
            offset += 32    // skip officer ID

            // Notes
            str = tagFrag!!.getPref("tag_notes")
            val bNotes = str!!.toByteArray()
            len = bNotes.size
            val maxlen = VriItemSize - offset
            if (len > maxlen) len = maxlen
            System.arraycopy(bNotes, 0, vri, offset, len)

            // set VRI
            if (mPwProtocol != null) {
                mPwProtocol!!.SetVri(vri)
            }

        }
        finish()
    }

}
