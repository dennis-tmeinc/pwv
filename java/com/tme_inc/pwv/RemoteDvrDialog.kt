package com.tme_inc.pwv

import android.app.AlertDialog
import android.app.Dialog
import android.app.DialogFragment
import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ListView

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Created by dennis on 06/05/15.
 */
class RemoteDvrDialog : DialogFragment() {

    private var m_UiHandler: Handler? = null
    private val m_DateList: Array<Int>? = null

    override fun onCreateDialog(savedInstanceState: Bundle): Dialog {
        val builder = AlertDialog.Builder(activity)
        // Get the layout inflater
        val inflater = activity.layoutInflater
        val baseView = inflater.inflate(R.layout.dialog_videodate, null)

        // Inflate and set the layout for the dialog
        // Pass null as the parent view because its going in the dialog layout
        builder.setView(baseView)

        val date = Calendar.getInstance()
        val format = "  MMMM d, yyyy "
        val sdf = SimpleDateFormat(format, Locale.US)

        val stringArray = arrayOfNulls<String>(m_DateList!!.size)
        for (i in m_DateList.indices) {
            date.clear()
            date.set(Calendar.YEAR, m_DateList[i] / 10000)
            date.set(Calendar.MONTH, m_DateList[i] % 10000 / 100 - 1 + Calendar.JANUARY)
            date.set(Calendar.DATE, m_DateList[i] % 100)
            stringArray[i] = sdf.format(date.time)
        }

        val adapter = ArrayAdapter<String>(
            activity,
            android.R.layout.simple_list_item_1, stringArray
        )

        val listView = baseView.findViewById<View>(R.id.videoDateList) as ListView
        if (listView != null)
            listView.adapter = adapter

        listView.onItemClickListener =
                AdapterView.OnItemClickListener { parent, view, position, id ->
                    val selectedDate = m_DateList[position]
                    m_UiHandler!!.obtainMessage(MSG_DATE_SELECTED, selectedDate, 0)
                        .sendToTarget()
                    dismiss()
                }

        return builder.create()
    }

    fun setUIHandler(handler: Handler) {
        m_UiHandler = handler
    }
}
