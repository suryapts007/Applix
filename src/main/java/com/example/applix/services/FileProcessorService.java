package com.example.applix.services;

import com.example.applix.exceptions.ApplixException;
import com.example.applix.models.db.FileTable;
import com.example.applix.models.db.FilteredData;
import com.example.applix.repositories.FileRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class FileProcessorService {
    @Value("${file.upload-dir}")
    private String UPLOAD_DIR;
    @Value("${sql.insert.batch-size}")
    private Integer BATCH_SIZE;
    private final FileRepository fileRepository;
    private final JdbcTemplate jdbcTemplate;
    public FileProcessorService(FileRepository fileRepository, JdbcTemplate jdbcTemplate) {
        this.fileRepository = fileRepository;
        this.jdbcTemplate = jdbcTemplate;
    }



    public FileTable insertFileMetaDataWithProcessingStatus(String fileName) {
        FileTable dbFileTable = new FileTable();
        dbFileTable.setName(fileName);
        dbFileTable.setStatus(0); // Mark as processing
        dbFileTable = fileRepository.save(dbFileTable);

        return dbFileTable;
    }

    public void updateFileMetaDataWithCompletedStatus(FileTable fileTable) {
        fileTable.setStatus(1);
        fileRepository.save(fileTable);
    }

    public File uploadFileToS3(MultipartFile file) throws IOException, ApplixException {
        // For now, we are saving the files in server
        // at location resources/uploads/

        if (file.isEmpty()) throw new ApplixException("Empty file");

        String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename(); // Avoid name conflicts
        File uploadDirectory = new File(UPLOAD_DIR);
        if (!uploadDirectory.exists()) {
            boolean mkdirs = uploadDirectory.mkdirs(); // Ensure directory exists
            if(!mkdirs) throw new ApplixException("File Could Not Be Saved! Try Again");
        }

        File savedFile = new File(uploadDirectory, fileName);
        try (FileOutputStream fos = new FileOutputStream(savedFile)) {
            fos.write(file.getBytes());
        }
        return savedFile;
    }

    public List<FilteredData> processFile(File file, int fileId) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            return br.lines()
                    .parallel()
                    .filter(line -> !line.trim().isEmpty())
                    .map(line -> this.parseLine(line, fileId))
                    .filter(Objects::nonNull)
                    .toList();
        }
    }

    public void processFileStreaming(File file, int fileId) throws IOException {
        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            List<FilteredData> chunk = new ArrayList<>();
            String line;
            int batchSize = 10_000;

            while ((line = reader.readLine()) != null) {
                FilteredData data = parseLine(line, fileId);
                if (data != null) chunk.add(data);

                if (chunk.size() >= batchSize) {
                    List<FilteredData> batch = new ArrayList<>(chunk);
                    executor.execute(() -> batchInsert(batch));
                    chunk.clear();
                }
            }

            if (!chunk.isEmpty()) {
                executor.execute(() -> batchInsert(chunk));
            }
        } catch (Exception e) {
            System.out.println("Exception caught : " + e.getMessage());
        } finally {
            System.out.println("Finally reached!");
            executor.shutdown();
        }
    }

    public void batchInsert(List<FilteredData> records) {
        int totalRecords = records.size();
        for (int i = 0; i < totalRecords; i += BATCH_SIZE) {
            System.out.println(i);
            int end = Math.min(i + BATCH_SIZE, totalRecords);
            List<FilteredData> batch = records.subList(i, end);

            // Constructing a single large SQL query
            StringBuilder sql = new StringBuilder("INSERT INTO filtered_data (timestamp, temperature, file_id) VALUES ");
            List<Object> params = new ArrayList<>();

            for (FilteredData data : batch) {
                sql.append("(?, ?, ?),");
                params.add(java.sql.Timestamp.valueOf(data.getTimestamp()));
                params.add(data.getTemperature());
                params.add(data.getFileId());
            }

            sql.setLength(sql.length() - 1); // Remove last comma

            // Execute a single insert query with all 10,000 records
            jdbcTemplate.update(sql.toString(), params.toArray());

            System.out.println("✅ Inserted " + batch.size() + " records in one query");
        }
    }

    public File convertMultipartFileToFile(MultipartFile multipartFile) throws IOException {
        File tempFile = File.createTempFile("upload_", "_" + multipartFile.getOriginalFilename());
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.write(multipartFile.getBytes());
        }
        return tempFile;
    }


    private FilteredData parseLine(String line, Integer fileId) {
        try {
            String[] values = line.split(",");
            if (values.length != 2) return null;

            FilteredData record = new FilteredData();
            record.setTimestamp(java.time.LocalDateTime.parse(values[0].trim(), java.time.format.DateTimeFormatter.ISO_DATE_TIME));
            record.setTemperature(Double.parseDouble(values[1].trim()));
            record.setFileId(fileId);

            return record;
        } catch (Exception e) {
            return null;
        }
    }
}
