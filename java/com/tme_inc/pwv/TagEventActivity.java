package com.tme_inc.pwv;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.Arrays;
import java.util.Calendar;


public class TagEventActivity extends Activity {

    protected PWProtocol mPwProtocol ;
    protected int VriItemSize=0 ;

    // DVR Date/Time in BCD
    protected long DvrDate = 0 ;
    protected long DvrTime = 0 ;

    public static class SettingsFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            PreferenceManager prefMan = getPreferenceManager();
            prefMan.setSharedPreferencesName("tag_event_prefs");
            SharedPreferences sPref = prefMan.getSharedPreferences() ;

            // Empty all prefs
            SharedPreferences.Editor ed = sPref.edit();
            ed.putString("tag_incident_classification", "");
            ed.putString("tag_case_number", "");
            ed.putString("tag_priority", "");
            ed.putString("tag_notes", "");
            ed.commit();

            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.pref_tag_event);

            // add Change Listener to set summary & init summary
            prefMan.findPreference("tag_incident_classification")
                .setOnPreferenceChangeListener(prefChangeListener);
            prefMan.findPreference("tag_case_number")
                .setOnPreferenceChangeListener(prefChangeListener);
            prefMan.findPreference("tag_priority")
                .setOnPreferenceChangeListener(prefChangeListener);
            prefMan.findPreference("tag_notes")
                .setOnPreferenceChangeListener(prefChangeListener);

        }

        // bind summary to value
        private static Preference.OnPreferenceChangeListener prefChangeListener = new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object value) {
                preference.setSummary(value.toString());
                return true;
            }
        };

        public void setPref( String key, Object value) {
            Preference pref = findPreference(key) ;
            if( pref != null ) {
                String v = value.toString();
                if (pref instanceof ListPreference) {
                    ((ListPreference) pref).setValue(v);
                    if( v.length() == 0 ) {
                        v = "(Not Set)" ;
                    }
                }
                else if (pref instanceof EditTextPreference) {
                    ((EditTextPreference) pref).setText(v);
                    if( v.length() == 0 ) {
                        v = "(Please Enter)" ;
                    }
                }
                else {
                    SharedPreferences.Editor editor = pref.getEditor();
                    editor.putString(key, v);
                    editor.commit();
                }
                pref.setSummary(v);
            }
        }

        public String getPref( String key ) {
            Preference pref = findPreference(key) ;
            if( pref != null ) {
                if (pref instanceof ListPreference) {
                    return ((ListPreference) pref).getValue();
                } else if (pref instanceof EditTextPreference) {
                    return ((EditTextPreference) pref).getText();
                }
                else {
                    PreferenceManager prefMan = getPreferenceManager();
                    SharedPreferences sPref = prefMan.getSharedPreferences();
                    return sPref.getString(key, "");
                }
            }
            return "";
        }
    }

    protected SettingsFragment tagFrag ;

    private Handler uiHandler = new Handler();
    private Runnable autoClose = new Runnable() {
        public void run() {
           finish();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        long timestamp = getIntent().getLongExtra("DvrTime", 0L);

        Calendar calendar = Calendar.getInstance();
        if( timestamp>0L ) {
            calendar.clear();
            calendar.setTimeInMillis(timestamp);
        }
        DvrDate = calendar.get(Calendar.YEAR) * 10000 +
                (calendar.get(Calendar.MONTH) - Calendar.JANUARY + 1) * 100 +
                calendar.get(Calendar.DAY_OF_MONTH) ;
        DvrTime = calendar.get(Calendar.HOUR_OF_DAY) * 10000 +
                calendar.get(Calendar.MINUTE) * 100 +
                calendar.get(Calendar.SECOND) ;

        setContentView(R.layout.activity_tag);

        // Display the fragment as the main content.
        tagFrag = new SettingsFragment() ;
        getFragmentManager().beginTransaction()
                .replace(R.id.tagfragment, tagFrag)
                .commit();


        // init PW event processor
        //
        mPwProtocol = new PWProtocol();
        mPwProtocol.GetVri( new PWProtocol.PWListener() {
            @Override
            public void onPWEvent(Bundle result) {
                onTagPWEvent(result);
            }
        });


        Button button = (Button)findViewById(R.id.button_tag) ;
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onTagEventClick();
            }
        });

        button= (Button)findViewById(R.id.button_cancel) ;
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        // keep screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // add auto close message
        uiHandler.postDelayed( autoClose, 300000 ) ;

    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        // extent to another 5 min
        uiHandler.removeCallbacks(autoClose);
        uiHandler.postDelayed(autoClose, 300000);
    }

    protected String m_vri ;

    protected void onTagPWEvent(Bundle result) {
        if ( result!=null ) {
            SettingsFragment sfreg = (SettingsFragment)null;
        }

        if ( result!=null ) {
            VriItemSize = result.getInt("VriItemSize", 0);
            int VriSize = result.getInt("VriListSize", 0);
            byte [] VriList = result.getByteArray("VriList");
            if (VriSize>0 && VriItemSize>0 && VriList!=null ) {
                int offset = 0 ;

                long searchdatetime = (DvrDate - 20000000) * 10000 + DvrTime/100  ;
                //search for current index
                long vr_p =0 ;
                int vr_pi = 0 ;
                long vr_n = 0 ;
                int vr_ni = 0;
                int o ;

                // Search matching vri
                for( o=0; o<(VriSize*VriItemSize); o+=VriItemSize ) {
                    long vridatetime = 0 ;

                    //VRI
                    String vri =  new String(VriList, o, 64).split("\0")[0].trim();
                    m_vri = vri ;   // store VRI

                    // Split vri date/time
                    String [] vriarray = vri.split("-");
                    if( vriarray.length>1 ) {
                        try{
                            vridatetime = Long.parseLong(vriarray[1]) ;
                        }catch(NumberFormatException e){
                            vridatetime = 0;
                        }
                        catch( Exception e ) {
                            vridatetime = 0 ;
                        }
                    }

                    if( vridatetime<=searchdatetime ) {
                        if( vr_p == 0 ) {
                            vr_p = vridatetime ;
                            vr_pi = o ;
                        }
                        else if( vridatetime > vr_p ) {
                            vr_p = vridatetime ;
                            vr_pi = o ;
                        }
                    }
                    else {
                        if( vr_n == 0 ) {
                            vr_n = vridatetime ;
                            vr_ni = o ;
                        }
                        else if( vridatetime < vr_n ) {
                            vr_n = vridatetime ;
                            vr_ni = o ;
                        }
                    }
                }

                if( vr_p !=0 ) {
                    o=vr_pi ;
                } else if( vr_n!=0 ) {
                    o=vr_ni ;
                }
                else {
                    o= 0 ;
                }


                offset = o ;

                String OfficerId = "";
                String str =  new String(VriList, offset, 64).split("\0")[0].trim();
                m_vri = str ;

                // set title
                TextView vriTitle = (TextView)findViewById(R.id.title_vri);
                if( vriTitle!=null ) {
                    vriTitle.setText("VRI : " + m_vri);
                }

                // splite officer ID from vri
                String [] strarray = str.split("-");
                if( strarray.length>2 ) {
                    OfficerId = strarray[strarray.length-1];
                }
                offset+=64 ;

                // incident classification
                str =  new String(VriList, offset, 32).split("\0")[0].trim();
                tagFrag.setPref("tag_incident_classification" , str);
                offset+=32 ;

                // case number
                str =  new String(VriList, offset, 64).split("\0")[0].trim();
                tagFrag.setPref("tag_case_number" , str);
                offset+=64 ;

                // Priority
                str =  new String(VriList, offset, 20).split("\0")[0].trim();
                tagFrag.setPref("tag_priority" , str);
                offset+=20 ;

                // Officer ID
                EditText eOfficerId = (EditText)findViewById(R.id.tag_officerid);
                if( eOfficerId!=null ) {
                    eOfficerId.setText(OfficerId);
                }
                offset+=32 ;    // skip officer ID

                // Notes
                str =  new String(VriList, offset, 255).split("\0")[0].trim();
                tagFrag.setPref("tag_notes" , str);

            }

        }
        return;
    }

    protected  void onTagEventClick()
    {
        if (VriItemSize > 0) {
            byte[] vri = new byte[VriItemSize];
            Arrays.fill(vri, (byte) ' ');

            String str;
            int len;
            int offset = 0;

            // Vri
            byte[] bVri = m_vri.getBytes();
            len = bVri.length;
            if (len > 64) len = 64;
            System.arraycopy(bVri, 0, vri, offset, len);
            offset += 64 ;

            // incident classification
            str = tagFrag.getPref("tag_incident_classification") ;
            byte[] bIncident = str.getBytes();
            len = bIncident.length;
            if (len > 32) len = 32;
            System.arraycopy(bIncident, 0, vri, offset, len);
            offset += 32;

            // case number
            str = tagFrag.getPref("tag_case_number") ;
            byte[] bCase = str.getBytes();
            len = bCase.length;
            if (len > 64) len = 64;
            System.arraycopy(bCase, 0, vri, offset, len);
            offset += 64;

            // Priority
            str = tagFrag.getPref("tag_priority") ;
            byte[] bPrio = str.getBytes();
            len = bPrio.length;
            if (len > 20) len = 20;
            System.arraycopy(bPrio, 0, vri, offset, len);
            offset += 20;

            // Officer ID
            offset += 32;    // skip officer ID

            // Notes
            str = tagFrag.getPref("tag_notes") ;
            byte[] bNotes = str.getBytes();
            len = bNotes.length;
            int maxlen = VriItemSize - offset;
            if (len > maxlen) len = maxlen;
            System.arraycopy(bNotes, 0, vri, offset, len);

            // set VRI
            if (mPwProtocol != null) {
                mPwProtocol.SetVri(vri, null);
            }

        }
        finish();
    }

}
