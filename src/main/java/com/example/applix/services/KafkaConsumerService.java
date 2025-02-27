package com.example.applix.services;


import com.example.applix.models.db.FilteredData;
import com.example.applix.repositories.FileRepository;
import com.example.applix.repositories.FilteredDataRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.io.IOException;
import java.util.stream.Stream;


@Service
public class KafkaConsumerService {
    private final FilteredDataRepository filteredDataRepository;
    private final FileRepository fileRepository;
    private final FileProcessorService fileProcessorService;

    public KafkaConsumerService(FilteredDataRepository filteredDataRepository, FileRepository fileRepository, FileProcessorService fileProcessorService) {
        this.filteredDataRepository = filteredDataRepository;
        this.fileRepository = fileRepository;
        this.fileProcessorService = fileProcessorService;
    }

    @KafkaListener(topics = "file-processing-topic", groupId = "file-processing-group")
    public void consume(ConsumerRecord<String, String> record) {
        Integer fileId = Integer.parseInt(record.key());
        String filePath = record.value();

        try {
            Path path = Paths.get(filePath);
//            fileProcessorService.processFile()
            Stream<String> lines = Files.lines(path, StandardCharsets.UTF_8);
            List<FilteredData> records = Files.lines(path, StandardCharsets.UTF_8)
                    .parallel()
                    .filter(line -> !line.trim().isEmpty()) // Remove empty lines
                    .map(line -> fileProcessorService.parseLine(line, fileId)) // Convert to FilteredData object
                    .filter(Objects::nonNull) // Remove invalid records
                    .collect(Collectors.toList());

            if (!records.isEmpty()) {
                filteredDataRepository.saveAll(records);
            }

            fileRepository.findById(fileId).ifPresent(file -> {
                file.setStatus(1);
                fileRepository.save(file);
            });

            System.out.println("File processing completed: " + filePath);
        } catch (IOException e) {
            System.err.println("Error processing file: " + e.getMessage());
        }
    }

}
