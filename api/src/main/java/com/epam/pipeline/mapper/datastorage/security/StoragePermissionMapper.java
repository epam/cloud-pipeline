package com.epam.pipeline.mapper.datastorage.security;

import com.epam.pipeline.dto.datastorage.security.StoragePermission;
import com.epam.pipeline.entity.datastorage.security.StoragePermissionEntity;
import com.epam.pipeline.dto.datastorage.security.StoragePermissionSid;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface StoragePermissionMapper {

    default StoragePermissionEntity toEntity(final StoragePermission dto) {
        return new StoragePermissionEntity(null, dto.getPath(),
                dto.getType(),
                dto.getSid().getName(),
                dto.getSid().getType(), dto.getMask(), dto.getCreatedDate());
    }

    default StoragePermission toDto(final StoragePermissionEntity entity) {
        return new StoragePermission(entity.getDatastoragePath(),
                entity.getDatastorageType(),
                new StoragePermissionSid(entity.getSidName(), entity.getSidType()),
                entity.getMask(), entity.getCreated());
    }
}
