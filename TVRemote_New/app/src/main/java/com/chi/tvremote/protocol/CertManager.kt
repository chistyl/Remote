package com.chi.tvremote.protocol

import android.content.Context
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.security.MessageDigest
import java.util.Date

class CertManager(private val context: Context) {
    private val file = File(context.filesDir, "tvremote-client.p12")
    private val alias = "tvremote"
    private val password = "tvremote-local-keystore".toCharArray()
    private val pins = context.getSharedPreferences("tvremote_cert_pins", Context.MODE_PRIVATE)

    @Volatile var keyStore: KeyStore? = null
        private set
    @Volatile var certificate: X509Certificate? = null
        private set

    @Synchronized
    fun ensureLoaded() {
        if (keyStore != null) return
        if (file.exists() && load()) return
        generate()
    }

    private fun load(): Boolean = try {
        val ks = KeyStore.getInstance("PKCS12")
        file.inputStream().use { ks.load(it, password) }
        val cert = ks.getCertificate(alias) as? X509Certificate ?: return false
        keyStore = ks
        certificate = cert
        true
    } catch (_: Throwable) { false }

    private fun generate() {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048, SecureRandom())
        val pair = generator.generateKeyPair()
        val name = X500Name("CN=TVRemote")
        val now = System.currentTimeMillis()
        val builder = X509v3CertificateBuilder(
            name, BigInteger(96, SecureRandom()),
            Date(now - 60_000L), Date(now + 10L * 365 * 24 * 60 * 60 * 1000),
            name, SubjectPublicKeyInfo.getInstance(pair.public.encoded)
        )
        val cert = JcaX509CertificateConverter().getCertificate(
            builder.build(JcaContentSignerBuilder("SHA256withRSA").build(pair.private))
        )
        val ks = KeyStore.getInstance("PKCS12")
        ks.load(null, null)
        ks.setKeyEntry(alias, pair.private, password, arrayOf(cert))
        file.outputStream().use { ks.store(it, password) }
        keyStore = ks
        certificate = cert
    }

    fun clientSpkiDer(): ByteArray = requireNotNull(certificate).publicKey.encoded

    fun pinServer(host: String, cert: X509Certificate) {
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
            .joinToString("") { "%02x".format(it) }
        val known = pins.getStringSet("known_server_fingerprints", emptySet()).orEmpty().toMutableSet()
        known.add(fingerprint)
        pins.edit()
            .putString(host, fingerprint)
            .putStringSet("known_server_fingerprints", known)
            .apply()
    }

    fun isServerPinned(host: String, cert: X509Certificate): Boolean {
        val actual = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
            .joinToString("") { "%02x".format(it) }
        val hostPin = pins.getString(host, null)
        if (hostPin != null) {
            return MessageDigest.isEqual(hostPin.toByteArray(), actual.toByteArray())
        }
        // Android TVs commonly receive a new DHCP address after reboot. Trust
        // a certificate already accepted during pairing even when its IP changes.
        val known = pins.getStringSet("known_server_fingerprints", emptySet()).orEmpty()
        return known.any { MessageDigest.isEqual(it.toByteArray(), actual.toByteArray()) }
    }
}
