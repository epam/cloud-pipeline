package com.epam.pipeline.manager.datastorage.security;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dao.datastorage.DataStorageDao;
import com.epam.pipeline.dto.datastorage.security.StorageKind;
import com.epam.pipeline.dto.datastorage.security.StoragePermission;
import com.epam.pipeline.dto.datastorage.security.StoragePermissionMoveBatchRequest;
import com.epam.pipeline.dto.datastorage.security.StoragePermissionMoveRequest;
import com.epam.pipeline.dto.datastorage.security.StoragePermissionDeleteAllBatchRequest;
import com.epam.pipeline.dto.datastorage.security.StoragePermissionDeleteAllRequest;
import com.epam.pipeline.dto.datastorage.security.StoragePermissionDeleteBatchRequest;
import com.epam.pipeline.dto.datastorage.security.StoragePermissionDeleteRequest;
import com.epam.pipeline.dto.datastorage.security.StoragePermissionInsertBatchRequest;
import com.epam.pipeline.dto.datastorage.security.StoragePermissionInsertRequest;
import com.epam.pipeline.dto.datastorage.security.StoragePermissionLoadAllRequest;
import com.epam.pipeline.dto.datastorage.security.StoragePermissionLoadBatchRequest;
import com.epam.pipeline.dto.datastorage.security.StoragePermissionLoadRequest;
import com.epam.pipeline.dto.datastorage.security.StoragePermissionPathType;
import com.epam.pipeline.dto.datastorage.security.StoragePermissionSid;
import com.epam.pipeline.dto.datastorage.security.StoragePermissionSidType;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.security.StoragePermissionEntity;
import com.epam.pipeline.entity.datastorage.security.StoragePermissionEntityId;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.mapper.datastorage.security.StoragePermissionMapper;
import com.epam.pipeline.repository.datastorage.security.StoragePermissionRepository;
import com.epam.pipeline.utils.StreamUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StoragePermissionBatchManager {

    private final StoragePermissionRepository repository;
    private final StoragePermissionMapper mapper;
    private final StoragePermissionProviderManager permissionProviderManager;
    private final DataStorageDao storageDao;
    private final MessageHelper messageHelper;

    @Transactional
    public List<StoragePermission> upsert(final StoragePermissionInsertBatchRequest request) {
        if (CollectionUtils.isEmpty(request.getRequests())) {
            return Collections.emptyList();
        }
        validate(request);
        final AbstractDataStorage storage = storageDao.loadDataStorage(request.getId());
        final Long root = getRoot(storage, request.getId());
        return StreamUtils.from(repository.save(request.getRequests().stream()
                .peek(r -> assertWriteAccess(storage, r.getPath(), r.getType()))
                .map(r -> permissionEntityFrom(r, root))
                .collect(Collectors.toList())))
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }

    private void validate(final StoragePermissionInsertBatchRequest request) {
        validateId(request.getId());
        validateKind(request.getType());
        for (final StoragePermissionInsertRequest r : request.getRequests()) {
            validatePath(r.getPath());
            validatePathType(r.getType());
            validateSid(r.getSid());
        }
    }

    private StoragePermissionEntity permissionEntityFrom(final StoragePermissionInsertRequest request,
                                                         final Long root) {
        return mapper.toEntity(request).toBuilder().datastorageRootId(root).created(DateUtils.nowUTC()).build();
    }

    @Transactional
    public void move(final StoragePermissionMoveBatchRequest request) {
        if (CollectionUtils.isEmpty(request.getRequests())) {
            return;
        }
        validate(request);
        final AbstractDataStorage storage = storageDao.loadDataStorage(request.getId());
        for (StoragePermissionMoveRequest r : request.getRequests()) {
            if (r.getSource().getType() == StoragePermissionPathType.FILE) {
                assertReadWriteAccess(storage, r.getSource().getPath(), r.getSource().getType());
                assertWriteAccess(storage, r.getDestination().getPath(), r.getDestination().getType());
            } else {
                assertFolderRecursiveReadWriteAccess(storage, r.getSource().getPath());
                assertFolderRecursiveWriteAccess(storage, r.getDestination().getPath());
            }
            permissionProviderManager.moveFolderPermissions(storage, r.getSource().getPath(), r.getDestination().getPath());
        }
    }

    private void validate(final StoragePermissionMoveBatchRequest request) {
        validateId(request.getId());
        validateKind(request.getType());
        for (final StoragePermissionMoveRequest r : request.getRequests()) {
            validate(r.getSource());
            validate(r.getDestination());
        }
    }

    private void validate(final StoragePermissionMoveRequest.StoragePermissionMoveRequestObject object) {
        Assert.notNull(object, messageHelper.getMessage(MessageConstants.ERROR_STORAGE_PERMISSION_MISSING));
        validatePath(object.getPath());
        validatePathType(object.getType());
    }

    @Transactional
    public void delete(final StoragePermissionDeleteBatchRequest request) {
        if (CollectionUtils.isEmpty(request.getRequests())) {
            return;
        }
        validate(request);
        final AbstractDataStorage storage = storageDao.loadDataStorage(request.getId());
        final Long root = getRoot(storage, request.getId());
        for (StoragePermissionDeleteRequest r : request.getRequests()) {
            assertWriteAccess(storage, r.getPath(), r.getType());
            repository.delete(permissionEntityIdFrom(r, root));
        }
    }

    private void validate(final StoragePermissionDeleteBatchRequest request) {
        validateId(request.getId());
        validateKind(request.getType());
        for (final StoragePermissionDeleteRequest r : request.getRequests()) {
            validatePath(r.getPath());
            validatePathType(r.getType());
            validateSid(r.getSid());
        }
    }

    private StoragePermissionEntityId permissionEntityIdFrom(final StoragePermissionDeleteRequest request,
                                                             final Long root) {
        return mapper.toEntityId(request).toBuilder().datastorageRootId(root).build();
    }

    @Transactional
    public void deleteAll(final StoragePermissionDeleteAllBatchRequest request) {
        if (CollectionUtils.isEmpty(request.getRequests())) {
            return;
        }
        validate(request);
        final AbstractDataStorage storage = storageDao.loadDataStorage(request.getId());
        final Long root = getRoot(storage, request.getId());
        for (StoragePermissionDeleteAllRequest r : request.getRequests()) {
            if (r.getType() == StoragePermissionPathType.FILE) {
                assertWriteAccess(storage, r.getPath(), r.getType());
                repository.deleteFilePermissions(root, r.getPath());
            } else {
                assertFolderRecursiveWriteAccess(storage, r.getPath());
                repository.deleteFolderPermissions(root, r.getPath());
            }
        }
    }

    private void validate(final StoragePermissionDeleteAllBatchRequest request) {
        validateId(request.getId());
        validateKind(request.getType());
        for (final StoragePermissionDeleteAllRequest r : request.getRequests()) {
            validatePath(r.getPath());
            validatePathType(r.getType());
        }
    }

    @Transactional
    public List<StoragePermission> load(final StoragePermissionLoadBatchRequest request) {
        if (CollectionUtils.isEmpty(request.getRequests())) {
            return Collections.emptyList();
        }
        validate(request);
        final Long root = getRoot(request.getId());
        return request.getRequests().stream()
                .map(r -> repository.findByRootAndPathAndType(root, r.getPath(), r.getType()))
                .flatMap(List::stream)
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }

    private void validate(final StoragePermissionLoadBatchRequest request) {
        validateId(request.getId());
        validateKind(request.getType());
        for (final StoragePermissionLoadRequest r : request.getRequests()) {
            validatePath(r.getPath());
            validatePathType(r.getType());
        }
    }

    @Transactional
    public List<StoragePermission> loadAll(final StoragePermissionLoadAllRequest request) {
        validate(request);
        final Long root = getRoot(request.getId());
        return repository.findByRoot(root)
                .stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }

    private void validate(final StoragePermissionLoadAllRequest request) {
        validateId(request.getId());
        validateKind(request.getType());
    }

    private void validateId(final Long id) {
        Assert.notNull(id, messageHelper.getMessage(MessageConstants.ERROR_STORAGE_PERMISSION_STORAGE_ID_MISSING));
    }

    private void validateKind(final StorageKind kind) {
        Assert.notNull(kind, messageHelper.getMessage(MessageConstants.ERROR_STORAGE_PERMISSION_STORAGE_KIND_MISSING));
    }

    private void validatePath(final String path) {
        Assert.notNull(path, messageHelper.getMessage(MessageConstants.ERROR_STORAGE_PERMISSION_PATH_MISSING));
    }

    private void validatePathType(final StoragePermissionPathType type) {
        Assert.notNull(type, messageHelper.getMessage(MessageConstants.ERROR_STORAGE_PERMISSION_PATH_TYPE_MISSING));
    }

    private void validateSid(final StoragePermissionSid sid) {
        Assert.notNull(sid, messageHelper.getMessage(MessageConstants.ERROR_STORAGE_PERMISSION_SID_MISSING));
        validateSidName(sid.getName());
        validateSidType(sid.getType());
    }

    private void validateSidName(final String sidName) {
        Assert.notNull(sidName, messageHelper.getMessage(MessageConstants.ERROR_STORAGE_PERMISSION_SID_NAME_MISSING));
    }

    private void validateSidType(final StoragePermissionSidType sidType) {
        Assert.notNull(sidType, messageHelper.getMessage(MessageConstants.ERROR_STORAGE_PERMISSION_SID_TYPE_MISSING));
    }

    private Long getRoot(final Long storageId) {
        return getRoot(storageDao.loadDataStorage(storageId), storageId);
    }

    private Long getRoot(final AbstractDataStorage storage, final Long storageId) {
        return Optional.ofNullable(storage)
                .map(AbstractDataStorage::getRootId)
                .orElseThrow(() -> new IllegalArgumentException(messageHelper.getMessage(
                        MessageConstants.ERROR_DATASTORAGE_NOT_FOUND, storageId)));
    }

    private void assertReadWriteAccess(final AbstractDataStorage storage,
                                       final String path,
                                       final StoragePermissionPathType type) {
        if (permissionProviderManager.isReadWriteNotAllowed(storage, path, type)) {
            throw new AccessDeniedException(String.format("Data storage path %s read and write is not allowed.", path));
        }
    }

    private void assertWriteAccess(final AbstractDataStorage storage,
                                   final String path,
                                   final StoragePermissionPathType type) {
        if (permissionProviderManager.isWriteNotAllowed(storage, path, type)) {
            throw new AccessDeniedException(String.format("Data storage path %s write is not allowed.", path));
        }
    }

    private void assertFolderRecursiveWriteAccess(final AbstractDataStorage storage, final String path) {
        if (permissionProviderManager.isRecursiveWriteNotAllowed(storage, path)) {
            throw new AccessDeniedException(String.format("Data storage path %s recursive write is not allowed.",
                    path));
        }
    }

    private void assertFolderRecursiveReadWriteAccess(final AbstractDataStorage storage, final String path) {
        if (permissionProviderManager.isRecursiveReadWriteNotAllowed(storage, path)) {
            throw new AccessDeniedException(String.format("Data storage path %s recursive read/write is not allowed.",
                    path));
        }
    }
}
