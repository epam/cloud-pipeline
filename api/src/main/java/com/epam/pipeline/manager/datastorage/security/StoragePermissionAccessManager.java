package com.epam.pipeline.manager.datastorage.security;

import com.epam.pipeline.entity.SecuredStorageEntity;
import com.epam.pipeline.repository.datastorage.security.StoragePermissionRepository;

import java.util.Set;

public interface StoragePermissionAccessManager {

    Set<StoragePermissionRepository.Storage> loadReadAllowedStorages();

    boolean isReadAllowed(SecuredStorageEntity storage);
}
