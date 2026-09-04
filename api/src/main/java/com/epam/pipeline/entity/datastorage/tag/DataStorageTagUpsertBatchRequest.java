package com.epam.pipeline.entity.datastorage.tag;

import java.util.List;

public record DataStorageTagUpsertBatchRequest(List<DataStorageTagUpsertRequest> requests) {}
