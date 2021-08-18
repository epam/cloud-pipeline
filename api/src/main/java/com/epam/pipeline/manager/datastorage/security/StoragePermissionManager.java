package com.epam.pipeline.manager.datastorage.security;

import com.epam.pipeline.dto.datastorage.security.StoragePermission;
import com.epam.pipeline.dto.datastorage.security.StoragePermissionPathType;
import com.epam.pipeline.mapper.datastorage.security.StoragePermissionMapper;
import com.epam.pipeline.repository.datastorage.security.StoragePermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StoragePermissionManager {

    private final StoragePermissionRepository repository;
    private final StoragePermissionMapper mapper;

    public List<StoragePermission> load(final Long root,
                                        final String path,
                                        final StoragePermissionPathType type) {
        return repository.findExactOrParentPermissions(root, path, type.name().toUpperCase()).stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());
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

    public Optional<Integer> loadAggregatedMask(final Long rootId,
                                                final String path,
                                                final String user,
                                                final List<String> groups) {
        return groups.isEmpty()
                ? repository.findAggregatedMask(rootId, path, user)
                : repository.findAggregatedMask(rootId, path, user, groups);
    }
}
