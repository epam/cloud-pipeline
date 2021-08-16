package com.epam.pipeline.dto.datastorage.security;

import lombok.Value;

import java.util.List;

@Value
public class StoragePermissionDeleteBatchRequest {

    Long id;
    StoragePermissionType type;
    List<StoragePermissionDeleteRequest> requests;
}
