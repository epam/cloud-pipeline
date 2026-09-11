package com.epam.pipeline.entity.datastorage;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;
import lombok.With;

@Value
@With
public class DataStorageObject {

    String path;
    String version;

    @JsonCreator
    public DataStorageObject(@JsonProperty("path") final String path,
                             @JsonProperty("version") final String version) {
        this.path = path;
        this.version = version;
    }

    public DataStorageObject(final String path) {
        this(path, null);
    }
}
