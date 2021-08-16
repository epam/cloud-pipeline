package com.epam.pipeline.dto.datastorage.security;

import lombok.Value;

import java.util.List;

@Value
public class StoragePermissionInsertBatchRequest {

    Long id;
    StoragePermissionType type;
    List<StoragePermissionInsertRequest> requests;
}
