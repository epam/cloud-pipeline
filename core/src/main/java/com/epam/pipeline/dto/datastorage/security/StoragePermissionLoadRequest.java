package com.epam.pipeline.dto.datastorage.security;

import lombok.Value;

@Value
public class StoragePermissionLoadRequest {

    String path;
    StoragePermissionPathType type;
}
