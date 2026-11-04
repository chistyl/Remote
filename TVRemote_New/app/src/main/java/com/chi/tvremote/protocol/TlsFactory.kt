package com.chi.tvremote.protocol

import java.security.SecureRandom
import java.net.InetSocketAddress
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

class TlsFactory(private val certs: CertManager) {
    fun createSocket(host: String, port: Int, allowUnpinned: Boolean = false): SSLSocket {
        certs.ensureLoaded()
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(certs.keyStore, "tvremote-local-keystore".toCharArray())

        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                if (allowUnpinned) return
                require(chain.isNotEmpty() && certs.isServerPinned(host, chain[0])) {
                    "電視憑證與配對紀錄不符，請重新配對"
                }
            }
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }

        val context = SSLContext.getInstance("TLSv1.2")
        context.init(kmf.keyManagers, arrayOf(trustManager), SecureRandom())
        return (context.socketFactory.createSocket() as SSLSocket).apply {
            useClientMode = true
            enabledProtocols = arrayOf("TLSv1.2")
            soTimeout = 15_000
            connect(InetSocketAddress(host, port), 8_000)
            startHandshake()
        }
    }
}
