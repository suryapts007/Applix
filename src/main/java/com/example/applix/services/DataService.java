package com.example.applix.services;


import com.example.applix.exceptions.ApplixException;
import com.example.applix.models.db.RawData;
import com.example.applix.repositories.RawDataRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class DataService {
    private final RawDataRepository rawDataRepository;

    public DataService(RawDataRepository rawDataRepository) {
        this.rawDataRepository = rawDataRepository;
    }


    public int uploadFile(MultipartFile file) throws IOException, ApplixException {
        if (file.isEmpty()) {
            throw new ApplixException("Empty file");
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            List<RawData> records = br.lines().map(line -> {
                String[] values = line.split(",");

                RawData record = new RawData();
                record.setTimestamp(LocalDateTime.parse(values[0], DateTimeFormatter.ISO_DATE_TIME));
                record.setTemperature(Double.parseDouble(values[1]));
                record.setHumidity(Double.parseDouble(values[2]));

                return record;
            }).collect(Collectors.toList());

            rawDataRepository.saveAll(records);

            return records.size();
        }
    }


    public List<RawData> getData(Integer pageNumber, Integer offSet) {
        // TODO
        return rawDataRepository.findAll();
    }

    public List<RawData> getDataByTimeDelta(String start, String end) {
        LocalDateTime startTime = LocalDateTime.parse(start, DateTimeFormatter.ISO_DATE_TIME);
        LocalDateTime endTime = LocalDateTime.parse(end, DateTimeFormatter.ISO_DATE_TIME);

        return rawDataRepository.findByTimestampBetween(startTime, endTime);
    }
}
