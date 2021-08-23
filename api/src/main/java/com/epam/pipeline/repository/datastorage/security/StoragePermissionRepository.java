package com.epam.pipeline.repository.datastorage.security;

import com.epam.pipeline.dto.datastorage.security.StoragePermissionPathType;
import com.epam.pipeline.dto.datastorage.security.StorageKind;
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

// TODO: 18.08.2021 Extract sql queries to resources
public interface StoragePermissionRepository
        extends CrudRepository<StoragePermissionEntity, StoragePermissionEntityId> {

    // TODO: 16.08.2021 Filter by target sids
    @Query(name = "StoragePermissionRepository.findExactOrParentPermissions", nativeQuery = true)
    List<StoragePermissionEntity> findExactOrParentPermissions(
            @Param("datastorage_root_id") Long root,
            @Param("datastorage_path") String path,
            @Param("datastorage_type") String type);

    @Query(name = "StoragePermissionRepository.findDirectChildPermissions", nativeQuery = true)
    List<StorageItemWithMask> findDirectChildPermissions(@Param("datastorage_root_id") Long root,
                                                         @Param("datastorage_path") String path,
                                                         @Param("user_sid_name") String user,
                                                         @Param("group_sid_names") List<String> groups);

    @Query(name = "StoragePermissionRepository.findRootDirectChildPermissions", nativeQuery = true)
    List<StorageItemWithMask> findRootDirectChildPermissions(@Param("datastorage_root_id") Long root,
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

    @Query(name = "StoragePermissionRepository.findAggregatedMaskForUserWithoutGroups", nativeQuery = true)
    Optional<Integer> findAggregatedMaskForUserWithoutGroups(@Param("datastorage_root_id") Long root,
                                                             @Param("datastorage_path") String path,
                                                             @Param("user_sid_name") String user);

    // returns storage_id, storage_type for with at least single storage path read permission
    // which can be used later on to filter storages in library tree listings.
    @Query(name = "StoragePermissionRepository.findReadAllowedStorages", nativeQuery = true)
    List<Storage> findReadAllowedStorages(
            @Param("user_sid_name") String user,
            @Param("group_sid_names") List<String> groups);

    // returns storage_path, storage_path_type for storage paths with at least single read permission under the given path
    // which can be used later on to filter items in storage path listings.
    @Query(name = "StoragePermissionRepository.findReadAllowedDirectChildItems", nativeQuery = true)
    List<StorageItem> findReadAllowedDirectChildItems(
            @Param("datastorage_root_id") Long root,
            @Param("datastorage_path") String path,
            @Param("user_sid_name") String user,
            @Param("group_sid_names") List<String> groups);

    @Query(name = "StoragePermissionRepository.findReadAllowedRootDirectChildItems", nativeQuery = true)
    List<StorageItem> findReadAllowedRootDirectChildItems(
            @Param("datastorage_root_id") Long root,
            @Param("user_sid_name") String user,
            @Param("group_sid_names") List<String> groups
    );

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

    @TypeDef(name = "StoragePermissionPathTypeUserType", typeClass = StoragePermissionPathTypeUserType.class)
    interface StorageItemWithMask {

        String getDatastoragePath();

        @Type(type = "StoragePermissionPathTypeUserType")
        StoragePermissionPathType getDatastorageType();

        int getMask();
    }

    @Value
    class StorageItemWithMaskImpl implements StorageItemWithMask {
        String datastoragePath;
        StoragePermissionPathType datastorageType;
        int mask;
    }

}
