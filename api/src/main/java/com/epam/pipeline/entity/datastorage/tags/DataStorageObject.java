package com.epam.pipeline.entity.datastorage.tags;

import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.experimental.Wither;

@Value
@Wither
@AllArgsConstructor
public class DataStorageObject {
    
    String root;
    String path;
    String version;

    public DataStorageObject(final String root, final String path) {
        this(root, path, null);
    }
}
