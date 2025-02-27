package com.example.applix.models.db;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "filtered_data")
public class FilteredData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "file_id", nullable = false)
    private Integer fileId;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "temperature", nullable = false)
    private Double temperature;
//INSERT INTO filtered_data (timestamp, temperature, file_id) VALUES (?, ?, ?)
}