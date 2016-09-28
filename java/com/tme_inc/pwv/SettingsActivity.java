package com.tme_inc.pwv;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.widget.CheckBox;
import android.widget.EditText;

public class SettingsActivity extends Activity {

    public static class SettingsFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            PreferenceManager prefMan = getPreferenceManager();
            prefMan.setSharedPreferencesName("pwv");

            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.pref_pwv);

            // add Change Listener to set summary & init summary
            bindSummary("officerId");
            bindSummary("dvrIp");
            bindSummary("autoCheckInterval");
        }

        protected void bindSummary( String key ) {
            Preference pref = findPreference(key) ;
            if( pref != null ) {
                pref.setOnPreferenceChangeListener(prefChangeListener);
                if (pref instanceof ListPreference) {
                    pref.setSummary( ((ListPreference) pref).getValue().toString());
                }
                else if (pref instanceof EditTextPreference) {
                    pref.setSummary( ((EditTextPreference) pref).getText());
                }
            }
        }

        // bind summary to value
        private static Preference.OnPreferenceChangeListener prefChangeListener = new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object value) {
                preference.setSummary(value.toString());
                return true;
            }
        };

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new SettingsFragment()  )
                .commit();

    }

}