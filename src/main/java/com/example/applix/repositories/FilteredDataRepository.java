package com.example.applix.repositories;

import com.example.applix.models.db.FilteredData;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;


import java.time.Instant;
import java.util.List;

public interface FilteredDataRepository extends JpaRepository<FilteredData, Integer> {
    long countByFileId(Integer fileId);

    List<FilteredData> findByFileIdAndTimestampInstantBetweenOrderByTimestampInstantAsc(Integer fileId, Instant startTime, Instant endTime, Pageable pageable);
    
    List<FilteredData> findByFileIdOrderByTimestampInstantAsc(Integer fileId, Pageable pageable);
    
    long countByFileIdAndTimestampInstantBetween(Integer fileId, Instant startTime, Instant endTime);
}
