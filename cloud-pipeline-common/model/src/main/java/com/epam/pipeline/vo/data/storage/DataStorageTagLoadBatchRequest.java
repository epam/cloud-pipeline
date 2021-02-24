package com.epam.pipeline.vo.data.storage;

import lombok.Value;

import java.util.List;

@Value
public class DataStorageTagLoadBatchRequest {
    
    List<DataStorageTagLoadRequest> requests;
}
