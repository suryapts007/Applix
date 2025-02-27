package com.example.applix.services;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;


@Service
public class KafkaProducerService {
    private final KafkaTemplate<String, String> kafkaTemplate;

    public KafkaProducerService(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendFileProcessingEvent(Integer fileId, String filePath) {
        kafkaTemplate.send("file-processing-topic", fileId.toString(), filePath);
        System.out.println("Published event: File ID: " + fileId + ", Path: " + filePath);
    }
}
