package com.example.applix.services;


import com.example.applix.exceptions.ApplixException;
import com.example.applix.models.db.File;
import com.example.applix.models.db.FilteredData;
import com.example.applix.repositories.FileRepository;
import com.example.applix.repositories.FilteredDataRepository;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;


import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class DataService {
    private final FilteredDataRepository filteredDataRepository;
    private final FileRepository fileRepository;
    private final JdbcTemplate jdbcTemplate;

    private static final int BATCH_SIZE = 1000;
    private static final String INSERT_SQL = "INSERT INTO filtered_data (timestamp, temperature, file_id) VALUES (?, ?, ?)";


    public DataService(FilteredDataRepository filteredDataRepository, FileRepository fileRepository, JdbcTemplate jdbcTemplate) {
        this.filteredDataRepository = filteredDataRepository;
        this.fileRepository = fileRepository;
        this.jdbcTemplate = jdbcTemplate;
    }


    @Transactional(rollbackOn = Exception.class)
    public int uploadFile(MultipartFile file) throws IOException, ApplixException {
        long startTime = System.nanoTime();  // Start timer

        if (file.isEmpty()) {
            throw new ApplixException("Empty file");
        }

        String fileName = file.getOriginalFilename();
        File newFile = new File();
        newFile.setName(fileName);
        newFile.setStatus(0); // Mark as processing

        newFile = fileRepository.save(newFile);
        Integer fileId = newFile.getId();

        BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));

        // Process and validate CSV file
        List<FilteredData> records = br.lines()
                .parallel() // Enable parallel processing
                .filter(line -> !line.trim().isEmpty()) // Remove empty lines
                .map(line -> parseLine(line, fileId)) // Convert to FilteredData object
                .filter(Objects::nonNull) // Remove invalid records
                .collect(Collectors.toList());

        if (records.isEmpty()) {
            throw new ApplixException("No valid data found in the file.");
        }

        // Perform batch insert
        batchInsert(records);

        // Mark file processing as completed
        newFile.setStatus(1);
        fileRepository.save(newFile);

        long endTime = System.nanoTime();  // End timer
        double totalTimeInSeconds = (endTime - startTime) / 1_000_000_000.0;
        System.out.println("uploadFile function executed in {} seconds" + totalTimeInSeconds);

        return records.size();
    }

    private FilteredData parseLine(String line, Integer fileId) {
        try {
            String[] values = line.split(",");
            if (values.length != 2) return null; // Ensure exactly two values

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
    }

    private void batchInsert(List<FilteredData> records) {
        for (int i = 0; i < records.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, records.size());
            List<FilteredData> batch = records.subList(i, end); // Get the batch


            jdbcTemplate.batchUpdate(INSERT_SQL, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(@NotNull PreparedStatement ps, int batchIndex) throws SQLException {
                    FilteredData data = batch.get(batchIndex); // ✅ Use batchIndex relative to this batch
                    ps.setTimestamp(1, java.sql.Timestamp.valueOf(data.getTimestamp()));
                    ps.setDouble(2, data.getTemperature());
                    ps.setInt(3, data.getFileId());
                }

                @Override
                public int getBatchSize() {
                    return batch.size(); // ✅ Correctly return batch size
                }
            });
        }
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
