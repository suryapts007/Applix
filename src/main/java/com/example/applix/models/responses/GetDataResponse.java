package com.example.applix.models.responses;

import com.example.applix.enums.ErrorCode;
import com.example.applix.models.db.RawData;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class GetDataResponse {
    public List<RawData> records;
    public String message;
    public ErrorCode errorCode;
}
