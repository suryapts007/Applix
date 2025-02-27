package com.example.applix.repositories;

import com.example.applix.models.db.FileTable;
import org.springframework.data.jpa.repository.JpaRepository;


import java.util.List;
import java.util.Optional;

public interface FileRepository extends JpaRepository<FileTable, Integer> {
     List<FileTable> findByStatusIn(List<Integer> statuses);

     Optional<FileTable> findById(Integer id);
}
