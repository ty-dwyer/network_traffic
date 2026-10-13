package com.example.networktraffic.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.networktraffic.entities.Packet;
import com.example.networktraffic.entities.Protocol;

class PcapParserTest {
    @TempDir Path tempDir;

    private final PcapParser parser = new PcapParser();

    @Test
    void parsesLittleEndianPcapWithVlanAndIpv4OptionsInBoundedBatches() throws Exception {
        Path file = writePcap(ByteOrder.LITTLE_ENDIAN, ethernetFrame(true), 1, false);
        List<List<Packet>> batches = new ArrayList<>();

        parser.readBatches(file.toString(), 1, batches::add);

        assertThat(batches).hasSize(1);
        Packet packet = batches.getFirst().getFirst();
        assertThat(packet.getSourceIp()).isEqualTo("192.168.1.10");
        assertThat(packet.getDestIp()).isEqualTo("8.8.8.8");
        assertThat(packet.getSourcePort()).isEqualTo(54321);
        assertThat(packet.getDestPort()).isEqualTo(443);
        assertThat(packet.getProtocol()).isEqualTo(Protocol.TCP);
        assertThat(packet.getTcpFlags()).isEqualTo("2");
    }

    @Test
    void parsesBigEndianPcapAndNanosecondTimestamp() throws Exception {
        Path file = writePcap(ByteOrder.BIG_ENDIAN, ethernetFrame(false), 2, true);
        Packet packet = parser.openFile(file.toString()).getFirst();

        assertThat(packet.getTimeStamp().getNano()).isEqualTo(123_456_789);
        assertThat(packet.getProtocol()).isEqualTo(Protocol.TCP);
    }

    @Test
    void rejectsInvalidMagicAndTruncatedPacketData() throws Exception {
        Path invalid = tempDir.resolve("invalid.pcap");
        Files.write(invalid, new byte[24]);
        assertThatIOException().isThrownBy(() -> parser.openFile(invalid.toString()))
                .withMessageContaining("magic");

        Path truncated = tempDir.resolve("truncated.pcap");
        byte[] validHeader = pcapHeader(ByteOrder.LITTLE_ENDIAN, false, 1);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(validHeader);
        ByteBuffer record = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(1).putInt(0).putInt(100).putInt(100);
        bytes.write(record.array());
        bytes.write(new byte[8]);
        Files.write(truncated, bytes.toByteArray());
        assertThatIOException().isThrownBy(() -> parser.openFile(truncated.toString()))
                .withMessageContaining("Truncated PCAP packet data");
    }

    private Path writePcap(ByteOrder order, byte[] frame, int linkType, boolean nano) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(pcapHeader(order, nano, linkType));
        ByteBuffer record = ByteBuffer.allocate(16).order(order)
                .putInt(1).putInt(nano ? 123_456_789 : 123_456).putInt(frame.length).putInt(frame.length);
        bytes.write(record.array());
        bytes.write(frame);
        Path path = tempDir.resolve("capture.pcap");
        Files.write(path, bytes.toByteArray());
        return path;
    }

    private byte[] pcapHeader(ByteOrder order, boolean nano, int linkType) {
        ByteBuffer header = ByteBuffer.allocate(24).order(order);
        if (order == ByteOrder.LITTLE_ENDIAN) {
            header.put(nano ? new byte[] {0x4d, 0x3c, (byte) 0xb2, (byte) 0xa1}
                    : new byte[] {(byte) 0xd4, (byte) 0xc3, (byte) 0xb2, (byte) 0xa1});
        } else {
            header.put(nano ? new byte[] {(byte) 0xa1, (byte) 0xb2, 0x3c, 0x4d}
                    : new byte[] {(byte) 0xa1, (byte) 0xb2, (byte) 0xc3, (byte) 0xd4});
        }
        header.putShort((short) 2).putShort((short) 4).putInt(0).putInt(0).putInt(65535).putInt(linkType);
        return header.array();
    }

    private byte[] ethernetFrame(boolean vlanAndOptions) {
        int ethernetLength = vlanAndOptions ? 18 : 14;
        int ipHeaderLength = vlanAndOptions ? 24 : 20;
        byte[] frame = new byte[ethernetLength + ipHeaderLength + 20];
        ByteBuffer bytes = ByteBuffer.wrap(frame).order(ByteOrder.BIG_ENDIAN);
        if (vlanAndOptions) {
            bytes.putShort(12, (short) 0x8100);
            bytes.putShort(16, (short) 0x0800);
        } else {
            bytes.putShort(12, (short) 0x0800);
        }
        int ip = ethernetLength;
        frame[ip] = (byte) (0x40 | (ipHeaderLength / 4));
        bytes.putShort(ip + 2, (short) (ipHeaderLength + 20));
        frame[ip + 9] = 6;
        frame[ip + 12] = (byte) 192; frame[ip + 13] = (byte) 168;
        frame[ip + 14] = 1; frame[ip + 15] = 10;
        frame[ip + 16] = 8; frame[ip + 17] = 8; frame[ip + 18] = 8; frame[ip + 19] = 8;
        int tcp = ip + ipHeaderLength;
        bytes.putShort(tcp, (short) 54321).putShort(tcp + 2, (short) 443);
        frame[tcp + 12] = 0x50;
        frame[tcp + 13] = 0x02;
        return frame;
    }
}
