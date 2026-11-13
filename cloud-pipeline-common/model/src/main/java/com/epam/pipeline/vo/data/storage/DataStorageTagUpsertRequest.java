package com.epam.pipeline.vo.data.storage;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value
public class DataStorageTagUpsertRequest {
    
    String path;
    String version;
    String key;
    String value;

    @JsonCreator
    public DataStorageTagUpsertRequest(
            @JsonProperty("path") final String path,
            @JsonProperty("version") final String version,
            @JsonProperty("key") final String key,
            @JsonProperty("value") final String value) {
        this.path = path;
        this.version = version;
        this.key = key;
        this.value = value;
    }
}
