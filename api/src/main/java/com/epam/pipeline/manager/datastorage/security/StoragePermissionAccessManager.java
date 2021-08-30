package com.epam.pipeline.manager.datastorage.security;

import com.epam.pipeline.entity.SecuredStorageEntity;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.repository.datastorage.security.StoragePermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class StoragePermissionAccessManager {

    private final StoragePermissionManager storagePermissionManager;
    private final AuthManager authManager;

    public Set<StoragePermissionRepository.Storage> loadReadAllowedStorages() {
        final PipelineUser user = authManager.getCurrentUserOrFail();
        return storagePermissionManager.loadReadAllowedStorages(user.getUserName(), user.getGroupsAndRoles());
    }

    public boolean isReadAllowed(final SecuredStorageEntity storage) {
        final PipelineUser user = authManager.getCurrentUserOrFail();
        return storagePermissionManager.isReadAllowed(storage.getRootId(), storage.getId(), user.getUserName(),
                user.getGroupsAndRoles());
    }

}
