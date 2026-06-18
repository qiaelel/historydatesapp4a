package ru.qiaelel.historydatesapp

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.http.SslError
import android.os.Bundle
import android.view.View
import android.webkit.*
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import android.util.Log

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var layoutNoInternet: LinearLayout

    private var hasPageError = false

    companion object {
        private const val TARGET_URL = "https://historydatesapp.qiaelel.ru"
        private const val KEY_URL = "current_url"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        layoutNoInternet = findViewById(R.id.layoutNoInternet)

        setupWebView()

        val urlToLoad = savedInstanceState?.getString(KEY_URL) ?: TARGET_URL

        webView.loadUrl(urlToLoad)

        swipeRefresh.setOnRefreshListener {
            hasPageError = false
            hideNoInternet()
            webView.reload()
        }

        findViewById<View>(R.id.btnRetry).setOnClickListener {
            hasPageError = false
            hideNoInternet()
            webView.loadUrl(TARGET_URL)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = false
            displayZoomControls = false
            setSupportZoom(true)
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString = "historydatesapp/1.1"
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                Log.d("WebViewError", "onPageStarted: $url")
                if (url != "about:blank") {
                    hasPageError = false
                    progressBar.visibility = View.VISIBLE
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d("WebViewError", "onPageFinished: $url, hasPageError=$hasPageError")
                progressBar.visibility = View.GONE
                swipeRefresh.isRefreshing = false

                if (hasPageError) {
                    showNoInternet()
                } else {
                    hideNoInternet()
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onReceivedError(
                view: WebView?, errorCode: Int, description: String?, failingUrl: String?
            ) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                Log.d("WebViewError", "onReceivedError (deprecated): errorCode=$errorCode, desc=$description, url=$failingUrl")
                
                if (failingUrl != "about:blank") {
                    hasPageError = true
                    runOnUiThread {
                        view?.loadUrl("about:blank")
                        showNoInternet()
                    }
                }
            }

            override fun onReceivedError(
                view: WebView?, request: WebResourceRequest?, error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                Log.d("WebViewError", "onReceivedError: isForMainFrame=${request?.isForMainFrame}, desc=${error?.description}, errCode=${error?.errorCode}, url=${request?.url}")
                
                if (request?.isForMainFrame == true) {
                    hasPageError = true
                    runOnUiThread {
                        view?.loadUrl("about:blank")
                        showNoInternet()
                    }
                }
            }

            override fun onReceivedHttpError(
                view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?
            ) {
                super.onReceivedHttpError(view, request, errorResponse)
                Log.d("WebViewError", "onReceivedHttpError: isForMainFrame=${request?.isForMainFrame}, statusCode=${errorResponse?.statusCode}, url=${request?.url}")
                if (request?.isForMainFrame == true) {
                    hasPageError = true
                    runOnUiThread {
                        view?.loadUrl("about:blank")
                        showNoInternet()
                    }
                }
            }

            override fun onReceivedSslError(
                view: WebView?, handler: SslErrorHandler?, error: SslError?
            ) {
                Log.d("WebViewError", "onReceivedSslError: url=${error?.url}")
                handler?.cancel()
                hasPageError = true
                runOnUiThread {
                    view?.loadUrl("about:blank")
                    showNoInternet()
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                Log.d("WebViewError", "onProgressChanged: $newProgress")
                if (newProgress == 100) {
                    progressBar.visibility = View.GONE
                } else {
                    progressBar.visibility = View.VISIBLE
                }
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                Log.d("WebViewError", "onReceivedTitle: $title")
                // Fallback check for error page title
                if (title?.contains("Webpage not available", ignoreCase = true) == true || 
                    title?.contains("недоступна", ignoreCase = true) == true) {
                    hasPageError = true
                    showNoInternet()
                }
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                val message = consoleMessage?.message() ?: ""
                Log.d("WebViewError", "Console: $message")
                
                // Catch Service Worker network fetch failures that cause net::ERR_FAILED silently
                if (message.contains("FetchEvent") && message.contains("network error response", ignoreCase = true)) {
                    hasPageError = true
                    runOnUiThread {
                        showNoInternet()
                    }
                }
                if (message.contains("Failed to convert value to 'Response'", ignoreCase = true)) {
                    hasPageError = true
                    runOnUiThread {
                        showNoInternet()
                    }
                }
                return super.onConsoleMessage(consoleMessage)
            }
        }

    }

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun showNoInternet() {
        webView.visibility = View.GONE
        swipeRefresh.visibility = View.GONE
        layoutNoInternet.visibility = View.VISIBLE
        swipeRefresh.isEnabled = false
    }

    private fun hideNoInternet() {
        webView.visibility = View.VISIBLE
        swipeRefresh.visibility = View.VISIBLE
        layoutNoInternet.visibility = View.GONE
        swipeRefresh.isEnabled = true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.url?.let { outState.putString(KEY_URL, it) }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }
}
