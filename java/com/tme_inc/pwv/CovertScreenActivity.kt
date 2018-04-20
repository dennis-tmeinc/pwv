package com.tme_inc.pwv

import android.app.Activity
import android.os.Bundle
import android.view.View
import kotlinx.android.synthetic.main.activity_covert_screen.*

/*
 * Covert screen
 * Display a black screen, quit on touch.
 */
class CovertScreenActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_covert_screen)

        // Set up the user interaction to manually show or hide the system UI.
        fullscreen_content?.setOnClickListener { finish() }
        fullscreen_content?.systemUiVisibility = (View.SYSTEM_UI_FLAG_LOW_PROFILE
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)

        // Hide UI bar
        actionBar?.hide()
        window.attributes.screenBrightness = 0.0f
    }

    override fun onStop() {
        super.onStop()
        finish()
    }
}
