package com.epam.pipeline.vo.data.storage;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value
public class DataStorageTagLoadRequest {
    
    String path;

    @JsonCreator
    public DataStorageTagLoadRequest(@JsonProperty("path") final String path) {
        this.path = path;
    }
}
