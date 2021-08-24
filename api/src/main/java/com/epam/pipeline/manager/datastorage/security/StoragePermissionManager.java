package com.epam.pipeline.manager.datastorage.security;

import com.epam.pipeline.dto.datastorage.security.StoragePermission;
import com.epam.pipeline.dto.datastorage.security.StoragePermissionPathType;
import com.epam.pipeline.mapper.datastorage.security.StoragePermissionMapper;
import com.epam.pipeline.repository.datastorage.security.StoragePermissionRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StoragePermissionManager {

    private final StoragePermissionRepository repository;
    private final StoragePermissionMapper mapper;

    public List<StoragePermission> load(final Long root,
                                        final String path,
                                        final StoragePermissionPathType type,
                                        final String user,
                                        final List<String> groups) {
        return repository.findPermissions(root, path, type.name().toUpperCase(), blankStringListIfEmpty(getParentPaths(path)),
                        user, blankStringListIfEmpty(groups))
                .stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }

    private List<String> getParentPaths(final String path) {
        final String[] items = path.split("/");
        final List<String> parents = new ArrayList<>();
        final StringJoiner joiner = new StringJoiner("/");
        for (int i = 0; i < items.length - 1; i++) {
            joiner.add(items[0]);
            parents.add(joiner.toString());
        }
        return parents;
    }

    public void delete(final Long root, final String path, final StoragePermissionPathType type) {
        if (type == StoragePermissionPathType.FILE) {
            repository.deleteFilePermissions(root, path);
        } else {
            repository.deleteFolderPermissions(root, path);
        }
    }

    public void copy(final Long root,
                     final String oldPath,
                     final String newPath,
                     final StoragePermissionPathType type) {
        if (type == StoragePermissionPathType.FILE) {
            repository.copyFilePermissions(root, oldPath, newPath);
        } else {
            repository.copyFolderPermissions(root, oldPath, newPath);
        }
    }

    public Optional<Integer> loadAggregatedMask(final Long root,
                                                final String path,
                                                final String user,
                                                final List<String> groups) {
        return repository.findAggregatedMask(root, path, user, blankStringListIfEmpty(groups));
    }

    public Set<StoragePermissionRepository.Storage> loadReadAllowedStorages(final String user,
                                                                            final List<String> groups) {
        return repository.findReadAllowedStorages(user, blankStringListIfEmpty(groups))
                .stream()
                .map(allowedStorage -> new StoragePermissionRepository.StorageImpl(
                        allowedStorage.getStorageId(),
                        allowedStorage.getStorageType()))
                .collect(Collectors.toSet());
    }

    public Set<StoragePermissionRepository.StorageItem> loadReadAllowedDirectChildItems(final Long root,
                                                                                        final String path,
                                                                                        final String user,
                                                                                        final List<String> groups) {
        return repository.findReadAllowedDirectChildItems(root, path, user, blankStringListIfEmpty(groups))
                .stream()
                .map(allowedItem -> new StoragePermissionRepository.StorageItemImpl(
                        allowedItem.getStoragePath(), allowedItem.getStoragePathType()))
                .collect(Collectors.toSet());
    }

    public List<StoragePermission> loadDirectChildPermissions(final Long root,
                                                              final String path,
                                                              final String user,
                                                              final List<String> groups) {
        return repository.findDirectChildPermissions(root, path, user, blankStringListIfEmpty(groups)).stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }

    private List<String> blankStringListIfEmpty(final List<String> list) {
        return CollectionUtils.isEmpty(list) ? Collections.singletonList(StringUtils.EMPTY) : list;
    }

}
