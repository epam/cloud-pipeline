package com.epam.pipeline.entity.datastorage.security;

import com.epam.pipeline.dto.datastorage.security.StoragePermissionPathType;
import com.epam.pipeline.entity.utils.EnumUserType;

public class StoragePermissionPathTypeUserType extends EnumUserType<StoragePermissionPathType> {

    public StoragePermissionPathTypeUserType() {
        super(StoragePermissionPathType.class);
    }

}
