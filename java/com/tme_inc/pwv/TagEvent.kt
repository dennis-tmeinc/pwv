package com.tme_inc.pwv

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Spinner

import java.util.Arrays
import java.util.Calendar

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
        var spinner: Spinner?
        spinner = findViewById<View>(R.id.tag_incident) as Spinner
        if (spinner != null) {
            val adapter = ArrayAdapter.createFromResource(
                this,
                R.array.tag_incident_list, android.R.layout.simple_spinner_item
            )
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinner.adapter = adapter
        }

        // init priority list
        spinner = findViewById<View>(R.id.tag_priority) as Spinner
        if (spinner != null) {
            val adapter = ArrayAdapter.createFromResource(
                this,
                R.array.tag_priority_list, android.R.layout.simple_spinner_item
            )
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinner.adapter = adapter
        }

        // init PW event processor
        //
        mPwProtocol = PWProtocol()
        mPwProtocol!!.GetVri { result -> onTagPWEvent(result) }

        var button = findViewById<View>(R.id.button_tag) as Button
        button.setOnClickListener { onTagEventClick() }

        button = findViewById<View>(R.id.button_cancel) as Button
        button.setOnClickListener { onCancelClick() }

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
        val id = item.itemId


        return if (id == R.id.action_settings) {
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

                    // VRI time
                    var vridatetime: Long = getVriTime( String(
                        VriList,
                        o,
                        64
                    ))

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
                val eVri = findViewById<View>(R.id.tag_vri) as EditText
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
                val sIncident = findViewById<View>(R.id.tag_incident) as Spinner
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
                val eCase = findViewById<View>(R.id.tag_casenumber) as EditText
                eCase?.setText(str)
                offset += 64

                // Priority
                str = String(
                    VriList,
                    offset,
                    20
                ).split("\u0000")[0].trim()
                val sPrio = findViewById<View>(R.id.tag_priority) as Spinner
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
                val eOfficerId = findViewById<View>(R.id.tag_officerid) as EditText
                eOfficerId?.setText(OfficerId)
                offset += 32    // skip officer ID

                // Notes
                str = String(
                    VriList,
                    offset,
                    255
                ).split("\u0000")[0].trim()
                val eNotes = findViewById<View>(R.id.tag_notes) as EditText
                eNotes?.setText(str)

            }

        }
        return
    }

    protected fun onTagEventClick() {
        if (VriItemSize > 0) {
            val vri = ByteArray(VriItemSize)
            Arrays.fill(vri, ' '.toByte())

            var str: String
            var len: Int
            var offset = 0

            // Vri
            val eVri = findViewById<View>(R.id.tag_vri) as EditText
            if (eVri != null) {
                val bVri = eVri.text.toString().toByteArray()
                len = bVri.size
                if (len > 64) len = 64
                System.arraycopy(bVri, 0, vri, offset, len)
            }
            offset += 64

            // incident classification
            val sIncident = findViewById<View>(R.id.tag_incident) as Spinner
            if (sIncident != null) {
                str = sIncident.selectedItem as String
                val bIncident = str.toByteArray()
                len = bIncident.size
                if (len > 32) len = 32
                System.arraycopy(bIncident, 0, vri, offset, len)
            }
            offset += 32

            // case number
            val eCase = findViewById<View>(R.id.tag_casenumber) as EditText
            if (eCase != null) {
                val bCase = eCase.text.toString().toByteArray()
                len = bCase.size
                if (len > 64) len = 64
                System.arraycopy(bCase, 0, vri, offset, len)
            }
            offset += 64

            // Priority
            val sPrio = findViewById<View>(R.id.tag_priority) as Spinner
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
            val eNotes = findViewById<View>(R.id.tag_notes) as EditText
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
        finish()
    }

    protected fun onCancelClick() {
        finish()
    }

}
