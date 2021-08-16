package com.epam.pipeline.manager.datastorage.security;

import com.epam.pipeline.dto.datastorage.security.StoragePermission;
import com.epam.pipeline.dto.datastorage.security.StoragePermissionPathType;
import com.epam.pipeline.mapper.datastorage.security.StoragePermissionMapper;
import com.epam.pipeline.repository.datastorage.security.StoragePermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StoragePermissionManager {

    private final StoragePermissionRepository repository;
    private final StoragePermissionMapper mapper;

    public List<StoragePermission> load(final Long rootId,
                                        final String path,
                                        final StoragePermissionPathType type) {
        return repository.findPermissions(rootId, path, type.name().toUpperCase()).stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }
}
