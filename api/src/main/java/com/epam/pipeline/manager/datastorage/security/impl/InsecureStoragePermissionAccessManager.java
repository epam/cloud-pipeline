package com.epam.pipeline.manager.datastorage.security.impl;

import com.epam.pipeline.entity.SecuredStorageEntity;
import com.epam.pipeline.manager.datastorage.security.StoragePermissionAccessManager;
import com.epam.pipeline.repository.datastorage.security.StoragePermissionRepository;

import java.util.Collections;
import java.util.Set;

public class InsecureStoragePermissionAccessManager implements StoragePermissionAccessManager {

    @Override
    public Set<StoragePermissionRepository.Storage> loadReadAllowedStorages() {
        return Collections.emptySet();
    }

    @Override
    public boolean isReadAllowed(final SecuredStorageEntity storage) {
        return false;
    }

}
