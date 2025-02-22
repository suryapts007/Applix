package com.example.applix.controllers;


import com.example.applix.enums.ErrorCode;
import com.example.applix.exceptions.ApplixException;
import com.example.applix.models.db.RawData;
import com.example.applix.models.responses.GetDataResponse;
import com.example.applix.models.responses.UploadResponse;
import com.example.applix.services.DataService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.example.applix.repositories.RawDataRepository;


import java.util.List;


@RestController
@RequestMapping("/data")
public class DataController {
    private final RawDataRepository repository;
    private final DataService dataService;

    public DataController(RawDataRepository repository, DataService dataService) {
        this.repository = repository;
        this.dataService = dataService;
    }


    @PostMapping("/upload")
    public UploadResponse upload(@RequestParam("file") MultipartFile file) {
        try {
            int recordCount = dataService.uploadFile(file);
            return new UploadResponse(ErrorCode.NO_ERROR, "success", recordCount);
        } catch (ApplixException e) {
            return new UploadResponse(ErrorCode.FILE_NOT_FOUND, "File Not Found", 0);
        } catch (Exception e) {
            return new UploadResponse(ErrorCode.GENERIC_ERROR, "Error : " + e.getMessage(), 0);
        }
    }


    @GetMapping
    public GetDataResponse getData(@RequestParam("page") Integer pageNo, @RequestParam("offset") Integer offSet) {
        try {
            List<RawData> data = dataService.getData(pageNo, offSet);
            return new GetDataResponse(data, "success", ErrorCode.NO_ERROR);
        } catch (Exception e) {
            return new GetDataResponse(null, "Error : " + e.getMessage(), ErrorCode.GENERIC_ERROR);
        }
    }


    @GetMapping("/filter")
    public GetDataResponse getDataByTimeDelta(@RequestParam String start, @RequestParam String end) {
        try {
            List<RawData> dataByTimeDelta = dataService.getDataByTimeDelta(start, end);
            return new GetDataResponse(dataByTimeDelta, "success", ErrorCode.NO_ERROR);
        } catch (Exception e) {
            return new GetDataResponse(null, "Error : " + e.getMessage(), ErrorCode.GENERIC_ERROR);
        }
    }
}