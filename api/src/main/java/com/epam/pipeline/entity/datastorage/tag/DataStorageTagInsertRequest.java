package com.epam.pipeline.entity.datastorage.tag;

public record DataStorageTagInsertRequest(
        String path,
        String version,
        String key,
        String value) {}
