package com.tme_inc.pwv

import android.app.AlertDialog
import android.app.Dialog
import android.app.DialogFragment
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner

import java.util.Arrays
import java.util.Calendar

/**
 * Created by dennis on 1/7/15.
 */
class TagEventDialog : DialogFragment() {

    private var mPwProtocol: PWProtocol? = null
    private var mBaseView: View? = null
    private var VriItemSize = 0

    // DVR Date/Time in BCD
    private var DvrDate: Long = 0
    private var DvrTime: Long = 0


    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(activity)
        // Get the layout inflater
        val inflater = activity.layoutInflater
        mBaseView = inflater.inflate(R.layout.dialog_tagevent, null)

        // Inflate and set the layout for the dialog
        // Pass null as the parent view because its going in the dialog layout
        builder.setView(mBaseView)
            .setMessage("Tag Event")
            .setPositiveButton("OK") { dialog, id ->
                if (VriItemSize > 0) {
                    val vri = ByteArray(VriItemSize)
                    Arrays.fill(vri, ' '.toByte())

                    var str: String
                    var len: Int
                    var offset = 0

                    // Vri
                    val eVri = mBaseView!!.findViewById<View>(R.id.tag_vri) as EditText
                    if (eVri != null) {
                        val bVri = eVri.text.toString().toByteArray()
                        len = bVri.size
                        if (len > 64) len = 64
                        System.arraycopy(bVri, 0, vri, offset, len)
                    }
                    offset += 64

                    // incident classification
                    val sIncident = mBaseView!!.findViewById<View>(R.id.tag_incident) as Spinner
                    if (sIncident != null) {
                        str = sIncident.selectedItem as String
                        val bIncident = str.toByteArray()
                        len = bIncident.size
                        if (len > 32) len = 32
                        System.arraycopy(bIncident, 0, vri, offset, len)
                    }
                    offset += 32

                    // case number
                    val eCase = mBaseView!!.findViewById<View>(R.id.tag_casenumber) as EditText
                    if (eCase != null) {
                        val bCase = eCase.text.toString().toByteArray()
                        len = bCase.size
                        if (len > 64) len = 64
                        System.arraycopy(bCase, 0, vri, offset, len)
                    }
                    offset += 64

                    // Priority
                    val sPrio = mBaseView!!.findViewById<View>(R.id.tag_priority) as Spinner
                    if (sPrio != null) {
                        str = sPrio.selectedItem as String
                        val bPrio = str.toByteArray()
                        len = bPrio.size
                        if (len > 20) len = 20
                        System.arraycopy(bPrio, 0, vri, offset, len)
                    }
                    offset += 20

                    // Officer ID
                    offset += 32    // skip officer ID

                    // Notes
                    val eNotes = mBaseView!!.findViewById<View>(R.id.tag_notes) as EditText
                    if (eNotes != null) {
                        val bNotes = eNotes.text.toString().toByteArray()
                        len = bNotes.size
                        val maxlen = VriItemSize - offset
                        if (len > maxlen) len = maxlen
                        System.arraycopy(bNotes, 0, vri, offset, len)
                    }

                    // set VRI
                    if (mPwProtocol != null) {
                        mPwProtocol!!.SetVri(vri)
                    }

                }
            }
            .setNegativeButton("Cancel") { dialog, id ->
                // User cancelled the dialog
            }

        var spinner: Spinner?
        spinner = mBaseView!!.findViewById<View>(R.id.tag_incident) as Spinner
        if (spinner != null) {
            val adapter = ArrayAdapter.createFromResource(
                activity,
                R.array.tag_incident_list, android.R.layout.simple_spinner_item
            )
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinner.adapter = adapter
        }

        spinner = mBaseView!!.findViewById<View>(R.id.tag_priority) as Spinner
        if (spinner != null) {
            val adapter = ArrayAdapter.createFromResource(
                activity,
                R.array.tag_priority_list, android.R.layout.simple_spinner_item
            )
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinner.adapter = adapter
        }

