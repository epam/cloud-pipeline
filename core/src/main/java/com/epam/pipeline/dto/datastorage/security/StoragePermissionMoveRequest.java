package com.epam.pipeline.dto.datastorage.security;

import lombok.Value;

@Value
public class StoragePermissionMoveRequest {

    StoragePermissionMoveRequestObject source;
    StoragePermissionMoveRequestObject destination;

    public static StoragePermissionMoveRequestObject object(final String path, final StoragePermissionPathType type) {
        return new StoragePermissionMoveRequestObject(path, type);
    }

    @Value
    public static class StoragePermissionMoveRequestObject {
        String path;
        StoragePermissionPathType type;
    }
}
