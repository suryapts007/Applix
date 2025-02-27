package com.example.applix.services;


import com.example.applix.exceptions.ApplixException;
import com.example.applix.models.db.FileTable;
import com.example.applix.models.db.FilteredData;
import com.example.applix.repositories.FileRepository;
import com.example.applix.repositories.FilteredDataRepository;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;


import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
    private final KafkaTemplate<String, String> kafkaTemplate;


    private static final int BATCH_SIZE = 1000;
    private static final String INSERT_SQL = "INSERT INTO filtered_data (timestamp, temperature, file_id) VALUES (?, ?, ?)";

    @Value("${file.upload-dir}") // Load upload directory from properties
    private String uploadDir;



    public DataService(FilteredDataRepository filteredDataRepository, FileRepository fileRepository, JdbcTemplate jdbcTemplate, KafkaTemplate kafkaTemplate) {
        this.filteredDataRepository = filteredDataRepository;
        this.fileRepository = fileRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Transactional
    public String uploadFileAsync(MultipartFile file) throws IOException, ApplixException {
        long startTime = System.nanoTime();

        if (file.isEmpty()) {
            throw new ApplixException("Empty file");
        }

        // ✅ Step 1: Save the file in resources/uploads/
        String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename(); // Avoid name conflicts
        java.io.File uploadDirectory = new java.io.File(uploadDir);
        if (!uploadDirectory.exists()) {
            boolean mkdirs = uploadDirectory.mkdirs(); // Ensure directory exists
            if(!mkdirs) throw new ApplixException("File Could Not Be Saved! Try Again");
        }


        java.io.File savedFile = new java.io.File(uploadDirectory, fileName);
        try (FileOutputStream fos = new FileOutputStream(savedFile)) {
            fos.write(file.getBytes());
        }

        // ✅ Step 2: Save file metadata in `files_table` with `status = 0`
        FileTable dbFileTable = new FileTable();
        dbFileTable.setName(fileName);
        dbFileTable.setStatus(0); // Mark as processing
        dbFileTable = fileRepository.save(dbFileTable);

        Integer fileId = dbFileTable.getId();

        // ✅ Step 3: Publish an event to Kafka
        kafkaTemplate.send("file-processing-topic", fileId.toString(), savedFile.getAbsolutePath());

        long endTime = System.nanoTime();
        double totalTimeInSeconds = (endTime - startTime) / 1_000_000_000.0;
        System.out.println("File upload completed in {} seconds: " + totalTimeInSeconds);

        return "File uploaded successfully. Processing started.";
    }

    @KafkaListener(topics = "file-processing-topic", groupId = "file-processing-group")
    public void consume(ConsumerRecord<String, String> record) {
        Integer fileId = Integer.parseInt(record.key());
        String filePath = record.value();

        try {
            // ✅ Step 1: Read the CSV file
            Path path = Paths.get(filePath);
            List<FilteredData> records = Files.lines(path, StandardCharsets.UTF_8)
                    .parallel() // Enable parallel processing
                    .filter(line -> !line.trim().isEmpty()) // Remove empty lines
                    .map(line -> parseLine(line, fileId)) // Convert to FilteredData object
                    .filter(Objects::nonNull) // Remove invalid records
                    .collect(Collectors.toList());

            if (!records.isEmpty()) {
                filteredDataRepository.saveAll(records);
            }

            // ✅ Step 2: Update file status to "Processed"
            fileRepository.findById(fileId).ifPresent(file -> {
                file.setStatus(1);
                fileRepository.save(file);
            });

            System.out.println("File processing completed: " + filePath);
        } catch (IOException e) {
            System.err.println("Error processing file: " + e.getMessage());
        }
    }



    @Transactional(rollbackOn = Exception.class)
    public int uploadFile(MultipartFile file) throws IOException, ApplixException {
        long startTime = System.nanoTime();  // Start timer

        if (file.isEmpty()) {
            throw new ApplixException("Empty file");
        }

        String fileName = file.getOriginalFilename();
        FileTable newFileTable = new FileTable();
        newFileTable.setName(fileName);
        newFileTable.setStatus(0); // Mark as processing

        newFileTable = fileRepository.save(newFileTable);
        Integer fileId = newFileTable.getId();

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
        newFileTable.setStatus(1);
        fileRepository.save(newFileTable);

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

    public List<FileTable> getUploadedFilesWithStatusZeroOrOne() {
        return fileRepository.findByStatusIn(List.of(0, 1));
    }
}
