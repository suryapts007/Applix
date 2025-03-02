package com.example.applix.services;


import com.example.applix.enums.ErrorCode;
import com.example.applix.exceptions.ApplixException;
import com.example.applix.models.db.FileTable;
import com.example.applix.models.db.FilteredData;
import com.example.applix.models.responses.GetDataResponse;
import com.example.applix.repositories.FileRepository;
import com.example.applix.repositories.FilteredDataRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;


import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.io.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;


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


    public GetDataResponse getData(Integer fileId, Integer pageNumber, Integer offSet, String startStr, String endStr) {
        int pageNum = (pageNumber == null || pageNumber < 1) ? 1 : pageNumber;
        int limit = (offSet == null || offSet < 1) ? 25 : offSet;

        Pageable pageable = PageRequest.of(pageNum - 1, limit);

        long totalRows;
        long totalFilteredRows;
        List<FilteredData> data;

        if (startStr != null && endStr != null) {
            Instant startInstant = Instant.parse(startStr);
            Instant endInstant = Instant.parse(endStr);

            // Fetch filtered data
            totalRows = filteredDataRepository.countByFileId(fileId);
            totalFilteredRows = filteredDataRepository.countByFileIdAndTimestampInstantBetween(fileId, startInstant, endInstant);
            data = filteredDataRepository.findByFileIdAndTimestampInstantBetweenOrderByTimestampInstantAsc(fileId, startInstant, endInstant, pageable);
        } else {
            // Fetch all data without time filtering
            totalRows = filteredDataRepository.countByFileId(fileId);
            totalFilteredRows = totalRows;
            data = filteredDataRepository.findByFileIdOrderByTimestampInstantAsc(fileId, pageable);
        }

        GetDataResponse response = new GetDataResponse();

        Optional<FileTable> fileById = fileRepository.findById(fileId);
        if(fileById.isPresent()) {
            FileTable file = fileById.get();
            Double mean = file.getMean();
            Double median = file.getMedian();
            response.setMeanTemperature(mean);
            response.setMedianTemperature(median);
        }

        response.setRecords(data);
        response.setTotalRows(totalRows);
        response.setTotalFilteredRows(totalFilteredRows);
        response.setTotalPages((int) Math.ceil((double) totalRows / limit));
        response.setMessage("success");
        response.setErrorCode(ErrorCode.NO_ERROR);

        return response;
    }


    public List<FileTable> getUploadedFilesWithStatusZeroOrOne() {
        return fileRepository.findByStatusInOrderByIdDesc(List.of(0, 1));
    }
}
