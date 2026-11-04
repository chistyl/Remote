package com.chi.tvremote.adb

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets

/**
 * Encoder for the scrcpy 5.x device-control channel.
 * All multibyte control fields use network (big-endian) byte order.
 */
internal object ScrcpyControlProtocol {
    const val TYPE_INJECT_TOUCH_EVENT = 2
    const val TYPE_INJECT_SCROLL_EVENT = 3
    const val TYPE_UHID_CREATE = 12
    const val TYPE_UHID_INPUT = 13
    const val TYPE_UHID_DESTROY = 14

    const val ACTION_DOWN = 0
    const val ACTION_UP = 1
    const val ACTION_MOVE = 2
    const val ACTION_HOVER_MOVE = 7

    fun uhidCreate(id: Int, vendorId: Int, productId: Int, name: String, reportDescriptor: ByteArray): ByteArray {
        requireU16(id, "id")
        requireU16(vendorId, "vendorId")
        requireU16(productId, "productId")
        val nameBytes = name.toByteArray(StandardCharsets.UTF_8)
        require(nameBytes.size <= 255) { "UHID name exceeds 255 bytes" }
        require(reportDescriptor.size <= 65535) { "UHID report descriptor exceeds 65535 bytes" }
        return encode { out ->
            out.writeByte(TYPE_UHID_CREATE)
            out.writeShort(id)
            out.writeShort(vendorId)
            out.writeShort(productId)
            out.writeByte(nameBytes.size)
            out.write(nameBytes)
            out.writeShort(reportDescriptor.size)
            out.write(reportDescriptor)
        }
    }

    fun uhidInput(id: Int, data: ByteArray): ByteArray {
        requireU16(id, "id")
        require(data.size <= 65535) { "UHID input exceeds 65535 bytes" }
        return encode { out ->
            out.writeByte(TYPE_UHID_INPUT)
            out.writeShort(id)
            out.writeShort(data.size)
            out.write(data)
        }
    }

    fun uhidDestroy(id: Int): ByteArray {
        requireU16(id, "id")
        return encode { out ->
            out.writeByte(TYPE_UHID_DESTROY)
            out.writeShort(id)
        }
    }

    /** Scrcpy touch injection format; coordinates are relative to the supplied display size. */
    fun injectTouch(
        action: Int,
        pointerId: Long,
        x: Int,
        y: Int,
        screenWidth: Int,
        screenHeight: Int,
        pressure: Float = 1f,
        actionButton: Int = 0,
        buttons: Int = 0
    ): ByteArray {
        require(action in 0..255) { "Invalid MotionEvent action" }
        require(screenWidth in 1..65535 && screenHeight in 1..65535) { "Invalid display dimensions" }
        require(x >= 0 && x <= Int.MAX_VALUE && y >= 0 && y <= Int.MAX_VALUE)
        require(pressure.isFinite() && pressure in 0f..1f)
        return encode { out ->
            out.writeByte(TYPE_INJECT_TOUCH_EVENT)
            out.writeByte(action)
            out.writeLong(pointerId)
            out.writeInt(x)
            out.writeInt(y)
            out.writeShort(screenWidth)
            out.writeShort(screenHeight)
            out.writeShort((pressure * 65535f).toInt())
            out.writeInt(actionButton)
            out.writeInt(buttons)
        }
    }

    /** Scroll values are signed 16-bit fixed-point fractions of 16. */
    fun injectScroll(
        x: Int, y: Int, screenWidth: Int, screenHeight: Int,
        horizontal: Float, vertical: Float, buttons: Int = 0
    ): ByteArray {
        require(screenWidth in 1..65535 && screenHeight in 1..65535) { "Invalid display dimensions" }
        require(x >= 0 && y >= 0)
        require(horizontal.isFinite() && horizontal in -16f..16f)
        require(vertical.isFinite() && vertical in -16f..16f)
        return encode { out ->
            out.writeByte(TYPE_INJECT_SCROLL_EVENT)
            out.writeInt(x)
            out.writeInt(y)
            out.writeShort(screenWidth)
            out.writeShort(screenHeight)
            out.writeShort((horizontal / 16f * 32768f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()))
            out.writeShort((vertical / 16f * 32768f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()))
            out.writeInt(buttons)
        }
    }

    private fun requireU16(value: Int, field: String) {
        require(value in 0..65535) { "$field must be an unsigned 16-bit value" }
    }

    private inline fun encode(block: (DataOutputStream) -> Unit): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use(block)
        return bytes.toByteArray()
    }
}
