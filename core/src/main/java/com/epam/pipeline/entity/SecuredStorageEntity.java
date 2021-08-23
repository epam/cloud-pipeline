package com.epam.pipeline.entity;

import com.epam.pipeline.dto.datastorage.security.StorageKind;

public interface SecuredStorageEntity {

    Long getId();
    Long getRootId();
    String getOwner();
    String resolveAbsolutePath(String relativePath);
    boolean isVersioningEnabled();
    StorageKind getKind();
}
