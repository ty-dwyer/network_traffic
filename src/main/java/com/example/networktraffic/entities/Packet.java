package com.example.networktraffic.entities;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name="packets")
@Getter
@Setter
@NoArgsConstructor
public class Packet {
    
    @Id
    @GeneratedValue( strategy= GenerationType.IDENTITY)
    private Long id;

    @Column(nullable=false)
    private Instant timeStamp;


    @Column(nullable = false, length= 15)
    private String sourceIp;

    @Column(nullable = false, length= 15)
    private String destIp;


    private Integer sourcePort;
    private Integer destPort;
    
    @Enumerated(EnumType.STRING)
    private Protocol protocol;


    private Integer packetSize;

    private String tcpFlags;

    @ManyToOne
    @JoinColumn(name = "device_id")
    private Device device;

}
