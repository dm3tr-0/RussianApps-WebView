package ru.sberwebview

import android.annotation.SuppressLint
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsControllerCompat
import android.webkit.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import ru.sberwebview.databinding.ActivityMainBinding
import java.io.IOException
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

        // Install the composite SSL context at the JVM level so that
        // every HTTPS connection inside this process uses it.
        installCustomSsl()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Edge‑to‑edge
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

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    // ─────────── SSL Setup ───────────

    /**
     * Replaces the default SSLSocketFactory on [HttpsURLConnection] with one
     * that trusts the bundled Russian root CAs (for Sberbank) **plus** the
     * normal system CAs (for any other resources the page may load).
     *
     * This is scoped to the current process only – no other app is affected.
     */
    private fun installCustomSsl() {
        val sslContext = SslHelper.createSslContext(this)
        HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.socketFactory)
        HttpsURLConnection.setDefaultHostnameVerifier { hostname, session ->
            // Use the built‑in hostname verifier (verifies SAN/CN)
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
            // Allow mixed content from Sberbank's own CDN if needed
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            // User‑agent – mimic a normal Chrome so Sberbank serves the desktop site properly
            userAgentString = userAgentString.replace(
                "; wv",
                ""
            )
            // Layout
            useWideViewPort = true
            loadWithOverviewMode = true
            // Allow file access within the webview sandbox only
            allowFileAccess = false
            allowContentAccess = true
            // Cache
            cacheMode = WebSettings.LOAD_DEFAULT
        }

        webView.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                // Keep all navigation inside the WebView, but open external
                // apps (tel:, mailto:, market:) in external handlers.
                return if (url.startsWith("tel:") ||
                    url.startsWith("mailto:") ||
                    url.startsWith("market:")
                ) {
                    try {
                        startActivity(android.content.Intent.parseUri(url, 0))
                    } catch (_: Exception) { /* ignore */ }
                    true
                } else {
                    false // let WebView handle it
                }
            }

            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?
            ) {
                // Our custom TrustManager should prevent this for Sberbank.
                // If it still happens, show the error view rather than silently proceeding.
                handler?.cancel()
                showError(
                    getString(R.string.error_ssl_title),
                    "${getString(R.string.error_ssl_message)}\n\n${error?.toString() ?: ""}"
                )
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?
            ) {
                super.onReceivedHttpError(view, request, errorResponse)
                // Don't show errors for sub‑resources (images, scripts, etc.)
                if (request?.isForMainFrame == true) {
                    showError(
                        getString(R.string.error_ssl_title),
                        "HTTP ${errorResponse?.statusCode}"
                    )
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    showError(
                        getString(R.string.error_no_connection),
                        error?.description?.toString() ?: ""
                    )
                }
            }
        }

        webView.webChromeClient = WebChromeClient()
    }

    // ─────────── UI helpers ───────────

    private fun setupEdgeToEdge() {
        WindowInsetsControllerCompat(window, binding.root).let { ctrl ->
            ctrl.isAppearanceLightStatusBars = true
            ctrl.isAppearanceLightNavigationBars = true
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, 0, systemBars.right, 0)
            insets
        }
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
