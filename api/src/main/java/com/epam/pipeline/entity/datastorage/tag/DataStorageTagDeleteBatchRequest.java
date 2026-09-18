package com.epam.pipeline.entity.datastorage.tag;

import java.util.List;

public record DataStorageTagDeleteBatchRequest(
        List<DataStorageTagDeleteRequest> requests) {}
