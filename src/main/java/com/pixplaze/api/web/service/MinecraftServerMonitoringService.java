package com.pixplaze.api.web.service;

import com.pixplaze.api.ext.data.server.MinecraftServerPortsInfo;
import com.pixplaze.api.web.data.server.RawMinecraftServer;
import com.pixplaze.api.web.data.server.MinecraftServerState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

@Service
public class MinecraftServerMonitoringService {

    private static final int MAX_PACKET_SIZE = 8192;
    private static final ThreadLocal<ByteBuffer> BUFFER_CACHE = ThreadLocal.withInitial(() -> ByteBuffer.allocate(MAX_PACKET_SIZE));
    /// Handshake packet id for Minecraft Server version > 1.18.2
    public static final int PACKET_ID_HANDSHAKE_1_18_2 = 758;
    private final JsonMapper jsonMapper;

    @Autowired
    public MinecraftServerMonitoringService(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public RawMinecraftServer getServer(String host, int port) throws IOException {
        return getServer(new InetSocketAddress(host, port));
    }

    public RawMinecraftServer getServer(InetSocketAddress socketAddress) throws IOException {
        try (Socket socket = new Socket()) {
            socket.setSoTimeout(10000);
            socket.connect(socketAddress);

            var out = socket.getOutputStream();
            var in = socket.getInputStream();

            ByteBuffer byteBufferOut = BUFFER_CACHE.get();
            byteBufferOut.clear();

            // Handshake
            writeHandshake(byteBufferOut, socketAddress.getHostName(), socket.getPort());
            writePacket(out, byteBufferOut);
            byteBufferOut.clear();

            //  Status request
            writeStatusRequest(byteBufferOut);
            writePacket(out, byteBufferOut);
            var json = readStatusResponse(in);
            byteBufferOut.clear();

            // Ping
            writePing(byteBufferOut);
            writePacket(out, byteBufferOut);
            long ping = readPingResponse(in);
            byteBufferOut.clear();

            RawMinecraftServer rawMinecraftServer = jsonMapper.readValue(json, RawMinecraftServer.class);
            rawMinecraftServer.setHost(socketAddress.getHostName());
            rawMinecraftServer.setPorts(new MinecraftServerPortsInfo(socket.getPort()));
            rawMinecraftServer.setState(new MinecraftServerState());
            rawMinecraftServer.getState().setPing(ping);

            return rawMinecraftServer;
        }
    }

    private static void writeHandshake(ByteBuffer buffer, String host, int port) {
        writeVarInt(buffer, 0x00); // Packet ID
        writeVarInt(buffer, PACKET_ID_HANDSHAKE_1_18_2); // Protocol version
        writeString(buffer, host);
        buffer.putShort((short) port);
        writeVarInt(buffer, 0x01); // Next state: STATUS
        buffer.flip();
    }

    private static void writeStatusRequest(ByteBuffer buffer) {
        writeVarInt(buffer, 0x00); // Packet ID
        buffer.flip();
    }

    private static String readStatusResponse(InputStream in) throws IOException {
        readVarInt(in); // packet length
        readVarInt(in); // packet id
        return readString(in);
    }

    private static void writePing(ByteBuffer buffer) {
        writeVarInt(buffer, 0x01);
        buffer.putLong(System.currentTimeMillis());
        buffer.flip();
    }

    private static long readPingResponse(InputStream in) throws IOException {
        readVarInt(in); // packet length
        readVarInt(in); // packet id
        return System.currentTimeMillis() - readLong(in);
    }

    public static void writePacket(OutputStream out, ByteBuffer buffer) throws IOException {
        byte[] data = buffer.array();
        int length = buffer.remaining();
        writeVarInt(out, length);
        out.write(data, buffer.position(), length);
    }

    public static void writeVarInt(ByteBuffer buffer, int value) {
        while ((value & 0xFFFFFF80) != 0L) {
            buffer.put((byte) ((value & 0x7F) | 0x80));
            value >>>= 7;
        }
        buffer.put((byte) value);
    }

    public static void writeVarInt(OutputStream out, int value) throws IOException {
        while ((value & 0xFFFFFF80) != 0L) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value);
    }

    public static void writeString(ByteBuffer buffer, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(buffer, bytes.length);
        buffer.put(bytes);
    }

    public static int readVarInt(InputStream in) throws IOException {
        int numRead = 0;
        int result = 0;
        int read;
        do {
            read = in.read();
            if (read == -1) throw new IOException("End of stream");
            int value = (read & 0b01111111);
            result |= (value << (7 * numRead));

            numRead++;
            if (numRead > 5) throw new IOException("VarInt too big");
        } while ((read & 0b10000000) != 0);
        return result;
    }

    public static long readLong(InputStream in) throws IOException {
        byte[] bytes = new byte[8];
        int read = in.read(bytes);
        if (read != 8) throw new IOException("Failed to read long");
        return ((long) (bytes[0] & 0xFF) << 56) |
                ((long) (bytes[1] & 0xFF) << 48) |
                ((long) (bytes[2] & 0xFF) << 40) |
                ((long) (bytes[3] & 0xFF) << 32) |
                ((long) (bytes[4] & 0xFF) << 24) |
                ((long) (bytes[5] & 0xFF) << 16) |
                ((long) (bytes[6] & 0xFF) << 8) |
                ((long) (bytes[7] & 0xFF));
    }

    public static String readString(InputStream in) throws IOException {
        int length = readVarInt(in);
        byte[] bytes = new byte[length];
        int read = 0;
        while (read < length) {
            int r = in.read(bytes, read, length - read);
            if (r == -1) throw new IOException("End of stream");
            read += r;
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
