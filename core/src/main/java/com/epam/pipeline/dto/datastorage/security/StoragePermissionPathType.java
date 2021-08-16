package com.epam.pipeline.dto.datastorage.security;

import com.epam.pipeline.entity.datastorage.DataStorageItemType;

public enum StoragePermissionPathType {

    FILE,
    FOLDER;

    public static StoragePermissionPathType from(final DataStorageItemType type) {
        return type == DataStorageItemType.Folder ? FOLDER : FILE;
    }

}
