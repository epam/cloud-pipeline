package com.epam.pipeline.entity.datastorage.tags;

import lombok.Value;

import java.util.List;

@Value
public class DataStorageTagBulkLoadRequest {
    
    /**
     * [
     *     "storage/path",
     *     "another/storage/path"
     * ]
     */
    List<String> paths;
}
