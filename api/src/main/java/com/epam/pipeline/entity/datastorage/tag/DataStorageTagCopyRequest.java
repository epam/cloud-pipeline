package com.epam.pipeline.entity.datastorage.tag;

public record DataStorageTagCopyRequest(DataStorageTagCopyRequestObject source,
                                        DataStorageTagCopyRequestObject destination) {

    public static DataStorageTagCopyRequestObject object(final String path, final String version) {
        return new DataStorageTagCopyRequestObject(path, version);
    }

    public record DataStorageTagCopyRequestObject(String path, String version) {
    }
}
