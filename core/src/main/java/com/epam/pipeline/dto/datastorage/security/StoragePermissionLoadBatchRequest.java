package com.epam.pipeline.dto.datastorage.security;

import lombok.Value;

import java.util.List;

@Value
public class StoragePermissionLoadBatchRequest {

    Long id;
    StorageKind type;
    List<StoragePermissionLoadRequest> requests;
}
