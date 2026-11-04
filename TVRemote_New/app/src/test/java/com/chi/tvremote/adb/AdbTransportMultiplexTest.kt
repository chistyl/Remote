package com.chi.tvremote.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class AdbTransportMultiplexTest {
    @Test
    fun concurrentShellCommandsReceiveTheirOwnInterleavedResponses() {
        val server = ServerSocket(0)
        val executor = Executors.newSingleThreadExecutor()
        val serverResult = executor.submit(Callable {
            server.accept().use { peer ->
                peer.soTimeout = 5000
                val input = DataInputStream(peer.getInputStream())
                val output = DataOutputStream(peer.getOutputStream())
                val opens = listOf(readPacket(input), readPacket(input))
                assertTrue(opens.all { it.command == A_OPEN })
                val first = opens[0]
                val second = opens[1]

                // Open both logical streams, then interleave their payloads.
                output.write(AdbPacketCodec.encode(A_OKAY, 501, first.arg0, byteArrayOf()))
                output.write(AdbPacketCodec.encode(A_OKAY, 502, second.arg0, byteArrayOf()))
                output.write(AdbPacketCodec.encode(A_WRTE, 502, second.arg0, "second-result".toByteArray()))
                output.write(AdbPacketCodec.encode(A_WRTE, 501, first.arg0, "first-result".toByteArray()))
                output.write(AdbPacketCodec.encode(A_CLSE, 502, second.arg0, byteArrayOf()))
                output.write(AdbPacketCodec.encode(A_CLSE, 501, first.arg0, byteArrayOf()))
                output.flush()
                true
            }
        })

        val clientSocket = Socket("127.0.0.1", server.localPort)
        val transport = AdbTransport.fromConnectedSocketForTest(clientSocket)
        val workers = Executors.newFixedThreadPool(2)
        try {
            val first = workers.submit<String> { transport.shell("echo first", 5000) }
            val second = workers.submit<String> { transport.shell("echo second", 5000) }
            val results = setOf(first.get(6, TimeUnit.SECONDS), second.get(6, TimeUnit.SECONDS))
            assertEquals(setOf("first-result", "second-result"), results)
            assertTrue(serverResult.get(2, TimeUnit.SECONDS))
        } finally {
            transport.close()
            workers.shutdownNow()
            executor.shutdownNow()
            server.close()
        }
    }

    private data class Frame(val command: Int, val arg0: Int, val arg1: Int)

    private fun readPacket(input: DataInputStream): Frame {
        val header = ByteArray(24)
        input.readFully(header)
        val fields = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val command = fields.int
        val arg0 = fields.int
        val arg1 = fields.int
        val length = fields.int
        val payload = ByteArray(length)
        input.readFully(payload)
        AdbPacketCodec.decode(header, payload)
        return Frame(command, arg0, arg1)
    }

    companion object {
        private const val A_OPEN = 0x4e45504f
        private const val A_OKAY = 0x59414b4f
        private const val A_WRTE = 0x45545257
        private const val A_CLSE = 0x45534c43
    }
}
