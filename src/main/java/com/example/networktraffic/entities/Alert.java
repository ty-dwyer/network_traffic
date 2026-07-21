package com.example.networktraffic.entities;

import java.time.Instant;

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
@Table(name = "alerts")
@Setter
@Getter
@NoArgsConstructor
public class Alert {

    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;


    @Enumerated(EnumType.STRING)
    private AlertType type;

    public enum AlertType {
        NEW_DEVICE,
        TRAFFIC_SPIKE,
        UNUSUAL_PORT
    }

    private String message;

    private Instant timeStamp;

    @ManyToOne
    @JoinColumn(name = "device_id")
    private Device device;

    private Boolean acknowledged = false;

}
