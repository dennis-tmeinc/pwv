package com.tme_inc.pwv

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.webkit.*
import android.widget.Toast

import java.net.URI
import java.net.URISyntaxException

// kotlin imports
import kotlinx.android.synthetic.main.activity_pw_web_view.*

class PwWebView : Activity() {

    private var m_originalUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pw_web_view)

        val webSettings = webview!!.settings
        webSettings.javaScriptEnabled = true
        webview.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                try {
                    var uri = URI(url)
                    val host = uri.host
                    uri = URI(m_originalUrl!!)
                    val ohost = uri.host
                    if (host == ohost) {
                        view.loadUrl(url)
                        return true
                    }
                } catch (e: URISyntaxException) {
                    e.printStackTrace()
                }

                return super.shouldOverrideUrlLoading(view, url)
            }
        }

        val title = intent!!.getStringExtra("TITLE")
        if (title != null) {
            setTitle(title)
        }

        if (savedInstanceState != null) {
            webview.restoreState(savedInstanceState)
        } else {
            val url = intent!!.getStringExtra("URL")
            if (url != null) {
                m_originalUrl = url
                webview.loadUrl(url)
            }
        }
    }

    override fun onBackPressed() {
        if (webview.canGoBack()) {
            webview.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webview.saveState(outState)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_pw_web_view, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_close) {
            finish()
            return true
        } else {
            return super.onOptionsItemSelected(item)
        }
    }

}
