package com.epam.pipeline.acl.datastorage.security;

import com.epam.pipeline.dto.datastorage.security.StoragePermissionDeleteBatchRequest;
import com.epam.pipeline.dto.datastorage.security.StoragePermissionInsertBatchRequest;
import com.epam.pipeline.dto.datastorage.security.StoragePermission;
import com.epam.pipeline.dto.datastorage.security.StoragePermissionLoadBatchRequest;
import com.epam.pipeline.manager.datastorage.security.StoragePermissionBatchManager;
import com.epam.pipeline.security.acl.AclExpressions;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StoragePermissionBatchApiService {

    private final StoragePermissionBatchManager manager;

    @PreAuthorize(AclExpressions.STORAGE_ID_OWNER)
    public List<StoragePermission> upsert(final StoragePermissionInsertBatchRequest request) {
        return manager.upsert(request);
    }

    @PreAuthorize(AclExpressions.STORAGE_ID_OWNER)
    public void delete(final StoragePermissionDeleteBatchRequest request) {
        manager.delete(request);
    }

    @PreAuthorize(AclExpressions.STORAGE_ID_READ)
    public List<StoragePermission> load(final StoragePermissionLoadBatchRequest request) {
        return manager.load(request);
    }

}
