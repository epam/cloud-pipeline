package com.epam.pipeline.entity.datastorage.tag;

public record DataStorageTagDeleteRequest(
        String path,
        String version) {}
