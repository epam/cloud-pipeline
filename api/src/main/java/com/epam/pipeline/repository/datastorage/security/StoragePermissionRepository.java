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

    @Query(name = "StoragePermissionRepository.findPermissions", nativeQuery = true)
    List<StoragePermissionEntity> findPermissions(@Param("datastorage_root_id") Long root,
                                                  @Param("datastorage_path") String path,
                                                  @Param("datastorage_type") String type,
                                                  @Param("datastorage_parent_paths") List<String> parentPaths,
                                                  @Param("user_sid_name") String user,
                                                  @Param("group_sid_names") List<String> groups);

    @Query(name = "StoragePermissionRepository.findDirectChildPermissions", nativeQuery = true)
    List<StoragePermissionEntity> findDirectChildPermissions(@Param("datastorage_root_id") Long root,
                                                             @Param("datastorage_path") String path,
                                                             @Param("user_sid_name") String user,
                                                             @Param("group_sid_names") List<String> groups);

    List<StoragePermissionEntity> findByDatastorageRootIdAndDatastoragePathAndDatastorageType(
            Long datastorageRootId,
            String datastoragePath,
            StoragePermissionPathType datastorageType
    );

    @Modifying
    @Query(name = "StoragePermissionRepository.copyFilePermissions", nativeQuery = true)
    void copyFilePermissions(@Param("datastorage_root_id") Long root,
                             @Param("datastorage_old_path") String oldPath,
                             @Param("datastorage_new_path") String newPath);

    @Modifying
    @Query(name = "StoragePermissionRepository.copyFolderPermissions", nativeQuery = true)
    void copyFolderPermissions(@Param("datastorage_root_id") Long root,
                               @Param("datastorage_old_path") String oldPath,
                               @Param("datastorage_new_path") String newPath);

    @Modifying
    @Query(name = "StoragePermissionRepository.deleteFilePermissions", nativeQuery = true)
    void deleteFilePermissions(@Param("datastorage_root_id") Long root,
                               @Param("datastorage_path") String path);

    @Modifying
    @Query(name = "StoragePermissionRepository.deleteFolderPermissions", nativeQuery = true)
    void deleteFolderPermissions(@Param("datastorage_root_id") Long root,
                                 @Param("datastorage_path") String path);

    @Query(name = "StoragePermissionRepository.findAggregatedMask", nativeQuery = true)
    Optional<Integer> findAggregatedMask(@Param("datastorage_root_id") Long root,
                                         @Param("datastorage_path") String path,
                                         @Param("user_sid_name") String user,
                                         @Param("group_sid_names") List<String> groups);

    // returns storage_id, storage_type for with at least single storage path read permission
    // which can be used later on to filter storages in library tree listings.
    @Query(name = "StoragePermissionRepository.findReadAllowedStorages", nativeQuery = true)
    List<Storage> findReadAllowedStorages(@Param("user_sid_name") String user,
                                          @Param("group_sid_names") List<String> groups);

    // returns storage_path, storage_path_type for storage paths with at least single read permission under the given path
    // which can be used later on to filter items in storage path listings.
    @Query(name = "StoragePermissionRepository.findReadAllowedDirectChildItems", nativeQuery = true)
    List<StorageItem> findReadAllowedDirectChildItems(@Param("datastorage_root_id") Long root,
                                                      @Param("datastorage_path") String path,
                                                      @Param("user_sid_name") String user,
                                                      @Param("group_sid_names") List<String> groups);

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
