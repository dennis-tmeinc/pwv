package com.tme_inc.pwv

import android.app.AlertDialog
import android.app.Dialog
import android.app.DialogFragment
import android.os.Bundle

/**
 * Created by dennis on 9/30/16.
 */
class AboutDialogFragment : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.app_label)
            .setIcon(R.drawable.icon)
            .setMessage("Version: " + BuildConfig.VERSION_NAME)
            .create()
}
