package com.example.applix.models.responses;

import com.example.applix.enums.ErrorCode;
import com.example.applix.models.db.FilteredData;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class GetDataResponse {
    public List<FilteredData> records;
    public Integer totalPages;
    public String message;
    public ErrorCode errorCode;
}
