package com.example.applix.models.responses;

import com.example.applix.enums.ErrorCode;
import com.example.applix.models.db.FileTable;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class GetFilesResponse {
    public List<FileTable> files;
    public String message;
    public ErrorCode errorCode;
}
