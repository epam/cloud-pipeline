package com.epam.pipeline.dto.datastorage.security;

import lombok.Value;

@Value
public class StoragePermissionInsertRequest {

    String path;
    StoragePermissionPathType type;
    StoragePermissionSid sid;
    int mask;
}
