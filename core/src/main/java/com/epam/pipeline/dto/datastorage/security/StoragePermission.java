package com.epam.pipeline.dto.datastorage.security;

import lombok.Value;

import java.time.LocalDateTime;

@Value
public class StoragePermission {

    String path;
    StoragePermissionPathType type;
    StoragePermissionSid sid;
    int mask;
    LocalDateTime created;
}
