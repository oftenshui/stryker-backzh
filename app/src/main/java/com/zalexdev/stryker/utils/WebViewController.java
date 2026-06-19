package com.zalexdev.stryker.utils;

import android.webkit.ConsoleMessage;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public abstract class WebViewController extends WebViewClient {

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
        view.loadUrl(url);
        return true;
    }

    public abstract boolean onConsoleMessage(ConsoleMessage consoleMessage);
}