package com.epam.pipeline.dto.datastorage.security;

import lombok.Value;

import java.util.List;

@Value
public class StoragePermissionDeleteAllBatchRequest {

    Long id;
    StorageKind type;
    List<StoragePermissionDeleteAllRequest> requests;
}
