package com.example.networktraffic.parser;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.example.networktraffic.entities.Packet;
import com.example.networktraffic.entities.Protocol;

public class PcapParser {
    
    public List<Packet> openFile (String path){
            List<Packet> packetList = new ArrayList<>();
            try (FileInputStream fis = new FileInputStream(path)){
                int data;
                while((data=fis.read())!= -1 ){
                    System.out.print(data+" ");
                }
            } catch (IOException e){
                e.printStackTrace();
            }
    }

    public Packet parsePacket (byte[] input, int timeStamp, int pakcetSize){
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
