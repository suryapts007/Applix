package com.example.applix.services;

import com.example.applix.models.db.FileTable;
import com.example.applix.repositories.FileRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

@Service
public class KafkaConsumerService {
    private final FileRepository fileRepository;
    private final FileProcessorService fileProcessorService;

    public KafkaConsumerService(FileRepository fileRepository, FileProcessorService fileProcessorService) {
        this.fileRepository = fileRepository;
        this.fileProcessorService = fileProcessorService;
    }

    @KafkaListener(topics = "file-processing-topic", groupId = "file-processing-group")
    public void consume(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        int fileId = Integer.parseInt(record.key());
        String filePath = record.value();

        System.out.println("✅ Received Kafka event: Processing file ID: " + fileId + ", Path: " + filePath);
        long startTime = System.nanoTime();

        try {
            // Step 1: Check if file status is already "1" (Processed)
            Optional<FileTable> fileTableOptional = fileRepository.findById(fileId);
            if (fileTableOptional.isEmpty()) {
                System.err.println("⚠️ File metadata not found for ID: " + fileId);
                acknowledgment.acknowledge();
                return;
            }

            FileTable fileTable = fileTableOptional.get();
            if (fileTable.getStatus() == 1) {
                System.out.println("✅ Skipping already processed file ID: " + fileId);
                acknowledgment.acknowledge();
                return;
            }

            // Step 2: Process the file
            File file = new File(filePath);
            if (!file.exists()) {
                System.err.println("⚠️ File not found at path: " + filePath);
                acknowledgment.acknowledge();
                return;
            }

            fileProcessorService.processFileStreaming(file, fileId);

            // Step 3: Update file status in DB
            fileProcessorService.updateFileMetaDataWithCompletedStatus(fileTable);
            System.out.println("✅ File processing completed for ID: " + fileId);

            // Step 4: Acknowledge Kafka after successful processing
            acknowledgment.acknowledge();

        } catch (IOException e) {
            System.err.println("❌ Error processing file ID: " + fileId + " - " + e.getMessage());
            // No acknowledgment -> Kafka will retry later
        }

        long endTime = System.nanoTime();
        double totalTimeInSeconds = (endTime - startTime) / 1_000_000_000.0;
        System.out.println("File processing completed in {} seconds: " + totalTimeInSeconds);
    }
}