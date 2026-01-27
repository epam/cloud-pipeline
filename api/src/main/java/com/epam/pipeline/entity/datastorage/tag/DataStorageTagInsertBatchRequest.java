package com.epam.pipeline.entity.datastorage.tag;

import java.util.List;

public record DataStorageTagInsertBatchRequest(List<DataStorageTagInsertRequest> requests) {}
