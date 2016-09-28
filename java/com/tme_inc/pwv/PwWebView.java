package com.tme_inc.pwv;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.net.URI;
import java.net.URISyntaxException;

public class PwWebView extends Activity {

    private String m_originalUrl = null ;
    private WebView m_webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pw_web_view);

        m_webView = (WebView) findViewById(R.id.webview);

        WebSettings webSettings = m_webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        m_webView.setWebViewClient(new WebViewClient(){
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                try {
                    URI uri = new URI(url) ;
                    String host = uri.getHost();
                    uri = new URI( m_originalUrl);
                    String ohost = uri.getHost() ;
                    if( host.equals(ohost) ) {
                        view.loadUrl(url);
                        return true ;
                    }
                } catch (URISyntaxException e) {
                    e.printStackTrace();
                }
                return super.shouldOverrideUrlLoading(view, url);
            }
        });

        Intent intent = getIntent();
        String title = intent.getStringExtra("TITLE") ;
        if( title!=null ) {
            setTitle(title);
        }

        if( savedInstanceState!=null ) {
            m_webView.restoreState(savedInstanceState) ;
        }
        else {
            String url = intent.getStringExtra("URL") ;
            if( url != null ) {
                m_originalUrl = url ;
                m_webView.loadUrl(url);
            }
        }
    }

    @Override
    public void onBackPressed() {
        if(m_webView.canGoBack()) {
            m_webView.goBack();
        }
        else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        m_webView.saveState(outState) ;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_pw_web_view, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_close) {
            finish();
            return true;
        }
        else {
            return super.onOptionsItemSelected(item);
        }
    }

}
