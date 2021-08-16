package com.epam.pipeline.dto.datastorage.security;

import lombok.Value;

@Value
public class StoragePermissionDeleteRequest {

    String path;
    StoragePermissionPathType type;
    StoragePermissionSid sid;
}
