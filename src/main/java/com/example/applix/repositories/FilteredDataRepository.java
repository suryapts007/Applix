package com.example.applix.repositories;

import com.example.applix.models.db.FilteredData;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;


import java.time.LocalDateTime;
import java.util.List;

public interface FilteredDataRepository extends JpaRepository<FilteredData, Integer> {
    List<FilteredData> findByFileIdAndTimestampBetween(Integer fileId, LocalDateTime start, LocalDateTime end);
    long countByFileId(Integer fileId);
    List<FilteredData> findByFileIdAndTimestampBetweenOrderByTimestampAsc(Integer fileId, LocalDateTime startTime, LocalDateTime endTime, Pageable pageable);
    List<FilteredData> findByFileIdOrderByTimestampAsc(Integer fileId, Pageable pageable);
    long countByFileIdAndTimestampBetween(Integer fileId, LocalDateTime startTime, LocalDateTime endTime);
}