        mPwProtocol = PWProtocol()
        if (savedInstanceState == null) {
            mPwProtocol!!.GetVri( { result ->
                if (mBaseView != null && result != null) {
                    VriItemSize = result.getInt("VriItemSize", 0)
                    val VriSize = result.getInt("VriListSize", 0)
                    val VriList = result.getByteArray("VriList")
                    if (VriSize > 0 && VriItemSize > 0 && VriList != null) {
                        var offset = 0

                        val searchdatetime = (DvrDate - 20000000) * 10000 + DvrTime / 100
                        //search for current index
                        var vr_p: Long = 0
                        var vr_pi = 0
                        var vr_n: Long = 0
                        var vr_ni = 0
                        var o: Int

                        // Search matching vri
                        o = 0
                        while (o < VriSize * VriItemSize) {
                            var vridatetime: Long = 0

                            //VRI
                            val vri = String(
                                VriList,
                                o,
                                64
                            ).split("\u0000")[0].trim()
                            // Split vri date/time
                            val vriarray = vri.split("-")
                            if (vriarray.size > 1) {
                                try {
                                    vridatetime = java.lang.Long.parseLong(vriarray[1])
                                } catch (e: NumberFormatException) {
                                    vridatetime = 0
                                } catch (e: Exception) {
                                    vridatetime = 0
                                }
                            }

                            if (vridatetime <= searchdatetime) {
                                if (vr_p == 0L) {
                                    vr_p = vridatetime
                                    vr_pi = o
                                } else if (vridatetime > vr_p) {
                                    vr_p = vridatetime
                                    vr_pi = o
                                }
                            } else {
                                if (vr_n == 0L) {
                                    vr_n = vridatetime
                                    vr_ni = o
                                } else if (vridatetime < vr_n) {
                                    vr_n = vridatetime
                                    vr_ni = o
                                }
                            }
                            o += VriItemSize
                        }

                        if (vr_p != 0L) {
                            o = vr_pi
                        } else if (vr_n != 0L) {
                            o = vr_ni
                        } else {
                            o = 0
                        }


                        offset = o

                        var OfficerId = ""
                        var str = String(
                            VriList,
                            offset,
                            64
                        ).split("\u0000")[0].trim()
                        val eVri = mBaseView!!.findViewById<View>(R.id.tag_vri) as EditText
                        eVri?.setText(str)
                        // splite officer ID from vri
                        val strarray = str.split("-")
                        if (strarray.size > 2) {
                            OfficerId = strarray[strarray.size - 1]
                        }
                        offset += 64

                        // incident classification
                        str = String(
                            VriList,
                            offset,
                            32
                        ).split("\u0000")[0].trim()
                        val sIncident = mBaseView!!.findViewById<View>(R.id.tag_incident) as Spinner
                        if (sIncident != null) {
                            val adapter = sIncident.adapter as ArrayAdapter<CharSequence>
                            val pos = adapter.getPosition(str)
                            if (pos < 0) {
                                //                                    adapter.add(str);
                                //                                    pos = adapter.getPosition(str);
                            }
                            sIncident.setSelection(pos)
                        }
                        offset += 32

                        // case number
                        str = String(
                            VriList,
                            offset,
                            64
                        ).split("\u0000")[0].trim()
                        val eCase = mBaseView!!.findViewById<View>(R.id.tag_casenumber) as EditText
                        eCase?.setText(str)
                        offset += 64

                        // Priority
                        str = String(
                            VriList,
                            offset,
                            20
                        ).split("\u0000")[0].trim()
                        val sPrio = mBaseView!!.findViewById<View>(R.id.tag_priority) as Spinner
                        if (sPrio != null) {
                            val adapter = sPrio.adapter as ArrayAdapter<CharSequence>
                            val pos = adapter.getPosition(str)
                            if (pos < 0) {
                                //                                    adapter.add(str);
                                //                                    pos = adapter.getPosition(str);
                            }
                            sPrio.setSelection(pos)
                        }
                        offset += 20

                        // Officer ID
                        val eOfficerId =
                            mBaseView!!.findViewById<View>(R.id.tag_officerid) as EditText
                        eOfficerId?.setText(OfficerId)
                        offset += 32    // skip officer ID

                        // Notes
                        str = String(
                            VriList,
                            offset,
                            255
                        ).split("\u0000")[0].trim()
                        val eNotes = mBaseView!!.findViewById<View>(R.id.tag_notes) as EditText
                        eNotes?.setText(str)

                    }

                }
            })
        }



        return builder.create()
    }

    override fun onDestroyView() {
        mBaseView = null
        mPwProtocol!!.close()
        super.onDestroyView()
    }

    // get DVRTime from dvr
    internal fun getSetCurDvrTime() {
        DvrDate = 20990101
        DvrTime = 0
    }

    internal fun setDvrTime(timestamp: Long) {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        DvrDate = (calendar.get(Calendar.YEAR) * 10000 +
                (calendar.get(Calendar.MONTH) - Calendar.JANUARY + 1) * 100 +
                calendar.get(Calendar.DAY_OF_MONTH)).toLong()
        DvrTime = (calendar.get(Calendar.HOUR_OF_DAY) * 10000 +
                calendar.get(Calendar.MINUTE) * 100 +
                calendar.get(Calendar.SECOND)).toLong()
    }

}
