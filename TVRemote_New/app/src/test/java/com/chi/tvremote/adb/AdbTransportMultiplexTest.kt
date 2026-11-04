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
                val first = opens.single { String(it.payload).contains("echo first") }
                val second = opens.single { String(it.payload).contains("echo second") }

                // Open both logical streams, then interleave their payloads.
                output.write(AdbPacketCodec.encode(A_OKAY, 501, first.arg0, byteArrayOf()))
                output.write(AdbPacketCodec.encode(A_OKAY, 502, second.arg0, byteArrayOf()))
                output.write(AdbPacketCodec.encode(A_WRTE, 502, second.arg0, "second-result".toByteArray()))
                output.write(AdbPacketCodec.encode(A_WRTE, 501, first.arg0, "first-result".toByteArray()))
                output.write(AdbPacketCodec.encode(A_CLSE, 502, second.arg0, byteArrayOf()))
                output.write(AdbPacketCodec.encode(A_CLSE, 501, first.arg0, byteArrayOf()))
                output.flush()

                // Keep the peer socket alive until the client acknowledges both
                // WRTE payloads and both stream closures.
                repeat(4) { readPacket(input) }
                true
            }
        })

        val clientSocket = Socket("127.0.0.1", server.localPort)
        val transport = AdbTransport.fromConnectedSocketForTest(clientSocket)
        val workers = Executors.newFixedThreadPool(2)
        try {
            val first = workers.submit<String> { transport.shell("echo first", 5000) }
            val second = workers.submit<String> { transport.shell("echo second", 5000) }
            assertEquals("first-result", first.get(6, TimeUnit.SECONDS))
            assertEquals("second-result", second.get(6, TimeUnit.SECONDS))
            assertTrue(serverResult.get(2, TimeUnit.SECONDS))
        } finally {
            transport.close()
            workers.shutdownNow()
            executor.shutdownNow()
            server.close()
        }
    }

    @Test
    fun shellAcknowledgesEachWriteBeforePeerClosesStream() {
        val server = ServerSocket(0)
        val executor = Executors.newSingleThreadExecutor()
        val serverResult = executor.submit(Callable {
            server.accept().use { peer ->
                peer.soTimeout = 5000
                val input = DataInputStream(peer.getInputStream())
                val output = DataOutputStream(peer.getOutputStream())
                val open = readPacket(input)
                assertEquals(A_OPEN, open.command)

                val remoteId = 701
                output.write(AdbPacketCodec.encode(A_OKAY, remoteId, open.arg0, byteArrayOf()))
                output.write(AdbPacketCodec.encode(A_WRTE, remoteId, open.arg0, "flow-controlled".toByteArray()))
                output.flush()

                // The peer waits for the host's WRTE acknowledgement before closing.
                val ack = readPacket(input)
                assertEquals(A_OKAY, ack.command)
                assertEquals(open.arg0, ack.arg0)
                assertEquals(remoteId, ack.arg1)
                assertEquals(0, ack.payload.size)

                output.write(AdbPacketCodec.encode(A_CLSE, remoteId, open.arg0, byteArrayOf()))
                output.flush()
                val closeAck = readPacket(input)
                assertEquals(A_CLSE, closeAck.command)
                assertEquals(open.arg0, closeAck.arg0)
                assertEquals(remoteId, closeAck.arg1)
                true
            }
        })

        val clientSocket = Socket("127.0.0.1", server.localPort)
        val transport = AdbTransport.fromConnectedSocketForTest(clientSocket)
        try {
            assertEquals("flow-controlled", transport.shell("echo flow", 5000))
            assertTrue(serverResult.get(2, TimeUnit.SECONDS))
        } finally {
            transport.close()
            executor.shutdownNow()
            server.close()
        }
    }

    private data class Frame(val command: Int, val arg0: Int, val arg1: Int, val payload: ByteArray)

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
        return Frame(command, arg0, arg1, payload)
    }

    companion object {
        private const val A_OPEN = 0x4e45504f
        private const val A_OKAY = 0x59414b4f
        private const val A_WRTE = 0x45545257
        private const val A_CLSE = 0x45534c43
    }
}
