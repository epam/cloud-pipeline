package com.epam.pipeline.entity.datastorage.tag;

import lombok.Value;

@Value
public class DataStorageTagCopyRequest {

    DataStorageTagCopyRequestObject source;
    DataStorageTagCopyRequestObject destination;

    public static DataStorageTagCopyRequestObject object(final String path, final String version) {
        return new DataStorageTagCopyRequestObject(path, version);
    }

    @Value
    public static class DataStorageTagCopyRequestObject {
        String path;
        String version;
    }
}
