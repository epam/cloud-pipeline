package com.epam.pipeline.entity.datastorage;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Value;
import lombok.With;

@Value
@With
@AllArgsConstructor
@NoArgsConstructor(force = true)
public class DataStorageObject {

    String path;
    String version;

    public DataStorageObject(final String path) {
        this(path, null);
    }
}
