package com.example.applix.repositories;

import com.example.applix.models.db.RawData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;


import java.time.LocalDateTime;
import java.util.Optional;

public interface RawDataRepository extends JpaRepository<RawData, Integer> {

    @Query("SELECT d FROM RawData d WHERE d.timestamp BETWEEN :startTime AND :endTime")
    List<RawData> findByTimestampBetween(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    Optional<RawData> findById(Integer integer);
}
