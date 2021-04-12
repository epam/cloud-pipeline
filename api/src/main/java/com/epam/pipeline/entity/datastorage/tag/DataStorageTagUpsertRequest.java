package com.epam.pipeline.entity.datastorage.tag;

import lombok.Value;

@Value
public class DataStorageTagUpsertRequest {
    
    String path;
    String version;
    String key;
    String value;
}
