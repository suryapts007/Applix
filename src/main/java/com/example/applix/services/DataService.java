package com.example.applix.services;


import com.example.applix.exceptions.ApplixException;
import com.example.applix.models.db.FileTable;
import com.example.applix.models.db.FilteredData;
import com.example.applix.repositories.FileRepository;
import com.example.applix.repositories.FilteredDataRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;


import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.io.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;


@Service
public class DataService {
    private final FilteredDataRepository filteredDataRepository;
    private final FileRepository fileRepository;
    private final FileProcessorService fileProcessorService;
    private final KafkaProducerService kafkaProducerService;
    public DataService(FilteredDataRepository filteredDataRepository, FileRepository fileRepository, KafkaProducerService kafkaProducerService, FileProcessorService fileProcessorService) {
        this.filteredDataRepository = filteredDataRepository;
        this.fileRepository = fileRepository;
        this.kafkaProducerService = kafkaProducerService;
        this.fileProcessorService = fileProcessorService;
    }


    @Deprecated
    @Transactional(rollbackOn = Exception.class)
    public int uploadFileSync(MultipartFile file) throws IOException, ApplixException {
        long startTime = System.nanoTime();

        FileTable fileTable = fileProcessorService.insertFileMetaDataWithProcessingStatus(file.getOriginalFilename());

        List<FilteredData> filteredData = fileProcessorService.processFile(fileProcessorService.convertMultipartFileToFile(file), fileTable.getId());

        fileProcessorService.batchInsert(filteredData);

        fileProcessorService.updateFileMetaDataWithCompletedStatus(fileTable);

        long endTime = System.nanoTime();
        double totalTimeInSeconds = (endTime - startTime) / 1_000_000_000.0;
        System.out.println("uploadFile function executed in {} seconds" + totalTimeInSeconds);

        return filteredData.size();
    }


    @Transactional
    public void uploadFileAsync(MultipartFile file) throws IOException, ApplixException {
        long startTime = System.nanoTime();

        File savedFile = fileProcessorService.uploadFileToS3(file);

        FileTable fileTable = fileProcessorService.insertFileMetaDataWithProcessingStatus(savedFile.getName());

        kafkaProducerService.sendFileProcessingEvent(fileTable.getId(), savedFile.getAbsolutePath());

        long endTime = System.nanoTime();
        double totalTimeInSeconds = (endTime - startTime) / 1_000_000_000.0;
        System.out.println("File upload completed in {} seconds: " + totalTimeInSeconds);
    }


    public List<FilteredData> getData(Integer fileId, Integer pageNumber, Integer offSet, String start, String end) {
        int pageNum = (pageNumber == null || pageNumber < 1) ? 1 : pageNumber;
        int limit = (offSet == null || offSet < 1) ? 25 : offSet;

        Pageable pageable = PageRequest.of(pageNum - 1, limit);

        List<FilteredData> data;

        if (start != null && end != null) {
            // Convert UTC to IST
            ZoneId utcZone = ZoneId.of("UTC");
            ZoneId istZone = ZoneId.of("Asia/Kolkata");

            LocalDateTime startTimeUTC = Instant.parse(start).atZone(utcZone).toLocalDateTime();
            LocalDateTime endTimeUTC = Instant.parse(end).atZone(utcZone).toLocalDateTime();

            // Convert to IST
            LocalDateTime startTimeIST = startTimeUTC.atZone(utcZone).withZoneSameInstant(istZone).toLocalDateTime();
            LocalDateTime endTimeIST = endTimeUTC.atZone(utcZone).withZoneSameInstant(istZone).toLocalDateTime();


            // Fetch filtered data
            data = filteredDataRepository.findByFileIdAndTimestampBetweenOrderByTimestampAsc(fileId, startTimeIST, endTimeIST, pageable);
        } else {
            // Fetch all data without time filtering
            data = filteredDataRepository.findByFileIdOrderByTimestampAsc(fileId, pageable);
        }

        return data;
    }
    // start: "2024-01-09T18:30:00.000Z"
    // end: "2024-01-12T18:30:00.000Z"

    // startTime: 2024-01-09T18:30  2024-01-09T18:30
    // endTime: 2024-01-12T18:30

    // Timestamp stored in DB: 2023-12-31 18:30:01.000000

    public int getTotalPageCount(Integer fileId, Integer offSet, String start, String end) {
        int limit = (offSet == null || offSet < 1) ? 25 : offSet;
        long totalRecords;

        if (start != null && end != null) {
            LocalDateTime startTime = LocalDateTime.parse(start, DateTimeFormatter.ISO_DATE_TIME);
            LocalDateTime endTime = LocalDateTime.parse(end, DateTimeFormatter.ISO_DATE_TIME);

            // Count filtered records
            totalRecords = filteredDataRepository.countByFileIdAndTimestampBetween(fileId, startTime, endTime);
        } else {
            // Count all records
            totalRecords = filteredDataRepository.countByFileId(fileId);
        }

        return (int) Math.ceil((double) totalRecords / limit);
    }


    public List<FileTable> getUploadedFilesWithStatusZeroOrOne() {
        return fileRepository.findByStatusIn(List.of(0, 1));
    }
}
