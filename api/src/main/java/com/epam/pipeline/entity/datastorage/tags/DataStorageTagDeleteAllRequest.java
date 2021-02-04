package com.epam.pipeline.entity.datastorage.tags;

import lombok.Value;

@Value
public class DataStorageTagDeleteAllRequest {

    String path;
    DataStorageTagDeleteAllRequestType type;

    public enum DataStorageTagDeleteAllRequestType {
        FILE, FOLDER
    }
}
