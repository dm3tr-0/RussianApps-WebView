package ru.sberwebview

import android.annotation.SuppressLint
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.View
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import ru.sberwebview.databinding.ActivityMainBinding
import javax.net.ssl.HttpsURLConnection


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var webView: WebView

    companion object {
        private const val TARGET_URL = "https://online.sberbank.ru"
    }

    // ─────────── Lifecycle ───────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        installCustomSsl()

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

    // ─────────── SSL Setup ───────────

    /**
     * Replaces the default SSLSocketFactory on [HttpsURLConnection] with one
     * that trusts the bundled Russian root CAs (for Sberbank) **plus** the
     * normal system CAs (for any other resources the page may load).
     * Scoped to the current process only.
     */
    private fun installCustomSsl() {
        val sslContext = SslHelper.createSslContext(this)
        HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.socketFactory)
        HttpsURLConnection.setDefaultHostnameVerifier { hostname, session ->
            HttpsURLConnection.getDefaultHostnameVerifier().verify(hostname, session)
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

    private fun showProgress(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showError(title: String, message: String) {
        webView.visibility = View.GONE
        showProgress(false)
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
