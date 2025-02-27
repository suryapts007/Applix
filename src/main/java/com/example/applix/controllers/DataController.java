package com.example.applix.controllers;

import com.example.applix.enums.ErrorCode;
import com.example.applix.exceptions.ApplixException;
import com.example.applix.models.db.FileTable;
import com.example.applix.models.db.FilteredData;
import com.example.applix.models.responses.GetDataResponse;
import com.example.applix.models.responses.GetFilesResponse;
import com.example.applix.models.responses.UploadResponse;
import com.example.applix.services.DataService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;


import java.util.List;


@RestController
@RequestMapping("/data")
@CrossOrigin(origins = "http://localhost:5173") // Allow only frontend
// TODO :: make it work in production
public class DataController {
    private final DataService dataService;

    public DataController(DataService dataService) {
        this.dataService = dataService;
    }


    @PostMapping("/upload_async")
    public UploadResponse uploadAsync(@RequestParam("file") MultipartFile file) {
        try {
            String recordCount = dataService.uploadFileAsync(file);
            return new UploadResponse(ErrorCode.NO_ERROR, "File Saved Successfully", 0);
        } catch (ApplixException e) {
            return new UploadResponse(ErrorCode.FILE_NOT_FOUND, "File Not Found", 0);
        } catch (Exception e) {
            return new UploadResponse(ErrorCode.GENERIC_ERROR, "Error : " + e.getMessage(), 0);
        }
    }

    @PostMapping("/upload")
    public UploadResponse upload(@RequestParam("file") MultipartFile file) {
        try {
            String recordCount = dataService.uploadFileAsync(file);
            return new UploadResponse(ErrorCode.NO_ERROR, "success", 0);
        } catch (ApplixException e) {
            return new UploadResponse(ErrorCode.FILE_NOT_FOUND, "File Not Found", 0);
        } catch (Exception e) {
            return new UploadResponse(ErrorCode.GENERIC_ERROR, "Error : " + e.getMessage(), 0);
        }
    }


    @GetMapping
    public GetDataResponse getData(@RequestParam("fileId") Integer fileId, @RequestParam("page") Integer pageNo, @RequestParam("offset") Integer offSet) {
        try {
            List<FilteredData> data = dataService.getData(fileId, pageNo, offSet);
            int totalPageCount = dataService.getTotalPageCount(fileId, offSet);
            return new GetDataResponse(data, totalPageCount, "success", ErrorCode.NO_ERROR);
        } catch (Exception e) {
            return new GetDataResponse(null, null, "Error : " + e.getMessage(), ErrorCode.GENERIC_ERROR);
        }
    }


    @GetMapping("/filter")
    public GetDataResponse getDataByTimeDelta(@RequestParam Integer fileId, @RequestParam String start, @RequestParam String end) {
        try {
            List<FilteredData> dataByTimeDelta = dataService.getDataByTimeDelta(fileId, start, end);
            return new GetDataResponse(dataByTimeDelta, null, "success", ErrorCode.NO_ERROR);
        } catch (Exception e) {
            return new GetDataResponse(null, null, "Error : " + e.getMessage(), ErrorCode.GENERIC_ERROR);
        }
    }


    @GetMapping("/files")
    public GetFilesResponse getFiles() {
        try {
            List<FileTable> fileTables = dataService.getUploadedFilesWithStatusZeroOrOne();
            return new GetFilesResponse(fileTables, "success", ErrorCode.NO_ERROR);
        } catch (Exception e) {
            return new GetFilesResponse(null, "Error : " + e.getMessage(), ErrorCode.GENERIC_ERROR);
        }
    }
}