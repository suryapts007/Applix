package com.example.applix.controllers;

import com.example.applix.enums.ErrorCode;
import com.example.applix.exceptions.ApplixException;
import com.example.applix.models.db.FileTable;
import com.example.applix.models.responses.GetDataResponse;
import com.example.applix.models.responses.GetFilesResponse;
import com.example.applix.models.responses.UploadAsyncResponse;
import com.example.applix.models.responses.UploadResponse;
import com.example.applix.services.DataService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;


import java.util.List;


@RestController
@RequestMapping("/data")
@CrossOrigin(origins = "*") // Allow all origins
// TODO :: make it work in production
public class DataController {
    private final DataService dataService;

    public DataController(DataService dataService) {
        this.dataService = dataService;
    }


    // Files with size less that 100MB or 1 million rows
    @Deprecated
    @PostMapping("/upload_sync")
    public UploadResponse uploadFileSync(@RequestParam("file") MultipartFile file) {
        try {
            int recordCount = dataService.uploadFileSync(file);
            return new UploadResponse(ErrorCode.NO_ERROR, "success", recordCount);
        } catch (ApplixException e) {
            return new UploadResponse(ErrorCode.FILE_NOT_FOUND, "File Not Found", 0);
        } catch (Exception e) {
            return new UploadResponse(ErrorCode.GENERIC_ERROR, "Error : " + e.getMessage(), 0);
        }
    }


    // Files with size less than 1.5GB or 20 million rows
    @PostMapping("/upload_async")
    public UploadAsyncResponse uploadAsync(@RequestParam("file") MultipartFile file) {
        try {
            dataService.uploadFileAsync(file);
            return new UploadAsyncResponse(ErrorCode.NO_ERROR, "File uploaded successfully. Processing started.");
        } catch (ApplixException e) {
            return new UploadAsyncResponse(ErrorCode.FILE_NOT_FOUND, "File Not Found");
        } catch (Exception e) {
            return new UploadAsyncResponse(ErrorCode.GENERIC_ERROR, "Error : " + e.getMessage());
        }
    }


    @GetMapping
    public GetDataResponse getData(@RequestParam("fileId") Integer fileId, @RequestParam("page") Integer pageNo, @RequestParam("offset") Integer offSet, @RequestParam(value = "startTime", required = false) String startTime, @RequestParam(value = "endTime", required = false) String endTime) {
        try {
            return dataService.getData(fileId, pageNo, offSet, startTime, endTime);
        } catch (Exception e) {
            return new GetDataResponse(null, 0, 0L, 0L, 0.0, 0.0, "Error : " + e.getMessage(), ErrorCode.GENERIC_ERROR);
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