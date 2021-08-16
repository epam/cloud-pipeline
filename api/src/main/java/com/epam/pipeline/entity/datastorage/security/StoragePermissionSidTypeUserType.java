package com.epam.pipeline.entity.datastorage.security;

import com.epam.pipeline.dto.datastorage.security.StoragePermissionSidType;
import com.epam.pipeline.entity.utils.EnumUserType;

public class StoragePermissionSidTypeUserType extends EnumUserType<StoragePermissionSidType> {

    public StoragePermissionSidTypeUserType() {
        super(StoragePermissionSidType.class);
    }

}
