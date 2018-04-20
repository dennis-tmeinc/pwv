package com.tme_inc.pwv

import android.app.AlertDialog
import android.app.Dialog
import android.app.DialogFragment
import android.os.Bundle
import android.os.Handler
import android.widget.AdapterView
import android.widget.ArrayAdapter
import kotlinx.android.synthetic.main.dialog_videodate.view.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Created by dennis on 28/04/15.
 */
class VideoDatesDialog : DialogFragment() {

    var dateList = intArrayOf()
    var uiHandler: Handler? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val slist = ArrayList<String>()
        for (d in dateList) {
            slist.add(
                SimpleDateFormat("  MMMM d, yyyy ").format(
                    GregorianCalendar(
                        d / 10000,
                        d % 10000 / 100 - 1 + Calendar.JANUARY,
                        d % 100
                    ).time
                )
            )
        }

        val baseView = activity.layoutInflater.inflate(R.layout.dialog_videodate, null)
        baseView.videoDateList.adapter = ArrayAdapter<String>(
            activity,
            android.R.layout.simple_list_item_1,
            slist
        )

        baseView.videoDateList.onItemClickListener =
                AdapterView.OnItemClickListener { parent, view, position, id ->
                    uiHandler
                        ?.obtainMessage(MSG_DATE_SELECTED, dateList[position], 0)
                        ?.sendToTarget()
                    dismiss()
                }

        return AlertDialog.Builder(activity).setView(baseView).create()
    }

}
