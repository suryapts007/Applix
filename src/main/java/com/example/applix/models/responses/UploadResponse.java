package com.example.applix.models.responses;

import com.example.applix.enums.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UploadResponse {
    public ErrorCode errorCodes;
    public String message;
    public Integer recordCount;
}
