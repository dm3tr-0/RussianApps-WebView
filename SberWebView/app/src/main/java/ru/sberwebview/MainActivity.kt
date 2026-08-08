package ru.sberwebview

import android.annotation.SuppressLint
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import ru.sberwebview.databinding.ActivityMainBinding


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var webView: WebView

    companion object {
        private const val TARGET_URL = "https://online.sberbank.ru"
    }

    // ─────────── Lifecycle ───────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // SSL обрабатывается через network_security_config.xml —
        // никаких вызовов в коде не нужно, WebView сам подхватит.

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupEdgeToEdge()

        webView = binding.webView
        setupWebView()

        binding.retryButton.setOnClickListener {
            if (isOnline()) {
                binding.errorView.visibility = View.GONE
                webView.visibility = View.VISIBLE
                webView.loadUrl(TARGET_URL)
            }
        }

        if (isOnline()) {
            webView.loadUrl(TARGET_URL)
        } else {
            showError(
                getString(R.string.error_no_connection),
                getString(R.string.error_ssl_message)
            )
        }
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    // ─────────── WebView ───────────

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            userAgentString = userAgentString.replace("; wv", "")
            useWideViewPort = true
            loadWithOverviewMode = true
            allowFileAccess = false
            allowContentAccess = true
            cacheMode = WebSettings.LOAD_DEFAULT
        }

        webView.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url.toString()
                return if (url.startsWith("tel:") ||
                    url.startsWith("mailto:") ||
                    url.startsWith("market:")
                ) {
                    try {
                        startActivity(Intent.parseUri(url, 0))
                    } catch (_: Exception) { /* ignore */ }
                    true
                } else {
                    false
                }
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: WebResourceResponse
            ) {
                super.onReceivedHttpError(view, request, errorResponse)
                if (request.isForMainFrame) {
                    showError(
                        getString(R.string.error_ssl_title),
                        "HTTP ${errorResponse.statusCode}"
                    )
                }
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                super.onReceivedError(view, request, error)
                if (request.isForMainFrame) {
                    showError(
                        getString(R.string.error_no_connection),
                        error.description?.toString() ?: ""
                    )
                }
            }
        }

        webView.webChromeClient = WebChromeClient()
    }

    // ─────────── UI helpers ───────────

    private fun setupEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, binding.root)
        controller.isAppearanceLightStatusBars = true
        controller.isAppearanceLightNavigationBars = true
    }

    private fun showError(title: String, message: String) {
        webView.visibility = View.GONE
        binding.progressBar.visibility = View.GONE
        binding.errorView.visibility = View.VISIBLE
        binding.errorTitle.text = title
        binding.errorMessage.text = message
    }

    // ─────────── Network ───────────

    private fun isOnline(): Boolean {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val cap = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
