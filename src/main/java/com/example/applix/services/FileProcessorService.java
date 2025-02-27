package com.example.applix.services;

import com.example.applix.exceptions.ApplixException;
import com.example.applix.models.db.FileTable;
import com.example.applix.models.db.FilteredData;
import com.example.applix.repositories.FileRepository;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

@Service
public class FileProcessorService {
    private final FileRepository fileRepository;
    private final JdbcTemplate jdbcTemplate;


    @Value("${file.upload-dir}")
    private String uploadDir;

    public FileProcessorService(FileRepository fileRepository, JdbcTemplate jdbcTemplate) {
        this.fileRepository = fileRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public File uploadFileToS3(MultipartFile file) throws IOException, ApplixException {
        // For now, we are saving the files in server
        // at location resources/uploads/

        if (file.isEmpty()) throw new ApplixException("Empty file");

        String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename(); // Avoid name conflicts
        File uploadDirectory = new File(uploadDir);
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

    public List<FilteredData> processFile(MultipartFile file, int fileId) throws IOException, ApplixException {
        BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));

        List<FilteredData> filteredData = br.lines()
                .parallel()
                .filter(line -> !line.trim().isEmpty())
                .map(line -> this.parseLine(line, fileId))
                .filter(Objects::nonNull)
                .toList();

        if (filteredData.isEmpty()) {
            throw new ApplixException("No valid data found in the file.");
        }

        return filteredData;
    }

    public FilteredData parseLine(String line, Integer fileId) {
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


    private static final int BATCH_SIZE = 1000;
    private static final String INSERT_SQL = "INSERT INTO filtered_data (timestamp, temperature, file_id) VALUES (?, ?, ?)";
    public void batchInsert(List<FilteredData> records) {
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

}
