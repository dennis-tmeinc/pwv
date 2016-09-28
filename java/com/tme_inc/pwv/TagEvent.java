package com.tme_inc.pwv;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;

import java.util.Arrays;
import java.util.Calendar;

public class TagEvent extends Activity {

    private PWProtocol mPwProtocol ;
    private int VriItemSize=0 ;

    // DVR Date/Time in BCD
    private long DvrDate = 0 ;
    private long DvrTime = 0 ;


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


        setContentView(R.layout.activity_tag_event);

        // init incident list
        Spinner spinner ;
        spinner = (Spinner)findViewById(R.id.tag_incident);
        if( spinner!=null ) {
            ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                    R.array.tag_incident_list, android.R.layout.simple_spinner_item);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinner.setAdapter(adapter);
        }

        // init priority list
        spinner = (Spinner)findViewById(R.id.tag_priority);
        if( spinner!=null ) {
            ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                    R.array.tag_priority_list, android.R.layout.simple_spinner_item);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinner.setAdapter(adapter);
        }

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
                                          onCancelClick();
                                      }
                                  });

    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_tag_event, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    protected void onTagPWEvent(Bundle result) {
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
                EditText eVri = (EditText)findViewById(R.id.tag_vri);
                if( eVri!=null ) {
                    eVri.setText(str);
                }
                // splite officer ID from vri
                String [] strarray = str.split("-");
                if( strarray.length>2 ) {
                    OfficerId = strarray[strarray.length-1];
                }
                offset+=64 ;

                // incident classification
                str =  new String(VriList, offset, 32).split("\0")[0].trim();
                Spinner sIncident = (Spinner)findViewById(R.id.tag_incident);
                if( sIncident!=null ) {
                    ArrayAdapter<CharSequence> adapter = (ArrayAdapter<CharSequence>)sIncident.getAdapter();
                    int pos = adapter.getPosition(str);
                    if(pos<0) {
//                                    adapter.add(str);
//                                    pos = adapter.getPosition(str);
                    }
                    sIncident.setSelection(pos);
                }
                offset+=32 ;

                // case number
                str =  new String(VriList, offset, 64).split("\0")[0].trim();
                EditText eCase = (EditText)findViewById(R.id.tag_casenumber);
                if( eCase!=null ) {
                    eCase.setText(str);
                }
                offset+=64 ;

                // Priority
                str =  new String(VriList, offset, 20).split("\0")[0].trim();
                Spinner sPrio = (Spinner)findViewById(R.id.tag_priority);
                if( sPrio!=null ) {
                    ArrayAdapter<CharSequence> adapter = (ArrayAdapter<CharSequence>)sPrio.getAdapter();
                    int pos = adapter.getPosition(str);
                    if(pos<0) {
//                                    adapter.add(str);
//                                    pos = adapter.getPosition(str);
                    }
                    sPrio.setSelection(pos);
                }
                offset+=20 ;

                // Officer ID
                EditText eOfficerId = (EditText)findViewById(R.id.tag_officerid);
                if( eOfficerId!=null ) {
                    eOfficerId.setText(OfficerId);
                }
                offset+=32 ;    // skip officer ID

                // Notes
                str =  new String(VriList, offset, 255).split("\0")[0].trim();
                EditText eNotes = (EditText)findViewById(R.id.tag_notes);
                if( eNotes!=null ) {
                    eNotes.setText(str);
                }

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
            EditText eVri = (EditText) findViewById(R.id.tag_vri);
            if (eVri != null) {
                byte[] bVri = eVri.getText().toString().getBytes();
                len = bVri.length;
                if (len > 64) len = 64;
                System.arraycopy(bVri, 0, vri, offset, len);
            }
            offset += 64;

            // incident classification
            Spinner sIncident = (Spinner) findViewById(R.id.tag_incident);
            if (sIncident != null) {
                str = (String) sIncident.getSelectedItem();
                byte[] bIncident = str.getBytes();
                len = bIncident.length;
                if (len > 32) len = 32;
                System.arraycopy(bIncident, 0, vri, offset, len);
            }
            offset += 32;

            // case number
            EditText eCase = (EditText) findViewById(R.id.tag_casenumber);
            if (eCase != null) {
                byte[] bCase = eCase.getText().toString().getBytes();
                len = bCase.length;
                if (len > 64) len = 64;
                System.arraycopy(bCase, 0, vri, offset, len);
            }
            offset += 64;

            // Priority
            Spinner sPrio = (Spinner) findViewById(R.id.tag_priority);
            if (sPrio != null) {
                str = (String) sPrio.getSelectedItem();
                byte[] bPrio = str.getBytes();
                len = bPrio.length;
                if (len > 20) len = 20;
                System.arraycopy(bPrio, 0, vri, offset, len);
            }
            offset += 20;

            // Officer ID
            offset += 32;    // skip officer ID

            // Notes
            EditText eNotes = (EditText) findViewById(R.id.tag_notes);
            if (eNotes != null) {
                byte[] bNotes = eNotes.getText().toString().getBytes();
                len = bNotes.length;
                int maxlen = VriItemSize - offset;
                if (len > maxlen) len = maxlen;
                System.arraycopy(bNotes, 0, vri, offset, len);
            }

            // set VRI
            if (mPwProtocol != null) {
                mPwProtocol.SetVri(vri, null);
            }

        }
        finish();
    }

    protected void onCancelClick() {
        finish();
    }

}
