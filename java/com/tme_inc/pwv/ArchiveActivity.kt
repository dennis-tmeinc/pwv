package com.tme_inc.pwv

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import kotlinx.android.synthetic.main.activity_archive.*
import kotlinx.android.synthetic.main.layout_clipitem.view.*
import java.util.*

class ArchiveActivity : Activity() {

    internal var pwProtocol = PWProtocol()

    private class ClipInfo {
        var filename: String? = null
        var channel: Int = 0
        var bcddate: Int = 0        // bcd date as in filename
        var bcdtime: Int = 0        // bcd time as in filename
        var cliplength: Int = 0
        var cliptype: Char = ' '      // 'N' or 'L'
    }

    private inner class ClipsArrayAdapter(context: Context) :
        ArrayAdapter<ClipInfo>(context, R.layout.layout_clipitem), Comparator<ClipInfo> {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val rowView: View
            val ci: ClipInfo? = this.getItem(position)

            if (convertView == null) {
                val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
                rowView = inflater.inflate(R.layout.layout_clipitem, parent, false)
            } else {
                rowView = convertView
            }
            rowView.clipname.text = ci!!.filename

            return rowView
        }

        override fun compare(lhs: ClipInfo, rhs: ClipInfo): Int {
            return 0
        }

        fun load() {
            clear()
            pwProtocol.getClipList(
                { result ->
                    val cliplist = result.getStringArray("clips")
                    if (cliplist != null) {
                        for (clipname in cliplist) {
                            val ci = ClipInfo()
                            ci.filename = clipname
                            this.add(ci)
                        }
                    }
                    this.notifyDataSetChanged()
                },
                -1
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_archive)

        listClips?.adapter = ClipsArrayAdapter(this)

        btRefresh.setOnClickListener { _ ->
            (listClips!!.adapter as ClipsArrayAdapter).load()
        }
    }

    override fun onResume() {
        super.onResume()
        (listClips!!.adapter as ClipsArrayAdapter).load()
    }

    override fun onPause() {
        super.onPause()

        pwProtocol.cancel()
    }
}
