package com.epam.pipeline.dto.datastorage.security;

import lombok.Value;

@Value
public class StoragePermissionDeleteAllRequest {

    String path;
    StoragePermissionPathType type;
}
