package com.epam.pipeline.entity.datastorage.tag;

public record DataStorageTagUpsertRequest(
        String path,
        String version,
        String key,
        String value) {}
