package com.epam.pipeline.entity.datastorage.tag;

import lombok.Value;

@Value
public class DataStorageTagDeleteRequest {
    
    String path;
    String version;
}
