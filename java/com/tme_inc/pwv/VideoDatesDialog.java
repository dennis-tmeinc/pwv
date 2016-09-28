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
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Created by dennis on 28/04/15.
 */
public class VideoDatesDialog extends DialogFragment {

    private View mBaseView ;
    private Handler m_UiHandler ;
    private Integer [] m_DateList ;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        // Get the layout inflater
        LayoutInflater inflater = getActivity().getLayoutInflater();
        mBaseView = inflater.inflate(R.layout.dialog_videodate, null);

        // Inflate and set the layout for the dialog
        // Pass null as the parent view because its going in the dialog layout
        builder.setView(mBaseView);

        Calendar date = Calendar.getInstance();
        String format = "  MMMM d, yyyy ";
        SimpleDateFormat sdf = new SimpleDateFormat(format, Locale.US);

        String [] stringArray = new String [m_DateList.length] ;
        for( int i=0 ; i<m_DateList.length; i++ ) {
            date.clear();
            date.set(Calendar.YEAR, m_DateList[i]/10000) ;
            date.set(Calendar.MONTH, m_DateList[i]%10000/100 -1+Calendar.JANUARY ) ;
            date.set(Calendar.DATE, m_DateList[i]%100) ;
            stringArray[i] = sdf.format( date.getTime() );
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(getActivity(),
                android.R.layout.simple_list_item_1, stringArray);

        ListView listView = (ListView)mBaseView.findViewById(R.id.videoDateList);
        if( listView!=null )
            listView.setAdapter(adapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                int selectedDate = m_DateList[position] ;
                m_UiHandler.obtainMessage( PWMessage.MSG_DATE_SELECTED, selectedDate, 0 ).sendToTarget();
                dismiss();
            }
        });

        return builder.create();
    }

    @Override
    public void onDestroyView() {
        mBaseView = null ;
        super.onDestroyView();
    }


    public void setDateList( Integer [] dateList ){
        m_DateList = dateList ;
    }

    public void setUIHandler( Handler handler){
        m_UiHandler = handler ;
    }
}
