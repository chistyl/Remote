package com.chi.tvremote.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class AdbTransportSyncTest {
    @Test
    fun pushFileUsesDedicatedSyncStreamAndWaitsForFinalOkay() {
        val server = ServerSocket(0)
        val executor = Executors.newSingleThreadExecutor()
        val file = File.createTempFile("tvremote-sync", ".bin")
        val payload = ByteArray(70000) { (it % 251).toByte() }
        file.writeBytes(payload)
        val peerResult = executor.submit(Callable {
            server.accept().use { peer ->
                peer.soTimeout = 5000
                val input = DataInputStream(peer.getInputStream())
                val output = DataOutputStream(peer.getOutputStream())
                val open = readPacket(input)
                assertEquals(A_OPEN, open.command)
                assertEquals("sync:\u0000", String(open.payload))
                val remoteId = 901
                output.write(AdbPacketCodec.encode(A_OKAY, remoteId, open.arg0, byteArrayOf()))
                output.flush()

                val header = readPacket(input)
                assertEquals(A_WRTE, header.command)
                assertEquals(remoteId, header.arg1)
                assertEquals(AdbSyncProtocol.sendHeader("/data/local/tmp/payload.bin").toList(), header.payload.toList())
                acknowledge(output, input, header, remoteId)

                val dataOne = readPacket(input)
                assertEquals(A_WRTE, dataOne.command)
                val decodedOne = decodeData(dataOne.payload)
                assertEquals(AdbSyncProtocol.MAX_DATA_LENGTH, decodedOne.size)
                acknowledge(output, input, dataOne, remoteId)

                val dataTwo = readPacket(input)
                assertEquals(A_WRTE, dataTwo.command)
                val decodedTwo = decodeData(dataTwo.payload)
                assertEquals(payload.size - AdbSyncProtocol.MAX_DATA_LENGTH, decodedTwo.size)
                acknowledge(output, input, dataTwo, remoteId)

                val done = readPacket(input)
                assertEquals(A_WRTE, done.command)
                assertEquals(0x454e4f44, ByteBuffer.wrap(done.payload).order(ByteOrder.LITTLE_ENDIAN).int)
                acknowledge(output, input, done, remoteId)

                val okay = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(0x59414b4f).array()
                output.write(AdbPacketCodec.encode(A_WRTE, remoteId, open.arg0, okay))
                output.flush()
                val responseAck = readPacket(input)
                assertEquals(A_OKAY, responseAck.command)
                assertEquals(open.arg0, responseAck.arg0)
                assertEquals(remoteId, responseAck.arg1)
                val close = readPacket(input)
                assertEquals(A_CLSE, close.command)
                true
            }
        })

        val transport = AdbTransport.fromConnectedSocketForTest(Socket("127.0.0.1", server.localPort))
        try {
            transport.pushFile(file, "/data/local/tmp/payload.bin", timeoutMs = 5000)
            assertTrue(peerResult.get(5, TimeUnit.SECONDS))
        } finally {
            transport.close()
            executor.shutdownNow()
            server.close()
            file.delete()
        }
    }

    private fun acknowledge(output: DataOutputStream, input: DataInputStream, frame: Frame, remoteId: Int) {
        output.write(AdbPacketCodec.encode(A_OKAY, remoteId, frame.arg0, byteArrayOf()))
        output.flush()
        val next = readPacket(input)
        // The client must wait for this acknowledgement before sending the next WRTE.
        assertTrue(next.command == A_WRTE || next.command == A_CLSE)
        // Push it back is not possible on a socket, so this helper is intentionally
        // not used for multi-frame sequencing.
    }

    private fun decodeData(frame: ByteArray): ByteArray {
        val buffer = ByteBuffer.wrap(frame).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0x41544144, buffer.int)
        val length = buffer.int
        return ByteArray(length).also { buffer.get(it) }
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
        val bytes = ByteArray(length)
        input.readFully(bytes)
        AdbPacketCodec.decode(header, bytes)
        return Frame(command, arg0, arg1, bytes)
    }

    companion object {
        private const val A_OPEN = 0x4e45504f
        private const val A_OKAY = 0x59414b4f
        private const val A_WRTE = 0x45545257
        private const val A_CLSE = 0x45534c43
    }
}
