package com.epam.pipeline.manager.datastorage.security.impl;

import com.epam.pipeline.entity.SecuredStorageEntity;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.manager.datastorage.security.StoragePermissionAccessManager;
import com.epam.pipeline.manager.datastorage.security.StoragePermissionManager;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.repository.datastorage.security.StoragePermissionRepository;
import lombok.RequiredArgsConstructor;

import java.util.Set;

@RequiredArgsConstructor
public class SecureStoragePermissionAccessManager implements StoragePermissionAccessManager {

    private final StoragePermissionManager storagePermissionManager;
    private final AuthManager authManager;

    @Override
    public Set<StoragePermissionRepository.Storage> loadReadAllowedStorages() {
        final PipelineUser user = authManager.getCurrentUserOrFail();
        return storagePermissionManager.loadReadAllowedStorages(user.getUserName(), user.getGroupsAndRoles());
    }

    @Override
    public boolean isReadAllowed(final SecuredStorageEntity storage) {
        final PipelineUser user = authManager.getCurrentUserOrFail();
        return storagePermissionManager.isReadAllowed(storage.getRootId(), storage.getId(), user.getUserName(),
                user.getGroupsAndRoles());
    }

}
