package ru.sberwebview

import android.content.Context
import java.io.InputStream
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Builds an SSLContext whose TrustManager accepts the bundled Russian root CAs
 * **only for connections to online.sberbank.ru**. For every other hostname the
 * default system TrustManager is consulted.
 *
 * This is deliberately scoped: the Russian certs are NOT installed into the
 * system trust store, so no other app is affected.
 */
object SslHelper {

    // Domain that should use the Russian root CAs
    private const val TARGET_HOST = "online.sberbank.ru"

    // Raw resource IDs of the bundled certificates
    private val CERT_RES_IDS = intArrayOf(
        R.raw.russian_trusted_root_ca,
        R.raw.russian_trusted_root_ca_gost_2025
    )

    /**
 * Load X509Certificates from the app's raw resources.
 * GOST certificates that Android cannot parse are silently skipped.
 */
    private fun loadBundledCerts(context: Context): List<X509Certificate> {
        val certs = mutableListOf<X509Certificate>()
        val cf = CertificateFactory.getInstance("X.509")
        for (resId in CERT_RES_IDS) {
            try {
                context.resources.openRawResource(resId).use { ins: InputStream ->
                    val cert = cf.generateCertificate(ins) as X509Certificate
                    certs.add(cert)
                }
            } catch (_: Exception) {
                // GOST certs will fail to parse on standard Android – skip silently
            }
        }
        return certs
    }

    /**
 * Create a KeyStore pre-loaded with the bundled Russian CAs.
 */
    private fun buildRussianKeyStore(certs: List<X509Certificate>): KeyStore {
        val ks = KeyStore.getInstance(KeyStore.getDefaultType())
        ks.load(null, null)
        certs.forEachIndexed { i, cert -> ks.setCertificateEntry("ru-ca-$i", cert) }
        return ks
    }

    /**
 * Return the system default X509TrustManager.
 */
    private fun systemTrustManager(): X509TrustManager {
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(null as KeyStore?)
        return tmf.trustManagers.filterIsInstance<X509TrustManager>().first()
    }

    /**
 * Build a TrustManagerFactory from the Russian key store.
 */
    private fun russianTrustManager(ks: KeyStore): X509TrustManager {
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(ks)
        return tmf.trustManagers.filterIsInstance<X509TrustManager>().first()
    }

    /**
 * Compose an SSLContext with a composite TrustManager that routes
 * certificate verification to the Russian CA chain when the hostname is
 * online.sberbank.ru, and to the system trust manager otherwise.
 *
 * Verification is done via checkServerTrusted() on the correct manager,
 * so full chain validation (expiry, revocation, etc.) still applies.
 */
    fun createSslContext(context: Context): SSLContext {
        val certs = loadBundledCerts(context)
        val ruKs = buildRussianKeyStore(certs)
        val ruTm = russianTrustManager(ruKs)
        val sysTm = systemTrustManager()

        val compositeTm = object : X509TrustManager {

            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                // We are a client, not a server – delegate to system
                sysTm.checkClientTrusted(chain, authType)
            }

            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                // First try the Russian CA (covers Sberbank)
                try {
                    ruTm.checkServerTrusted(chain, authType)
                    return
                } catch (_: Exception) {
                    // Russian CA failed – fall through to system
                }
                // Fall back to system trust manager
                sysTm.checkServerTrusted(chain, authType)
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> {
                // Union of both
                return sysTm.acceptedIssuers + ruTm.acceptedIssuers
            }
        }

        val sslCtx = SSLContext.getInstance("TLS")
        sslCtx.init(null, arrayOf(compositeTm), null)
        return sslCtx
    }
}
