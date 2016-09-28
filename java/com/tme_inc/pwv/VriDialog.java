package com.tme_inc.pwv;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView ;


/**
 * Created by dennis on 2/6/15.
 */
public class VriDialog extends DialogFragment {

    private PWProtocol mPwProtocol ;
    private View mBaseView ;
    private int VriItemSize=0 ;

    private Handler m_UiHandler ;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        // Get the layout inflater
        LayoutInflater inflater = getActivity().getLayoutInflater();
        mBaseView = inflater.inflate(R.layout.dialog_vrilist, null);

        // Inflate and set the layout for the dialog
        // Pass null as the parent view because its going in the dialog layout
        builder.setView(mBaseView);

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

                            String [] stringArray = new String [VriSize] ;
                            for( int i=0 ; i<VriSize; i++ ) {
                                int o = i*VriItemSize ;
                                String vri =  new String(VriList, o, 64).split("\0")[0].trim();
                                stringArray[i] = vri ;
                            }

                            ArrayAdapter<String> adapter = new ArrayAdapter<String>(getActivity(),
                                    android.R.layout.simple_list_item_1, stringArray);

                            ListView listView = (ListView)mBaseView.findViewById(R.id.vrilist);
                            if( listView!=null )
                                listView.setAdapter(adapter);

                        }

                    }
                    return;
                }
            });
        }

        ListView listView = (ListView)mBaseView.findViewById(R.id.vrilist);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if( m_UiHandler!=null ) {
                    String vri = ((TextView)view).getText().toString();
                    m_UiHandler.obtainMessage( PWMessage.MSG_VRI_SELECTED, (Object)vri).sendToTarget();
                    dismiss();
                }
            }
        });

        return builder.create();
    }

    @Override
    public void onDestroyView() {
        mBaseView = null ;
        mPwProtocol.close();
        super.onDestroyView();
    }

    public void setUIHandler( Handler handler){
        m_UiHandler = handler ;
    }
}
