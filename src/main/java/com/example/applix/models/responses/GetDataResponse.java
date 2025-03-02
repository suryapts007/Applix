package com.example.applix.models.responses;

import com.example.applix.enums.ErrorCode;
import com.example.applix.models.db.FilteredData;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GetDataResponse {
    public List<FilteredData> records;
    public Integer totalPages;
    public Long totalRows;
    public Long totalFilteredRows;
    public Double meanTemperature;
    public Double medianTemperature;
    public String message;
    public ErrorCode errorCode;
}
