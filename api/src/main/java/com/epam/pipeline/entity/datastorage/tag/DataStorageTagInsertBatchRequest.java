package com.epam.pipeline.entity.datastorage.tag;

import lombok.Value;

import java.util.List;

@Value
public class DataStorageTagInsertBatchRequest {
    
    List<DataStorageTagInsertRequest> requests;
}
