package com.example.applix.models.responses;

import com.example.applix.enums.ErrorCode;
import com.example.applix.models.db.File;
import com.example.applix.models.db.FilteredData;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class GetFilesResponse {
    public List<File> files;
    public String message;
    public ErrorCode errorCode;
}
