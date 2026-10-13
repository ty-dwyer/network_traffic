package com.example.networktraffic.parser;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.springframework.stereotype.Component;

import com.example.networktraffic.entities.Packet;
import com.example.networktraffic.entities.Protocol;

/** Reads classic PCAP files containing Ethernet frames and IPv4 packets. */
@Component
public class PcapParser {
    private static final int GLOBAL_HEADER_LENGTH = 24;
    private static final int RECORD_HEADER_LENGTH = 16;
    private static final int ETHERNET_HEADER_LENGTH = 14;
    private static final int ETHERNET_IPV4 = 0x0800;
    private static final int ETHERNET_VLAN = 0x8100;
    private static final int ETHERNET_QINQ = 0x88A8;
    private static final int MAX_CAPTURED_PACKET_LENGTH = 16 * 1024 * 1024;

    /** Compatibility helper for small captures. Production ingestion uses readBatches. */
    public List<Packet> openFile(String path) throws IOException {
        List<Packet> packets = new ArrayList<>();
        readBatches(path, 512, packets::addAll);
        return packets;
    }

    /** Streams parsed IPv4 packets to the consumer in bounded batches. Non-IPv4 frames are skipped. */
    public void readBatches(String path, int batchSize, Consumer<List<Packet>> consumer) throws IOException {
        if (batchSize <= 0) throw new IllegalArgumentException("batchSize must be positive");
        try (DataInputStream stream = new DataInputStream(Files.newInputStream(Path.of(path)))) {
            byte[] globalHeader = readExact(stream, GLOBAL_HEADER_LENGTH, "truncated PCAP global header");
            Header header = parseHeader(globalHeader);
            if (header.linkType != 1) {
                throw new IOException("Unsupported PCAP link type " + header.linkType + "; Ethernet (1) is required");
            }

            List<Packet> batch = new ArrayList<>(batchSize);
            byte[] recordHeader = new byte[RECORD_HEADER_LENGTH];
            while (true) {
                int first = stream.read();
                if (first == -1) break;
                recordHeader[0] = (byte) first;
                try {
                    stream.readFully(recordHeader, 1, RECORD_HEADER_LENGTH - 1);
                } catch (EOFException ex) {
                    throw new IOException("Truncated PCAP packet header", ex);
                }
                ByteBuffer record = ByteBuffer.wrap(recordHeader).order(header.order);
                long seconds = Integer.toUnsignedLong(record.getInt());
                long fraction = Integer.toUnsignedLong(record.getInt());
                long capturedLength = Integer.toUnsignedLong(record.getInt());
                long originalLength = Integer.toUnsignedLong(record.getInt());
                if (capturedLength > MAX_CAPTURED_PACKET_LENGTH || capturedLength > Integer.MAX_VALUE) {
                    throw new IOException("Captured packet length exceeds the supported limit: " + capturedLength);
                }
                if (header.snapLength > 0 && capturedLength > header.snapLength) {
                    throw new IOException("Captured packet length exceeds PCAP snap length");
                }
                if (capturedLength > originalLength) throw new IOException("Captured length exceeds original packet length");
                byte[] frame;
                try {
                    frame = readExact(stream, (int) capturedLength, "truncated PCAP packet data");
                } catch (EOFException ex) {
                    throw new IOException("Truncated PCAP packet data", ex);
                }
                long nanos = header.nanosecondResolution ? fraction : fraction * 1_000L;
                if (nanos >= 1_000_000_000L) throw new IOException("Invalid PCAP timestamp fraction");
                Packet packet = parseEthernetFrame(frame, Instant.ofEpochSecond(seconds, nanos), (int) capturedLength);
                if (packet != null) {
                    batch.add(packet);
                    if (batch.size() == batchSize) {
                        consumer.accept(List.copyOf(batch));
                        batch.clear();
                    }
                }
            }
            if (!batch.isEmpty()) consumer.accept(List.copyOf(batch));
        }
    }

