package com.epam.pipeline.entity.datastorage.tags;

import lombok.Value;

import java.util.Map;

@Value
public class DataStorageTagBulkUpsertRequest {
    
    /**
     * {
     *     "storage/path": {
     *         "tag1": "value1",
     *         "tag2": "value2"
     *     },
     *     "another/storage/path": {
     *         "tag3": "value3",
     *         "tag4": "value4"
     *     }
     * }
     */
    Map<String, Map<String, String>> pathTags;
}
