package com.tme_inc.pwv;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;

public class ArchiveActivity extends Activity {

    private class ClipInfo {
        public String filename ;
        public int channel;
        public int bcddate;        // bcd date as in filename
        public int bcdtime;        // bcd time as in filename
        public int cliplength;
        public char cliptype;      // 'N' or 'L'
    } ;

    PWProtocol pwProtocol = new PWProtocol();

    private class ClipsArrayAdapter extends ArrayAdapter<ClipInfo> implements Comparator<ClipInfo> {

        public ClipsArrayAdapter(Context context) {

            super(context, R.layout.layout_clipitem);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View rowView ;
            ClipInfo ci ;
            ci = this.getItem(position) ;

            if(convertView==null) {
                LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                rowView = inflater.inflate(R.layout.layout_clipitem, parent, false);
            }
            else {
                rowView = convertView ;
            }
            TextView tv = (TextView) rowView.findViewById(R.id.clipname) ;
            tv.setText(ci.filename);

            return rowView ;
        }

        @Override
        public int compare(ClipInfo lhs, ClipInfo rhs) {
            return 0;
        }

        public void load() {
            final ClipsArrayAdapter _this = this ;
            clear();
            pwProtocol.getClipList(
                    new PWProtocol.PWListener() {
                        @Override
                        public void onPWEvent(Bundle result) {
                            if( result!=null) {
                                String cliplist[] = result.getStringArray("clips");
                                if( cliplist!=null) {
                                    for(int i=0; i<cliplist.length; i++ ) {
                                        ClipInfo ci = new ClipInfo();
                                        ci.filename = cliplist[i];
                                        _this.add(ci);
                                    }
                                }
                                _this.notifyDataSetChanged();
                            }
                        }
                    },
                    -1 );
                    }
    }

    private ListView m_cliplist ;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_archive);

        m_cliplist = (ListView) findViewById(R.id.listClips);
        ClipsArrayAdapter clipsArrayAdapter = new ClipsArrayAdapter(this);
        m_cliplist.setAdapter(clipsArrayAdapter);

        m_cliplist.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                //view.showContextMenu();
                //onHighlightItem(view);
            }
        });

        Button button = (Button)findViewById(R.id.btRefresh);
        button.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((ClipsArrayAdapter)m_cliplist.getAdapter()).load();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        ((ClipsArrayAdapter)m_cliplist.getAdapter()).load();
    }

    @Override
    protected void onPause() {
        super.onPause();

        pwProtocol.cancel();
    }
}
