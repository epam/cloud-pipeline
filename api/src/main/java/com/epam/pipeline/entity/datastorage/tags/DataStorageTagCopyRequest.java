package com.epam.pipeline.entity.datastorage.tags;

import lombok.Value;

@Value
public class DataStorageTagCopyRequest {

    DataStorageTagCopyRequestObject source;
    DataStorageTagCopyRequestObject destination;

    @Value
    public static class DataStorageTagCopyRequestObject {
        String path;
        String version;
    }
}
