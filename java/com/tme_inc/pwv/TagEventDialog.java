package com.tme_inc.pwv;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;

import java.util.Arrays;
import java.util.Calendar;

/**
 * Created by dennis on 1/7/15.
 */
public class TagEventDialog extends DialogFragment {

    private PWProtocol mPwProtocol ;
    private View mBaseView ;
    private int VriItemSize=0 ;

    // DVR Date/Time in BCD
    private long DvrDate = 0 ;
    private long DvrTime = 0 ;


    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        // Get the layout inflater
        LayoutInflater inflater = getActivity().getLayoutInflater();
        mBaseView = inflater.inflate(R.layout.dialog_tagevent, null);

        // Inflate and set the layout for the dialog
        // Pass null as the parent view because its going in the dialog layout
        builder.setView(mBaseView)
                .setMessage("Tag Event")
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        if (VriItemSize > 0) {
                            byte[] vri = new byte[VriItemSize];
                            Arrays.fill(vri, (byte) ' ');

                            String str;
                            int len;
                            int offset = 0;

                            // Vri
                            EditText eVri = (EditText) mBaseView.findViewById(R.id.tag_vri);
                            if (eVri != null) {
                                byte[] bVri = eVri.getText().toString().getBytes();
                                len = bVri.length;
                                if (len > 64) len = 64;
                                System.arraycopy(bVri, 0, vri, offset, len);
                            }
                            offset += 64;

                            // incident classification
                            Spinner sIncident = (Spinner) mBaseView.findViewById(R.id.tag_incident);
                            if (sIncident != null) {
                                str = (String) sIncident.getSelectedItem();
                                byte[] bIncident = str.getBytes();
                                len = bIncident.length;
                                if (len > 32) len = 32;
                                System.arraycopy(bIncident, 0, vri, offset, len);
                            }
                            offset += 32;

                            // case number
                            EditText eCase = (EditText) mBaseView.findViewById(R.id.tag_casenumber);
                            if (eCase != null) {
                                byte[] bCase = eCase.getText().toString().getBytes();
                                len = bCase.length;
                                if (len > 64) len = 64;
                                System.arraycopy(bCase, 0, vri, offset, len);
                            }
                            offset += 64;

                            // Priority
                            Spinner sPrio = (Spinner) mBaseView.findViewById(R.id.tag_priority);
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
                            EditText eNotes = (EditText) mBaseView.findViewById(R.id.tag_notes);
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
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // User cancelled the dialog

                    }
                });

        Spinner spinner ;
        spinner = (Spinner)mBaseView.findViewById(R.id.tag_incident);
        if( spinner!=null ) {
            ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(getActivity(),
                    R.array.tag_incident_list, android.R.layout.simple_spinner_item);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinner.setAdapter(adapter);
        }

        spinner = (Spinner)mBaseView.findViewById(R.id.tag_priority);
        if( spinner!=null ) {
            ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(getActivity(),
                    R.array.tag_priority_list, android.R.layout.simple_spinner_item);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinner.setAdapter(adapter);
        }

        mPwProtocol = new PWProtocol();
        if( savedInstanceState==null ) {
            mPwProtocol.GetVri(new PWProtocol.PWListener() {
                @Override
                public void onPWEvent(Bundle result) {
                    if ( mBaseView!=null && result!=null ) {
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
                            EditText eVri = (EditText)mBaseView.findViewById(R.id.tag_vri);
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
                            Spinner sIncident = (Spinner)mBaseView.findViewById(R.id.tag_incident);
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
                            EditText eCase = (EditText)mBaseView.findViewById(R.id.tag_casenumber);
                            if( eCase!=null ) {
                                eCase.setText(str);
                            }
                            offset+=64 ;

                            // Priority
                            str =  new String(VriList, offset, 20).split("\0")[0].trim();
                            Spinner sPrio = (Spinner)mBaseView.findViewById(R.id.tag_priority);
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
                            EditText eOfficerId = (EditText)mBaseView.findViewById(R.id.tag_officerid);
                            if( eOfficerId!=null ) {
                                eOfficerId.setText(OfficerId);
                            }
                            offset+=32 ;    // skip officer ID

                            // Notes
                            str =  new String(VriList, offset, 255).split("\0")[0].trim();
                            EditText eNotes = (EditText)mBaseView.findViewById(R.id.tag_notes);
                            if( eNotes!=null ) {
                                eNotes.setText(str);
                            }

                        }

                    }
                    return;
                }
            });
        }



        return builder.create();
    }

    @Override
    public void onDestroyView() {
        mBaseView = null ;
        mPwProtocol.close();
        super.onDestroyView();
    }

    // get DVRTime from dvr
    void getSetCurDvrTime(){
        DvrDate = 20990101 ;
        DvrTime = 0 ;
    }

    void setDvrTime(long timestamp) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timestamp);
        DvrDate = calendar.get(Calendar.YEAR) * 10000 +
                (calendar.get(Calendar.MONTH) - Calendar.JANUARY + 1) * 100 +
                calendar.get(Calendar.DAY_OF_MONTH) ;
        DvrTime = calendar.get(Calendar.HOUR_OF_DAY) * 10000 +
                calendar.get(Calendar.MINUTE) * 100 +
                calendar.get(Calendar.SECOND) ;
    }

}
