package com.epam.pipeline.entity.datastorage.tag;

import java.util.List;

public record DataStorageTagDeleteAllBatchRequest(
        List<DataStorageTagDeleteAllRequest> requests) {}
