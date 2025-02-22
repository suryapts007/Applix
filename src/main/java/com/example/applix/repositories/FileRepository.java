package com.example.applix.repositories;

import com.example.applix.models.db.File;
import org.springframework.data.jpa.repository.JpaRepository;


import java.util.List;
import java.util.Optional;

public interface FileRepository extends JpaRepository<File, Integer> {
     List<File> findByStatusIn(List<Integer> statuses);

     Optional<File> findById(Integer id);
}
