package com.epam.pipeline.vo.data.storage;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.List;

@Value
public class DataStorageTagLoadBatchRequest {
    
    List<DataStorageTagLoadRequest> requests;

    @JsonCreator
    public DataStorageTagLoadBatchRequest(
            @JsonProperty("requests") final List<DataStorageTagLoadRequest> requests) {
        this.requests = requests;
    }
}
