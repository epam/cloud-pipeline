package com.epam.pipeline.vo.data.storage;

import lombok.Value;

import java.util.List;

@Value
public class DataStorageTagInsertBatchRequest {
    
    List<DataStorageTagInsertRequest> requests;
}
