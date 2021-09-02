package com.epam.pipeline.repository.datastorage.security;

import com.epam.pipeline.dto.datastorage.security.StorageKind;
import com.epam.pipeline.dto.datastorage.security.StoragePermissionPathType;
import com.epam.pipeline.entity.datastorage.security.StoragePermissionEntity;
import com.epam.pipeline.entity.datastorage.security.StoragePermissionEntityId;
import com.epam.pipeline.entity.datastorage.security.StoragePermissionPathTypeUserType;
import lombok.Value;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import java.util.List;
import java.util.Optional;

public interface StoragePermissionRepository
        extends CrudRepository<StoragePermissionEntity, StoragePermissionEntityId> {

    String STORAGE_ID = "datastorage_id";
    String STORAGE_ROOT_ID = "datastorage_root_id";
    String STORAGE_PATH = "datastorage_path";
    String STORAGE_OLD_PATH = "datastorage_old_path";
    String STORAGE_NEW_PATH = "datastorage_new_path";
    String STORAGE_PARENT_PATHS = "datastorage_parent_paths";
    String STORAGE_PATH_TYPE = "datastorage_type";
    String USER_SID_NAME = "user_sid_name";
    String GROUP_SID_NAMES = "group_sid_names";

    default List<StoragePermissionEntity> findByRootAndPathAndType(Long root,
                                                                   String path,
                                                                   StoragePermissionPathType type) {
        return findByDatastorageRootIdAndDatastoragePathAndDatastorageType(root, path, type);
    }

    List<StoragePermissionEntity> findByDatastorageRootIdAndDatastoragePathAndDatastorageType(
            Long datastorageRootId,
            String datastoragePath,
            StoragePermissionPathType datastorageType
    );

    @Query(name = "StoragePermissionRepository.findPermissions", nativeQuery = true)
    List<StoragePermissionEntity> findPermissions(@Param(STORAGE_ROOT_ID) Long root,
                                                  @Param(STORAGE_PATH) String path,
                                                  @Param(STORAGE_PATH_TYPE) String type,
                                                  @Param(STORAGE_PARENT_PATHS) List<String> parentPaths,
                                                  @Param(USER_SID_NAME) String user,
                                                  @Param(GROUP_SID_NAMES) List<String> groups);

    @Query(name = "StoragePermissionRepository.findImmediateChildPermissions", nativeQuery = true)
    List<StoragePermissionEntity> findImmediateChildPermissions(@Param(STORAGE_ROOT_ID) Long root,
                                                                @Param(STORAGE_PATH) String path,
                                                                @Param(USER_SID_NAME) String user,
                                                                @Param(GROUP_SID_NAMES) List<String> groups);

    /**
     * @return Immediate child items with at least a single recursive read permission.
     */
    @Query(name = "StoragePermissionRepository.findReadAllowedImmediateChildItems", nativeQuery = true)
    List<StorageItem> findReadAllowedImmediateChildItems(@Param(STORAGE_ROOT_ID) Long root,
                                                         @Param(STORAGE_PATH) String path,
                                                         @Param(USER_SID_NAME) String user,
                                                         @Param(GROUP_SID_NAMES) List<String> groups);

    /**
     * @return Storages with at least a single path read permission.
     */
    @Query(name = "StoragePermissionRepository.findReadAllowedStorages", nativeQuery = true)
    List<Storage> findReadAllowedStorages(@Param(USER_SID_NAME) String user,
                                          @Param(GROUP_SID_NAMES) List<String> groups);

    @Query(name = "StoragePermissionRepository.findReadAllowedStorage", nativeQuery = true)
    List<Storage> findReadAllowedStorage(@Param(STORAGE_ROOT_ID) Long root,
                                             @Param(STORAGE_ID) Long storage,
                                             @Param(USER_SID_NAME) String user,
                                             @Param(GROUP_SID_NAMES) List<String> groups);

    @Query(name = "StoragePermissionRepository.findRecursiveMask", nativeQuery = true)
    Optional<Integer> findRecursiveMask(@Param(STORAGE_ROOT_ID) Long root,
                                        @Param(STORAGE_PATH) String path,
                                        @Param(USER_SID_NAME) String user,
                                        @Param(GROUP_SID_NAMES) List<String> groups);

    @Modifying
    @Query(name = "StoragePermissionRepository.copyFilePermissions", nativeQuery = true)
    void copyFilePermissions(@Param(STORAGE_ROOT_ID) Long root,
                             @Param(STORAGE_OLD_PATH) String oldPath,
                             @Param(STORAGE_NEW_PATH) String newPath);

    @Modifying
    @Query(name = "StoragePermissionRepository.copyFolderPermissions", nativeQuery = true)
    void copyFolderPermissions(@Param(STORAGE_ROOT_ID) Long root,
                               @Param(STORAGE_OLD_PATH) String oldPath,
                               @Param(STORAGE_NEW_PATH) String newPath);

    @Modifying
    @Query(name = "StoragePermissionRepository.deleteFilePermissions", nativeQuery = true)
    void deleteFilePermissions(@Param(STORAGE_ROOT_ID) Long root,
                               @Param(STORAGE_PATH) String path);

    @Modifying
    @Query(name = "StoragePermissionRepository.deleteFolderPermissions", nativeQuery = true)
    void deleteFolderPermissions(@Param(STORAGE_ROOT_ID) Long root,
                                 @Param(STORAGE_PATH) String path);

    interface Storage {

        Long getStorageId();

        @Enumerated(EnumType.STRING)
        StorageKind getStorageType();
    }

    @Value
    class StorageImpl implements Storage {
        Long storageId;
        StorageKind storageType;
    }

    @TypeDef(name = "StoragePermissionPathTypeUserType", typeClass = StoragePermissionPathTypeUserType.class)
    interface StorageItem {

        String getStoragePath();

        @Type(type = "StoragePermissionPathTypeUserType")
        StoragePermissionPathType getStoragePathType();
    }

    @Value
    class StorageItemImpl implements StorageItem {
        String storagePath;
        StoragePermissionPathType storagePathType;
    }

}
