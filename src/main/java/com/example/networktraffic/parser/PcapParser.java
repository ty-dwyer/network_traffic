package com.example.networktraffic.parser;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.example.networktraffic.entities.Packet;
import com.example.networktraffic.entities.Protocol;

@Component
public class PcapParser {
    
    public List<Packet> openFile(String path) throws IOException {
    List<Packet> packets = new ArrayList<>();

    try (DataInputStream stream = new DataInputStream(new FileInputStream(path))) {
        stream.skipBytes(24);

        while (stream.available() > 0) {
            byte[] packetHeader = new byte[16];
            stream.readFully(packetHeader);

            int tsSeconds = ((packetHeader[0] & 0xFF)) | ((packetHeader[1] & 0xFF) << 8)
                           | ((packetHeader[2] & 0xFF) << 16) | ((packetHeader[3] & 0xFF) << 24);
            int tsMicros = ((packetHeader[4] & 0xFF)) | ((packetHeader[5] & 0xFF) << 8)
                          | ((packetHeader[6] & 0xFF) << 16) | ((packetHeader[7] & 0xFF) << 24);
            int capturedLength = ((packetHeader[8] & 0xFF)) | ((packetHeader[9] & 0xFF) << 8)
                                | ((packetHeader[10] & 0xFF) << 16) | ((packetHeader[11] & 0xFF) << 24);

            Instant timestamp = Instant.ofEpochSecond(tsSeconds, tsMicros * 1000L);

            byte[] input = new byte[capturedLength];
            stream.readFully(input);

            Packet packet = parsePacket(input, timestamp, capturedLength);
            packets.add(packet);
             }
        }
    return packets;
    }

    public Packet parsePacket (byte[] input, Instant timeStamp, int pakcetSize){
        Protocol protocol;
        Packet packet = new Packet();
        Integer sourcePort=null;
        Integer destPort=null;
        String tcpFlags = null;
        int protocolByte = input[23] & 0xFF; //we have to mask with 0xFF otherwise java would give us negative numbers
       
       
        protocol = switch (protocolByte) {
            case 6 -> Protocol.TCP;
            case 17 -> Protocol.UDP;
            case 1 -> Protocol.ICMP;
            default -> Protocol.UNKNOWN;
        };
        

        if (protocol == Protocol.TCP) {
            // ports and flags
            sourcePort = ((input[34] & 0xFF) << 8) | (input[35] & 0xFF);
            destPort = ((input[36] & 0xFF) << 8) | (input[37] & 0xFF);
            tcpFlags = String.valueOf(input[47] & 0xFF);
        } else if (protocol == Protocol.UDP) {
            // ports and skip flags
            sourcePort = ((input[34] & 0xFF) << 8) | (input[35] & 0xFF);
            destPort = ((input[36] & 0xFF) << 8) | (input[37] & 0xFF);
        } 

        String sourceIp = (input[26] & 0xFF) + "." + (input[27] & 0xFF) + "." 
                 + (input[28] & 0xFF) + "." + (input[29] & 0xFF);
        String destIp = (input[30] & 0xFF) + "." + (input[31] & 0xFF) + "." 
                 + (input[32] & 0xFF) + "." + (input[33] & 0xFF);
        
        packet.setTimeStamp(timeStamp);
        packet.setDestIp(destIp);
        packet.setSourceIp(sourceIp);
        packet.setSourcePort(sourcePort);
        packet.setDestPort(destPort);
        packet.setProtocol(protocol);
        packet.setPacketSize(pakcetSize);
        packet.setTcpFlags(tcpFlags);
        return packet;
    }

}
