package com.epam.pipeline.entity.datastorage.tag;

public record DataStorageObject(String path, String version) {
    public DataStorageObject(final String path) {
        this(path, null);
    }
}
