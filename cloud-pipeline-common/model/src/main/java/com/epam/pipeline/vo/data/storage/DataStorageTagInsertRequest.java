package com.epam.pipeline.vo.data.storage;

import lombok.Value;

@Value
public class DataStorageTagInsertRequest {
    
    String path;
    String version;
    String key;
    String value;
}
