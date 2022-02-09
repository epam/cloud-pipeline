package com.epam.pipeline.dto.datastorage.security;

import lombok.Value;

@Value
public class StoragePermissionLoadAllRequest {

    Long id;
    StorageKind type;
}
