package com.example.applix.services;


import com.example.applix.exceptions.ApplixException;
import com.example.applix.models.db.File;
import com.example.applix.models.db.FilteredData;
import com.example.applix.repositories.FileRepository;
import com.example.applix.repositories.FilteredDataRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;


import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class DataService {
    private final FilteredDataRepository filteredDataRepository;
    private final FileRepository fileRepository;

    public DataService(FilteredDataRepository filteredDataRepository, FileRepository fileRepository) {
        this.filteredDataRepository = filteredDataRepository;
        this.fileRepository = fileRepository;
    }


    @Transactional(rollbackOn = Exception.class)
    public int uploadFile(MultipartFile file) throws IOException, ApplixException {
        if (file.isEmpty()) {
            throw new ApplixException("Empty file");
        }

        String fileName = file.getOriginalFilename();
        File newFile = new File();
        newFile.setName(fileName);
        newFile.setStatus(0);

        newFile = fileRepository.save(newFile);
        Integer fileId = newFile.getId();

        BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));

        List<FilteredData> records = br.lines()
                .filter(line -> !line.trim().isEmpty()) // Remove empty lines
                .filter(line -> {
                    String[] values = line.split(",");
                    return values.length == 2 && !values[0].trim().isEmpty() && !values[1].trim().isEmpty();
                }) // Ensure exactly two values (timestamp, temperature) & both non-empty
                .map(line -> {
                    String[] values = line.split(",");
                    try {
                        LocalDateTime timestamp = LocalDateTime.parse(values[0].trim(), DateTimeFormatter.ISO_DATE_TIME);
                        double temperature = Double.parseDouble(values[1].trim());

                        FilteredData record = new FilteredData();
                        record.setTimestamp(timestamp);
                        record.setTemperature(temperature);
                        record.setFileId(fileId);

                        return record;
                    } catch (Exception e) {
                        return null; // Skip invalid entries
                    }
                })
                .filter(Objects::nonNull) // Remove any null (malformed) records
                .collect(Collectors.toList());

        if (records.isEmpty()) {
            throw new ApplixException("No valid data found in the file.");
        }

        filteredDataRepository.saveAll(records);

        newFile.setStatus(1);
        fileRepository.save(newFile);

        return records.size();
    }

    public List<FilteredData> getData(Integer fileId, Integer pageNumber, Integer offSet) {
        // Set default values
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

    public List<File> getUploadedFilesWithStatusZeroOrOne() {
        return fileRepository.findByStatusIn(List.of(0, 1));
    }
}
