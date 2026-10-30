package com.chi.tvremote.protocol

import android.content.Context
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Security
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/** One app-scoped RSA identity reused for all paired TVs. The private key stays in app storage. */
internal class ClientIdentity private constructor(context: Context) {
    private val file = File(context.filesDir, "atv_remote_client.p12")
    private val password = "chi-tv-remote-local".toCharArray()
    lateinit var keyPair: KeyPair
        private set
    lateinit var certificate: X509Certificate
        private set
    private lateinit var keyStore: KeyStore

    init { loadOrCreate() }

    private fun loadOrCreate() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) Security.addProvider(BouncyCastleProvider())
        keyStore = KeyStore.getInstance("PKCS12")
        if (file.exists()) {
            file.inputStream().use { keyStore.load(it, password) }
            val alias = keyStore.aliases().toList().firstOrNull() ?: error("Client identity is empty")
            val key = keyStore.getKey(alias, password) as java.security.PrivateKey
            certificate = keyStore.getCertificate(alias) as X509Certificate
            keyPair = KeyPair(certificate.publicKey, key)
        } else {
            val generator = KeyPairGenerator.getInstance("RSA")
            generator.initialize(2048)
            keyPair = generator.generateKeyPair()
            val now = System.currentTimeMillis()
            val subject = X500Name("CN=CHI TV Remote")
            val builder = JcaX509v3CertificateBuilder(
                subject, BigInteger.valueOf(now), Date(now - 60_000), Date(now + 10L * 365 * 24 * 60 * 60 * 1000),
                subject, keyPair.public
            )
            val signer = JcaContentSignerBuilder("SHA256withRSA").setProvider(BouncyCastleProvider.PROVIDER_NAME).build(keyPair.private)
            certificate = JcaX509CertificateConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME).getCertificate(builder.build(signer))
            certificate.checkValidity()
            certificate.verify(keyPair.public)
            keyStore.load(null, password)
            keyStore.setKeyEntry("chi-tv-remote", keyPair.private, password, arrayOf(certificate))
            file.outputStream().use { keyStore.store(it, password) }
        }
    }

    fun keyManagers() = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).run {
        init(keyStore, password); keyManagers
    }

    companion object {
        @Volatile private var cached: ClientIdentity? = null

        /** One process-wide identity prevents concurrent key generation and PKCS#12 writes. */
        fun get(context: Context): ClientIdentity = cached ?: synchronized(this) {
            cached ?: ClientIdentity(context.applicationContext).also { cached = it }
        }

        fun trustManagers(): Array<TrustManager> = arrayOf(object : X509TrustManager {
            override fun getAcceptedIssuers() = emptyArray<java.security.cert.X509Certificate>()
            override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) = Unit
            override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) = Unit
        })
    }
}
