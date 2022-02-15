package com.epam.pipeline.dto.datastorage.security;

import lombok.Value;

import java.util.List;

@Value
public class StoragePermissionInsertBatchRequest {

    Long id;
    StorageKind type;
    List<StoragePermissionInsertRequest> requests;
}
