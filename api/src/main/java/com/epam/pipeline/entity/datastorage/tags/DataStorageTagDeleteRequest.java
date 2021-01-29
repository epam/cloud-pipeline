package com.epam.pipeline.entity.datastorage.tags;

import lombok.Value;

@Value
public class DataStorageTagDeleteRequest {
    
    String path;
    String version;
}
