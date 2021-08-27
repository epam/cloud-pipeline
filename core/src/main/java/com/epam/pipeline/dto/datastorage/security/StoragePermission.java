package com.epam.pipeline.dto.datastorage.security;

import lombok.Value;
import lombok.experimental.Wither;

import java.time.LocalDateTime;

@Value
@Wither
public class StoragePermission {

    String path;
    StoragePermissionPathType type;
    StoragePermissionSid sid;
    int mask;
    LocalDateTime created;
}
