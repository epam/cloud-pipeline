package com.epam.pipeline.vo.data.storage;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.List;

@Value
public class DataStorageTagUpsertBatchRequest {
    
    List<DataStorageTagUpsertRequest> requests;

    @JsonCreator
    public DataStorageTagUpsertBatchRequest(
            @JsonProperty("requests") final List<DataStorageTagUpsertRequest> requests) {
        this.requests = requests;
    }
}
