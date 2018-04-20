package com.tme_inc.pwv

import android.app.Activity
import android.content.SharedPreferences
import android.os.Bundle
import android.preference.EditTextPreference
import android.preference.ListPreference
import android.preference.Preference
import android.preference.PreferenceFragment
import android.preference.PreferenceManager
import android.widget.CheckBox
import android.widget.EditText

// kotlin synthetic extension

class SettingsActivity : Activity() {

    class SettingsFragment : PreferenceFragment() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)

            val prefMan = preferenceManager
            prefMan.sharedPreferencesName = "pwv"

            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.pref_pwv)

            // add Change Listener to set summary & init summary
            bindSummary("officerId")
            bindSummary("dvrIp")
            bindSummary("autoCheckInterval")
        }

        protected fun bindSummary(key: String) {
            val pref = findPreference(key)
            if (pref != null) {
                pref.onPreferenceChangeListener = object : Preference.OnPreferenceChangeListener {
                    override fun onPreferenceChange(
                        preference: Preference?,
                        newValue: Any?
                    ): Boolean {
                        preference!!.setSummary(newValue.toString());
                        return true;
                    }
                }

                if (pref is ListPreference) {
                    pref.summary = pref.value.toString()
                } else if (pref is EditTextPreference) {
                    pref.summary = pref.text
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fragmentManager.beginTransaction()
            .replace(android.R.id.content, SettingsFragment())
            .commit()

    }

}