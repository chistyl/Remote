package com.chi.tvremote.adb

import android.content.Context
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Verifies and extracts the pinned official scrcpy server release bundled by CI.
 * The release binary is Apache-2.0 licensed; see SCRCPY-LICENSE.txt in assets.
 */
internal object ScrcpyServerAsset {
    const val VERSION = "5.0.1"
    const val REMOTE_PATH = "/data/local/tmp/scrcpy-server.jar"
    const val SHA256 = "764eb6f79811d5211fe9df341120882ba9994c7a61b897d7bf3fb662e53bc536"
    private const val ASSET_NAME = "scrcpy-server.jar"

    @Throws(IOException::class)
    fun extractVerified(context: Context): File {
        val destination = File(context.filesDir, "scrcpy/$ASSET_NAME")
        if (destination.isFile && sha256(destination.readBytes()) == SHA256) return destination
        val parent = destination.parentFile
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw IOException("無法建立 scrcpy 暫存資料夾")
        }
        val temporary = File(parent, "$ASSET_NAME.tmp")
        try {
            context.assets.open(ASSET_NAME).use { input ->
                temporary.outputStream().use { output -> input.copyTo(output) }
            }
            if (sha256(temporary.readBytes()) != SHA256) {
                throw IOException("scrcpy Server 完整性驗證失敗")
            }
            if (destination.exists() && !destination.delete()) {
                throw IOException("無法更新 scrcpy Server 檔案")
            }
            if (!temporary.renameTo(destination)) {
                throw IOException("無法保存 scrcpy Server 檔案")
            }
            return destination
        } catch (e: IOException) {
            temporary.delete()
            throw e
        }
    }

    internal fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
