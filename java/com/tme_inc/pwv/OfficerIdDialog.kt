package com.tme_inc.pwv

import android.app.AlertDialog
import android.app.Dialog
import android.app.DialogFragment
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import kotlinx.android.synthetic.main.dialog_officerid.view.*

/**
 * Created by dennis on 1/15/15.
 */
class OfficerIdDialog : DialogFragment() {

    private var mPwProtocol: PWProtocol = PWProtocol()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        // Get the layout inflater
        val baseView = activity.layoutInflater.inflate(R.layout.dialog_officerid, null)

        if (savedInstanceState == null) {
            mPwProtocol.GetOfficerIDList({ result ->
                if (baseView != null && result != null) {
                    val nid = result.getInt("policeId_number", 0)
                    val nidlen = result.getInt("policeId_size")
                    val idlist = result.getByteArray("policeId_list")
                    if (nid > 0) {
                        val niditemlen = nidlen / nid
                        val ids = ArrayList<String>()
                        for (i in 0 until nid) {
                            ids.add( String(
                                idlist!!,
                                i * niditemlen,
                                niditemlen)
                                .split("\u0000")[0].trim())
                        }
                        baseView.e_officerid.setAdapter(ArrayAdapter<String>(
                            activity,
                            android.R.layout.simple_dropdown_item_1line,
                            ids
                        ))
                        if( ids.size>0 ) {
                            baseView.e_officerid.setOnClickListener { v: View ->
                                (v as AutoCompleteTextView).showDropDown()
                            }
                            baseView.e_officerid.setText(ids[0], false)
                        }
                        else {
                            baseView.e_officerid.setText("00001", false)
                        }
                    }
                }
            })
        }

        // Inflate and set the layout for the dialog
        // Pass null as the parent view because its going in the dialog layout
        return AlertDialog.Builder(activity)
            .setView(baseView)
            .setMessage("Select or Enter Officer ID")
            .setPositiveButton("OK") { dialog, id ->
                val offierid = baseView.e_officerid.text.toString()

                mPwProtocol.SetOfficerId(offierid)

                activity.getSharedPreferences("pwv", 0)
                    .edit()
                    .putString("officerId", offierid)
                    .apply()
            }
            .create()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mPwProtocol.close()
    }
}
