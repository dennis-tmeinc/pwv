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
import android.widget.TextView


/**
 * Created by dennis on 2/6/15.
 */
class VriDialog : DialogFragment() {

    private var mPwProtocol: PWProtocol? = null
    private var mBaseView: View? = null
    private var VriItemSize = 0

    private var m_UiHandler: Handler? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(activity)
        // Get the layout inflater
        val inflater = activity.layoutInflater
        mBaseView = inflater.inflate(R.layout.dialog_vrilist, null)

        // Inflate and set the layout for the dialog
        // Pass null as the parent view because its going in the dialog layout
        builder.setView(mBaseView)

        mPwProtocol = PWProtocol()
        if (savedInstanceState == null) {
            mPwProtocol!!.GetVri( { result ->
                if (mBaseView != null && result != null) {
                    VriItemSize = result.getInt("VriItemSize", 0)
                    val VriSize = result.getInt("VriListSize", 0)
                    val VriList = result.getByteArray("VriList")
                    if (VriSize > 0 && VriItemSize > 0 && VriList != null) {

                        val stringArray = ArrayList<String>()
                        for (i in 0 until VriSize) {
                            stringArray.add(String(
                                VriList,
                                i * VriItemSize,
                                64
                            ).split("\u0000")[0].trim())
                        }

                        val adapter = ArrayAdapter<String>(
                            activity,
                            android.R.layout.simple_list_item_1, stringArray
                        )

                        val listView = mBaseView!!.findViewById<View>(R.id.vrilist) as ListView
                        listView.adapter = adapter
                    }

                }
            })
        }

        val listView = mBaseView!!.findViewById<View>(R.id.vrilist) as ListView
        listView.onItemClickListener =
                AdapterView.OnItemClickListener { parent, view, position, id ->
                    if (m_UiHandler != null) {
                        val vri = (view as TextView).text.toString()
                        m_UiHandler!!.obtainMessage(MSG_VRI_SELECTED, vri as Any)
                            .sendToTarget()
                        dismiss()
                    }
                }

        return builder.create()
    }

    override fun onDestroyView() {
        mBaseView = null
        mPwProtocol!!.close()
        super.onDestroyView()
    }

    fun setUIHandler(handler: Handler) {
        m_UiHandler = handler
    }
}
