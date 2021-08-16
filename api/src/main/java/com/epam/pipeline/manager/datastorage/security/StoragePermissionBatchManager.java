package com.epam.pipeline.manager.datastorage.security;

import com.epam.pipeline.dao.datastorage.DataStorageDao;
import com.epam.pipeline.dto.datastorage.security.StoragePermission;
import com.epam.pipeline.dto.datastorage.security.StoragePermissionDeleteBatchRequest;
import com.epam.pipeline.dto.datastorage.security.StoragePermissionDeleteRequest;
import com.epam.pipeline.dto.datastorage.security.StoragePermissionInsertBatchRequest;
import com.epam.pipeline.dto.datastorage.security.StoragePermissionInsertRequest;
import com.epam.pipeline.dto.datastorage.security.StoragePermissionLoadBatchRequest;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.security.StoragePermissionEntity;
import com.epam.pipeline.entity.datastorage.security.StoragePermissionEntityId;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.mapper.datastorage.security.StoragePermissionMapper;
import com.epam.pipeline.repository.datastorage.security.StoragePermissionRepository;
import com.epam.pipeline.utils.StreamUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class StoragePermissionBatchManager {

    private final StoragePermissionRepository repository;
    private final StoragePermissionMapper mapper;
    private final DataStorageDao storageDao;

    public List<StoragePermission> upsert(final StoragePermissionInsertBatchRequest request) {
        return getRoot(request.getId())
                .map(root -> request.getRequests().stream()
                        .map(r -> permissionEntityFrom(r, root))
                        .collect(Collectors.toList()))
                .map(repository::save)
                .map(StreamUtils::from)
                .orElseGet(Stream::empty)
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }

    private StoragePermissionEntity permissionEntityFrom(final StoragePermissionInsertRequest request,
                                                         final Long root) {
        return mapper.toEntity(request).toBuilder().datastorageRootId(root).created(DateUtils.nowUTC()).build();
    }

    public void delete(final StoragePermissionDeleteBatchRequest request) {
        getRoot(request.getId())
                .map(root -> request.getRequests().stream()
                        .map(r -> permissionEntityIdFrom(r, root)))
                .orElseGet(Stream::empty)
                .forEach(repository::delete);
    }

    private StoragePermissionEntityId permissionEntityIdFrom(final StoragePermissionDeleteRequest request,
                                                             final Long root) {
        return mapper.toEntityId(request).toBuilder().datastorageRootId(root).build();
    }

    public List<StoragePermission> load(final StoragePermissionLoadBatchRequest request) {
        return getRoot(request.getId())
                .map(root -> request.getRequests().stream()
                        .map(r -> repository.findByDatastorageRootIdAndDatastoragePathAndDatastorageType(root,
                                r.getPath(), r.getType())))
                .orElseGet(Stream::empty)
                .flatMap(List::stream)
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }

    private Optional<Long> getRoot(final Long storageId) {
        return Optional.ofNullable(storageDao.loadDataStorage(storageId))
                .map(AbstractDataStorage::getRootId);
    }

}
