package com.epam.pipeline.entity.datastorage.tags;

import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.experimental.Wither;

@Value
@Wither
@AllArgsConstructor
public class DataStorageObject {
    
    Long storageId;
    String path;
    String version;

    public DataStorageObject(final Long storageId, final String path) {
        this(storageId, path, null);
    }
}
