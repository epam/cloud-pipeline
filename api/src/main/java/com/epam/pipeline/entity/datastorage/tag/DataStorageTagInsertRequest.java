package com.epam.pipeline.entity.datastorage.tag;

import lombok.Value;

@Value
public class DataStorageTagInsertRequest {
    
    String path;
    String version;
    String key;
    String value;
}
