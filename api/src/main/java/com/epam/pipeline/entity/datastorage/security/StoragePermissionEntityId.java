package com.epam.pipeline.entity.datastorage.security;

import com.epam.pipeline.dto.datastorage.security.StoragePermissionPathType;
import com.epam.pipeline.dto.datastorage.security.StoragePermissionSidType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Builder(toBuilder = true)
public class StoragePermissionEntityId implements Serializable {

    private Long datastorageRootId;
    private String datastoragePath;
    private StoragePermissionPathType datastorageType;
    private String sidName;
    private StoragePermissionSidType sidType;
}
