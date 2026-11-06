package com.epam.pipeline.vo.data.storage;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.List;

@Value
public class DataStorageTagInsertBatchRequest {
    
    List<DataStorageTagInsertRequest> requests;

    @JsonCreator
    public DataStorageTagInsertBatchRequest(
           @JsonProperty("requests") final List<DataStorageTagInsertRequest> requests) {
        this.requests = requests;
    }
}
