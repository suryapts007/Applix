package com.example.applix.services;


import com.example.applix.exceptions.ApplixException;
import com.example.applix.models.db.FileTable;
import com.example.applix.models.db.FilteredData;
import com.example.applix.repositories.FileRepository;
import com.example.applix.repositories.FilteredDataRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;


import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.io.*;
import java.time.LocalDateTime;
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


    public List<FilteredData> getData(Integer fileId, Integer pageNumber, Integer offSet) {
        int pageNum = (pageNumber == null || pageNumber < 1) ? 1 : pageNumber;
        int limit = (offSet == null || offSet < 1) ? 25 : offSet;

        Pageable pageable = PageRequest.of(pageNum - 1, limit);
        Page<FilteredData> page = filteredDataRepository.findByFileId(fileId, pageable);

        return page.getContent();
    }


    public int getTotalPageCount(Integer fileId, Integer offSet) {
         int limit = (offSet == null || offSet < 1) ? 25 : offSet;

        // Get the total count of records for the given fileId
        long totalRecords = filteredDataRepository.countByFileId(fileId);

        return (int) Math.ceil((double) totalRecords / limit);
    }

    public List<FilteredData> getDataByTimeDelta(Integer fileId, String start, String end) {
        // Parse the start and end time strings into LocalDateTime
        LocalDateTime startTime = LocalDateTime.parse(start, DateTimeFormatter.ISO_DATE_TIME);
        LocalDateTime endTime = LocalDateTime.parse(end, DateTimeFormatter.ISO_DATE_TIME);

        // Retrieve data within the specified time range
        return filteredDataRepository.findByFileIdAndTimestampBetween(fileId, startTime, endTime);
    }

    public List<FileTable> getUploadedFilesWithStatusZeroOrOne() {
        return fileRepository.findByStatusIn(List.of(0, 1));
    }
}
