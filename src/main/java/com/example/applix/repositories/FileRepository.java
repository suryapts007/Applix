package com.example.applix.repositories;

import com.example.applix.models.db.File;
import org.springframework.data.jpa.repository.JpaRepository;


import java.util.Optional;

public interface FileRepository extends JpaRepository<File, Integer> {
     Optional<File> findById(Integer id);
}
