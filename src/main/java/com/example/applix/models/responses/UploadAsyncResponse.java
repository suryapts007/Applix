package com.example.applix.models.responses;

import com.example.applix.enums.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UploadAsyncResponse {
    public ErrorCode errorCodes;
    public String message;
}




