package com.epam.pipeline.entity.datastorage;

import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.With;

@Value
@With
@AllArgsConstructor
public class DataStorageObject {

    String path;
    String version;

    public DataStorageObject(final String path) {
        this(path, null);
    }
}
