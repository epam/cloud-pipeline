package com.epam.pipeline.dto.datastorage.security;

import lombok.Value;

import java.util.List;

@Value
public class StoragePermissionDeleteBatchRequest {

    List<StoragePermissionDeleteRequest> requests;
}
