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
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.DoubleSummaryStatistics;
import java.util.PriorityQueue;
import java.util.concurrent.TimeUnit;

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

    @Deprecated
    public List<FilteredData> processFile(File file, int fileId) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            return br.lines()
                    .parallel()
                    .filter(line -> !line.trim().isEmpty())
                    .map(line -> this.parseAndFilterLine(line, fileId))
                    .filter(Objects::nonNull)
                    .toList();
        }
    }

    public List<Double> processFileStreaming(File file, int fileId) throws IOException {
        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        DoubleSummaryStatistics stats = new DoubleSummaryStatistics();
        PriorityQueue<Double> minHeap = new PriorityQueue<>();
        PriorityQueue<Double> maxHeap = new PriorityQueue<>((a, b) -> Double.compare(b, a));

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            List<FilteredData> chunk = new ArrayList<>();
            String line;

            while ((line = reader.readLine()) != null) {
                FilteredData data = parseAndFilterLine(line, fileId);

                if (data != null) {
                    chunk.add(data);
                    stats.accept(data.getTemperature());
                    addNumberToHeapsForMedianData(data.getTemperature(), minHeap, maxHeap);
                }

                if (chunk.size() >= BATCH_SIZE) {
                    List<FilteredData> batch = new ArrayList<>(chunk);
                    executor.execute(() -> batchInsert(batch));
                    chunk.clear();
                }
            }

            if (!chunk.isEmpty()) {
                executor.execute(() -> batchInsert(chunk));
            }

        } catch (Exception e) {
            System.out.println("Exception caught while processFileStreaming Execution : " + e.getMessage());
        } finally {
            executor.shutdown(); // Stop accepting new tasks
            try {
                if (!executor.awaitTermination(10, TimeUnit.MINUTES)) { // Wait for all tasks to finish
                    System.out.println("Forcing shutdown as tasks took too long!");
                    executor.shutdownNow(); // Force shutdown if not finished
                }
            } catch (InterruptedException e) {
                System.out.println("Thread was interrupted while waiting for executor to finish.");
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            System.out.println("Successfully completed all tasks.");
        }

        double mean = stats.getAverage();
        double median = getMedian(minHeap, maxHeap);
        return List.of(mean, median);
    }


    private FilteredData parseAndFilterLine(String line, Integer fileId) {
        try {
            String[] values = line.split(",");
            if (values.length != 2) return null;

            String timestampStr = values[0].trim();
            String temperatureStr = values[1].trim();

            // Validate timestamp
            Instant instant;
            try {
                instant = Instant.parse(timestampStr);
            } catch (java.time.format.DateTimeParseException e) {
                return null; // Invalid timestamp format
            }

            // Validate temperature
            double temperature;
            try {
                temperature = Double.parseDouble(temperatureStr);
            } catch (NumberFormatException e) {
                return null; // Invalid temperature format
            }

            // Additional filtering: temperature range check
            if (temperature < -100 || temperature > 100) {
                return null; // Temperature out of realistic range
            }

            FilteredData record = new FilteredData();
            record.setTimestampInstant(instant);
            record.setTemperature(temperature);
            record.setFileId(fileId);

            return record;
        } catch (Exception e) {
            return null;
        }
    }


    public void batchInsert(List<FilteredData> records) {
        int totalRecords = records.size();
        for (int i = 0; i < totalRecords; i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, totalRecords);
            List<FilteredData> batch = records.subList(i, end);

            // Constructing a single large SQL query
            StringBuilder sql = new StringBuilder("INSERT INTO filtered_data (timestamp, temperature, file_id) VALUES ");
            List<Object> params = new ArrayList<>();

            for (FilteredData data : batch) {
                sql.append("(?, ?, ?),");
                params.add(Timestamp.from(data.getTimestampInstant()));
                params.add(data.getTemperature());
                params.add(data.getFileId());
            }

            sql.setLength(sql.length() - 1); // Remove last comma

            // Execute a single insert query with all BATCH_SIZE records
            jdbcTemplate.update(sql.toString(), params.toArray());

            System.out.println("✅ Filtered and Added " + batch.size() + " records in DB");
        }
    }

    public File convertMultipartFileToFile(MultipartFile multipartFile) throws IOException {
        File tempFile = File.createTempFile("upload_", "_" + multipartFile.getOriginalFilename());
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.write(multipartFile.getBytes());
        }
        return tempFile;
    }

    private void addNumberToHeapsForMedianData(double num, PriorityQueue<Double> minHeap, PriorityQueue<Double> maxHeap) {
        if (maxHeap.isEmpty() || num <= maxHeap.peek()) {
            maxHeap.offer(num);
        } else {
            minHeap.offer(num);
        }

        if (maxHeap.size() > minHeap.size() + 1) {
            minHeap.offer(maxHeap.poll());
        } else if (minHeap.size() > maxHeap.size()) {
            maxHeap.offer(minHeap.poll());
        }
    }

    private double getMedian(PriorityQueue<Double> minHeap, PriorityQueue<Double> maxHeap) {
        if(maxHeap.size() == 0) return 0;
        if (maxHeap.size() == minHeap.size()) {
            return (maxHeap.peek() + minHeap.peek()) / 2.0;
        } else {
            return maxHeap.peek();
        }
    }

}
