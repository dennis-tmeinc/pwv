package com.tme_inc.pwv;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;

/**
 * Created by dennis on 1/15/15.
 */
public class OfficerIdDialog extends DialogFragment {

    private PWProtocol mPwProtocol ;
    private View mBaseView ;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        // Get the layout inflater
        LayoutInflater inflater = getActivity().getLayoutInflater();
        mBaseView = inflater.inflate(R.layout.dialog_officerid, null);

        // Inflate and set the layout for the dialog
        // Pass null as the parent view because its going in the dialog layout
        builder.setView(mBaseView)
                .setMessage("Select or Enter Officer ID")
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {

                        AutoCompleteTextView e_officerid = (AutoCompleteTextView) mBaseView.findViewById(R.id.e_officerid) ;
                        if( e_officerid != null ) {
                            String offierid = e_officerid.getText().toString();
                            saveOfficerId(offierid) ;
                            mPwProtocol.SetOfficerId(offierid, null);
                        }
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // User cancelled the dialog
                    }
                });

        mPwProtocol = new PWProtocol();
        if( savedInstanceState==null ) {
            mPwProtocol.GetOfficerIDList(new PWProtocol.PWListener() {
                @Override
                public void onPWEvent(Bundle result) {
                    if (mBaseView != null && result != null) {
                        int nid = result.getInt("policeId_number", 0);
                        int nidlen = result.getInt("policeId_size");
                        byte[] idlist = result.getByteArray("policeId_list");
                        if (nid > 0) {
                            int niditemlen = nidlen / nid;
                            String[] ids = new String[nid];
                            for (int i = 0; i < nid; i++) {
                                ids[i] = new String(idlist, i * niditemlen, niditemlen).split("\0")[0].trim();
                            }

                            AutoCompleteTextView e_officerid = (AutoCompleteTextView) mBaseView.findViewById(R.id.e_officerid);
                            if (e_officerid != null) {
                                ArrayAdapter<String> adapter = new ArrayAdapter<String>(getActivity(),
                                        android.R.layout.simple_dropdown_item_1line, ids);
                                e_officerid.setAdapter(adapter);
                                e_officerid.setOnClickListener(new AutoCompleteTextView.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        ((AutoCompleteTextView) v).showDropDown();
                                    }
                                });
                                e_officerid.setText(ids[0], false);
                            }
                        }
                    }
                    return;
                }
            });
        }


        return builder.create();
    }

    private void saveOfficerId( String officerId ) {
        SharedPreferences prefs = getActivity().getSharedPreferences("pwv", 0);
        SharedPreferences.Editor ed = prefs.edit();
        ed.putString("officerId", officerId);
        ed.commit();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        mBaseView = null ;
        mPwProtocol.close();
        mPwProtocol=null;
    }
}