    private Header parseHeader(byte[] bytes) throws IOException {
        int magic = ByteBuffer.wrap(bytes, 0, 4).order(ByteOrder.BIG_ENDIAN).getInt();
        ByteOrder order;
        boolean nano;
        switch (magic) {
            case 0xD4C3B2A1 -> { order = ByteOrder.LITTLE_ENDIAN; nano = false; }
            case 0xA1B2C3D4 -> { order = ByteOrder.BIG_ENDIAN; nano = false; }
            case 0x4D3CB2A1 -> { order = ByteOrder.LITTLE_ENDIAN; nano = true; }
            case 0xA1B23C4D -> { order = ByteOrder.BIG_ENDIAN; nano = true; }
            default -> throw new IOException("Invalid or unsupported PCAP magic number");
        }
        ByteBuffer header = ByteBuffer.wrap(bytes).order(order);
        int majorVersion = Short.toUnsignedInt(header.getShort(4));
        int minorVersion = Short.toUnsignedInt(header.getShort(6));
        if (majorVersion != 2 || minorVersion != 4) throw new IOException("Unsupported PCAP version " + majorVersion + "." + minorVersion);
        header.position(16);
        long snapLength = Integer.toUnsignedLong(header.getInt());
        long linkType = Integer.toUnsignedLong(header.getInt());
        if (snapLength > MAX_CAPTURED_PACKET_LENGTH) throw new IOException("PCAP snap length exceeds supported limit");
        return new Header(order, nano, snapLength, linkType);
    }

    private Packet parseEthernetFrame(byte[] frame, Instant timestamp, int capturedLength) throws IOException {
        if (frame.length < ETHERNET_HEADER_LENGTH) throw new IOException("Truncated Ethernet frame");
        int etherType = unsignedShort(frame, 12);
        int offset = ETHERNET_HEADER_LENGTH;
        while (etherType == ETHERNET_VLAN || etherType == ETHERNET_QINQ) {
            requireRange(frame, offset, 4, "Truncated VLAN header");
            etherType = unsignedShort(frame, offset + 2);
            offset += 4;
        }
        if (etherType != ETHERNET_IPV4) return null;
        requireRange(frame, offset, 20, "Truncated IPv4 header");
        int versionAndLength = frame[offset] & 0xff;
        if ((versionAndLength >>> 4) != 4) throw new IOException("Invalid IPv4 version");
        int ipHeaderLength = (versionAndLength & 0x0f) * 4;
        if (ipHeaderLength < 20) throw new IOException("Invalid IPv4 header length");
        requireRange(frame, offset, ipHeaderLength, "Truncated IPv4 options");
        int totalLength = unsignedShort(frame, offset + 2);
        if (totalLength < ipHeaderLength) throw new IOException("Invalid IPv4 total length");
        int ipEnd = offset + Math.min(totalLength, frame.length - offset);
        int fragmentOffset = unsignedShort(frame, offset + 6) & 0x1fff;
        int transportOffset = offset + ipHeaderLength;
        int protocolNumber = frame[offset + 9] & 0xff;
        Protocol protocol = switch (protocolNumber) {
            case 6 -> Protocol.TCP;
            case 17 -> Protocol.UDP;
            case 1 -> Protocol.ICMP;
            default -> Protocol.UNKNOWN;
        };
        Integer sourcePort = null, destPort = null;
        String tcpFlags = null;
        if (fragmentOffset == 0 && protocol == Protocol.TCP && transportOffset + 20 <= ipEnd) {
            sourcePort = unsignedShort(frame, transportOffset);
            destPort = unsignedShort(frame, transportOffset + 2);
            tcpFlags = String.valueOf(frame[transportOffset + 13] & 0xff);
        } else if (fragmentOffset == 0 && protocol == Protocol.UDP && transportOffset + 8 <= ipEnd) {
            sourcePort = unsignedShort(frame, transportOffset);
            destPort = unsignedShort(frame, transportOffset + 2);
        }
        Packet packet = new Packet();
        packet.setTimeStamp(timestamp);
        packet.setSourceIp(ipv4Address(frame, offset + 12));
        packet.setDestIp(ipv4Address(frame, offset + 16));
        packet.setSourcePort(sourcePort);
        packet.setDestPort(destPort);
        packet.setTcpFlags(tcpFlags);
        packet.setProtocol(protocol);
        packet.setPacketSize(capturedLength);
        return packet;
    }

    private static byte[] readExact(InputStream stream, int length, String message) throws IOException {
        byte[] bytes = new byte[length];
        try {
            new DataInputStream(stream).readFully(bytes);
        } catch (EOFException ex) {
            EOFException failure = new EOFException(message);
            failure.initCause(ex);
            throw failure;
        }
        return bytes;
    }

    private static void requireRange(byte[] bytes, int offset, int length, String message) throws IOException {
        if (offset < 0 || length < 0 || offset > bytes.length - length) throw new IOException(message);
    }

    private static int unsignedShort(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) << 8) | (bytes[offset + 1] & 0xff);
    }

    private static String ipv4Address(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) + "." + (bytes[offset + 1] & 0xff) + "."
                + (bytes[offset + 2] & 0xff) + "." + (bytes[offset + 3] & 0xff);
    }

    private record Header(ByteOrder order, boolean nanosecondResolution, long snapLength, long linkType) {}
}
