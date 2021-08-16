package com.epam.pipeline.mapper.datastorage.security;

import com.epam.pipeline.dto.datastorage.security.StoragePermissionDeleteRequest;
import com.epam.pipeline.dto.datastorage.security.StoragePermissionInsertRequest;
import com.epam.pipeline.dto.datastorage.security.StoragePermission;
import com.epam.pipeline.entity.datastorage.security.StoragePermissionEntity;
import com.epam.pipeline.dto.datastorage.security.StoragePermissionSid;
import com.epam.pipeline.entity.datastorage.security.StoragePermissionEntityId;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface StoragePermissionMapper {

    default StoragePermissionEntity toEntity(final StoragePermission dto) {
        return new StoragePermissionEntity(null, dto.getPath(),
                dto.getType(),
                dto.getSid().getName(),
                dto.getSid().getType(), dto.getMask(), dto.getCreatedDate());
    }

    default StoragePermissionEntity toEntity(final StoragePermissionInsertRequest request) {
        return new StoragePermissionEntity(null, request.getPath(), request.getType(),
                request.getSid().getName(), request.getSid().getType(), request.getMask(), null);
    }

    default StoragePermissionEntityId toEntityId(final StoragePermissionDeleteRequest request) {
        return new StoragePermissionEntityId(null, request.getPath(), request.getType(),
                request.getSid().getName(), request.getSid().getType());
    }

    default StoragePermission toDto(final StoragePermissionEntity entity) {
        return new StoragePermission(entity.getDatastoragePath(),
                entity.getDatastorageType(),
                new StoragePermissionSid(entity.getSidName(), entity.getSidType()),
                entity.getMask(), entity.getCreated());
    }
}
