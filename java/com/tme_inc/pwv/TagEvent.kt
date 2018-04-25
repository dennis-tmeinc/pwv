package com.tme_inc.pwv

import android.app.Activity
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import kotlinx.android.synthetic.main.activity_tag_event.*

import java.util.Arrays
import java.util.Calendar
import kotlin.math.max
import kotlin.math.min

class TagEvent : Activity() {

    private var mPwProtocol: PWProtocol? = null
    private var VriItemSize = 0

    // DVR Date/Time in BCD
    private var DvrDate: Long = 0
    private var DvrTime: Long = 0


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val timestamp = intent.getLongExtra("DvrTime", 0L)

        val calendar = Calendar.getInstance()
        if (timestamp > 0L) {
            calendar.clear()
            calendar.timeInMillis = timestamp
        }
        DvrDate = (calendar.get(Calendar.YEAR) * 10000 +
                (calendar.get(Calendar.MONTH) - Calendar.JANUARY + 1) * 100 +
                calendar.get(Calendar.DAY_OF_MONTH)).toLong()
        DvrTime = (calendar.get(Calendar.HOUR_OF_DAY) * 10000 +
                calendar.get(Calendar.MINUTE) * 100 +
                calendar.get(Calendar.SECOND)).toLong()


        setContentView(R.layout.activity_tag_event)

        // init incident list
        var adapter = ArrayAdapter.createFromResource(
            this,
            R.array.tag_incident_list, android.R.layout.simple_spinner_item
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        tag_incident.adapter = adapter

        // init priority list
        adapter = ArrayAdapter.createFromResource(
            this,
            R.array.tag_priority_list, android.R.layout.simple_spinner_item
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        tag_priority.adapter = adapter

        // init PW event processor
        //
        mPwProtocol = PWProtocol()
        mPwProtocol!!.getVri {
            onTagPWEvent(it)
        }

        button_tag.setOnClickListener {
            onTagEventClick()
        }

        button_cancel.setOnClickListener {
            finish()
        }

    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_tag_event, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return if (item.itemId == R.id.action_settings) {
            true
        } else super.onOptionsItemSelected(item)

    }

    private fun getVriTime( vri : String ) : Long {
        //trim VRI
        val tVri = vri.split("\u0000")[0].trim()
        val vriarray = tVri.split("-")
        if (vriarray.size > 1) {
            var vriTime: Long = 0
            try {
                vriTime = vriarray[vriarray.size - 1].toLong()        // this one could be police ID
                if (vriTime > 1000000000 && vriTime < 5000000000) {
                    return vriTime
                }
            } catch (e: NumberFormatException) {
                vriTime = 0
            } catch (e: Exception) {
                vriTime = 0
            }

            try {
                vriTime = vriarray[vriarray.size - 2].toLong()
                if (vriTime > 1000000000 && vriTime < 5000000000) {
                    return vriTime
                }
            } catch (e: NumberFormatException) {
                vriTime = 0
            } catch (e: Exception) {
                vriTime = 0
            }
            return vriTime
        }
        return 0L
    }

    protected fun onTagPWEvent(result: Bundle?) {
        if (result != null) {
            VriItemSize = result.getInt("VriItemSize", 0)
            val VriSize = result.getInt("VriListSize", 0)
            val VriList = result.getByteArray("VriList")
            if (VriSize > 0 && VriItemSize > 0 && VriList != null) {

                val searchdatetime = (DvrDate - 20000000) * 10000 + DvrTime / 100
                //search for current index
                var vr_p: Long = 0
                var vr_pi = 0
                var vr_n: Long = 0
                var vr_ni = 0

                // extension of vri
                fun ByteArray.getVriItem(offset: Int, len: Int ) : String {
                    return String(
                        this,
                        offset,
                        len
                    ).split("\u0000")[0].trim()
                }

                // Search matching vri
                var offset = 0
                while (offset < VriSize * VriItemSize) {

                    // VRI time
                    var vridatetime: Long = getVriTime( String(
                        VriList,
                        offset,
                        64
                    ))

                    if (vridatetime <= searchdatetime) {
                        if (vr_p == 0L) {
                            vr_p = vridatetime
                            vr_pi = offset
                        } else if (vridatetime > vr_p) {
                            vr_p = vridatetime
                            vr_pi = offset
                        }
                    } else {
                        if (vr_n == 0L) {
                            vr_n = vridatetime
                            vr_ni = offset
                        } else if (vridatetime < vr_n) {
                            vr_n = vridatetime
                            vr_ni = offset
                        }
                    }
                    offset += VriItemSize
                }

                offset = if (vr_p != 0L) {
                    vr_pi
                } else if (vr_n != 0L) {
                    vr_ni
                } else {
                    0
                }

                var OfficerId = ""

                var str = VriList.getVriItem(offset, 64)
                tag_vri.setText(str)
                // splite officer ID from vri
                val strarray = str.split("-")
                if (strarray.size > 2) {
                    OfficerId = strarray[strarray.size - 1]
                }
                offset += 64

                var apos = 0

                // incident classification
                str = VriList.getVriItem(offset, 32)
                val incident_adapter = tag_incident.adapter as ArrayAdapter<CharSequence>
                apos = incident_adapter.getPosition(str)
                if (apos < 0) {
                    //                                    adapter.add(str);
                    //                                    pos = adapter.getPosition(str);
                    apos = 0
                }
                tag_incident.setSelection(apos)
                offset += 32

                // case number
                tag_casenumber.setText(VriList.getVriItem(offset, 64))
                offset += 64

                // Priority
                str = VriList.getVriItem(offset, 20)
                val prio_adapter = tag_priority.adapter as ArrayAdapter<CharSequence>
                apos = prio_adapter.getPosition(str)
                if (apos < 0) {
                    //                                    adapter.add(str);
                    //                                    pos = adapter.getPosition(str);
                    apos = 0
                }
                tag_priority.setSelection(apos)
                offset += 20

                // Officer ID
                tag_officerid.setText(OfficerId)
                offset += 32    // skip officer ID

                // Notes
                tag_notes.setText(VriList.getVriItem(offset, 255))

            }

        }
        return
    }

    private fun onTagEventClick() {
        if (VriItemSize > 0) {
            val vri = ByteArray(VriItemSize) {
                ' '.toByte()
            }

            fun ByteArray.setVriItem( sData : String, offset: Int, len: Int) : Int {
                val bData = sData.toByteArray()
                val blen = min( bData.size, len )
                System.arraycopy(bData, 0, this, offset, blen)
                return len
            }

            var offset = 0

            // Vri
            offset += vri.setVriItem(tag_vri.text.toString(), offset, 64)

            // incident classification
            offset += vri.setVriItem(tag_incident.selectedItem as String, offset, 32)

            // case number
            offset += vri.setVriItem(tag_casenumber.text.toString(), offset, 64)

            // Priority
            offset += vri.setVriItem(tag_priority.selectedItem.toString(), offset, 20)

            // Officer ID
            offset += 32    // skip officer ID

            // Notes
            vri.setVriItem(tag_notes.text.toString(), offset, VriItemSize - offset)

            // set VRI
            if (mPwProtocol != null) {
                mPwProtocol!!.setVri(vri)
            }

        }
        finish()
    }

}
