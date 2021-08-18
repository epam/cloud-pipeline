package com.epam.pipeline.repository.datastorage.security;

import com.epam.pipeline.dto.datastorage.security.StoragePermissionPathType;
import com.epam.pipeline.entity.datastorage.security.StoragePermissionEntity;
import com.epam.pipeline.entity.datastorage.security.StoragePermissionEntityId;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

// TODO: 18.08.2021 Extract sql queries to resources
public interface StoragePermissionRepository
        extends CrudRepository<StoragePermissionEntity, StoragePermissionEntityId> {

    // TODO: 16.08.2021 Filter by target sids
    @Query(value = "" +
            "SELECT length(datastorage_path), *\n" +
            "FROM pipeline.datastorage_permission\n" +
            "WHERE datastorage_root_id = :datastorage_root_id\n" +
            "AND datastorage_type = 'FOLDER'\n" +
            "AND (datastorage_path = ''\n" +
            "\tOR datastorage_path != :datastorage_path\n" +
            "\tAND position(datastorage_path in :datastorage_path) = 1\n" +
            "\tAND substring(substring(:datastorage_path from (length(datastorage_path) + 1)), 1, 1) = '/')" +
            "AND length(datastorage_path) = (\n" +
            "\tSELECT max(length(datastorage_path)) FROM pipeline.datastorage_permission\n" +
            "\tWHERE datastorage_root_id = :datastorage_root_id\n" +
            "\tAND datastorage_type = 'FOLDER'\n" +
            "\tAND (datastorage_path = ''\n" +
            "\t\tOR datastorage_path != :datastorage_path\n" +
            "\t\tAND position(datastorage_path in :datastorage_path) = 1\n" +
            "\t\tAND substring(substring(:datastorage_path from (length(datastorage_path) + 1)), 1, 1) = '/')" +
            ")\n" +
            "UNION\n" +
            "SELECT length(datastorage_path), * FROM pipeline.datastorage_permission\n" +
            "WHERE datastorage_root_id = :datastorage_root_id\n" +
            "AND datastorage_type = (:datastorage_type)\\:\\:datastorage_item_type\n" +
            "AND datastorage_path = :datastorage_path\n" +
            "ORDER BY 1 DESC",
            nativeQuery = true)
    List<StoragePermissionEntity> findExactOrParentPermissions(
            @Param("datastorage_root_id") Long root,
            @Param("datastorage_path") String path,
            @Param("datastorage_type") String type);

    List<StoragePermissionEntity> findByDatastorageRootIdAndDatastoragePathAndDatastorageType(
            Long datastorageRootId,
            String datastoragePath,
            StoragePermissionPathType datastorageType
    );

    @Modifying
    @Query(value = "" +
            "INSERT\n" +
            "INTO pipeline.datastorage_permission (\n" +
            "\tdatastorage_root_id,\n" +
            "\tdatastorage_path,\n" +
            "\tdatastorage_type,\n" +
            "\tsid_name,\n" +
            "\tsid_type,\n" +
            "\tmask,\n" +
            "\tcreated\n" +
            ")\n" +
            "SELECT\n" +
            "\tp.datastorage_root_id,\n" +
            "\t:datastorage_new_path,\n" +
            "\tp.datastorage_type,\n" +
            "\tp.sid_name,\n" +
            "\tp.sid_type,\n" +
            "\tp.mask,\n" +
            "\tp.created\n" +
            "FROM pipeline.datastorage_permission p\n" +
            "WHERE p.datastorage_root_id = :datastorage_root_id\n" +
            "AND p.datastorage_type = 'FILE'\n" +
            "AND p.datastorage_path = :datastorage_old_path\n" +
            "ON CONFLICT (datastorage_root_id, datastorage_path, datastorage_type, sid_name, sid_type)\n" +
            "DO UPDATE SET\n" +
            "mask = excluded.mask,\n" +
            "created = excluded.created",
            nativeQuery = true)
    void copyFilePermissions(@Param("datastorage_root_id") Long root,
                             @Param("datastorage_old_path") String oldPath,
                             @Param("datastorage_new_path") String newPath);

    @Modifying
    @Query(value = "" +
            "INSERT\n" +
            "INTO pipeline.datastorage_permission (\n" +
            "\tdatastorage_root_id,\n" +
            "\tdatastorage_path,\n" +
            "\tdatastorage_type,\n" +
            "\tsid_name,\n" +
            "\tsid_type,\n" +
            "\tmask,\n" +
            "\tcreated\n" +
            ")\n" +
            "SELECT\n" +
            "\tp.datastorage_root_id,\n" +
            "\tregexp_replace(p.datastorage_path, :datastorage_old_path, :datastorage_new_path),\n" +
            "\tp.datastorage_type,\n" +
            "\tp.sid_name,\n" +
            "\tp.sid_type,\n" +
            "\tp.mask,\n" +
            "\tp.created\n" +
            "FROM pipeline.datastorage_permission p\n" +
            "WHERE p.datastorage_root_id = :datastorage_root_id\n" +
            "AND p.datastorage_type = 'FOLDER'\n" +
            "AND (p.datastorage_path = :datastorage_old_path\n" +
            "\tOR p.datastorage_path LIKE :datastorage_old_path || '/%')\n" +
            "ON CONFLICT (datastorage_root_id, datastorage_path, datastorage_type, sid_name, sid_type)\n" +
            "DO UPDATE SET\n" +
            "mask = excluded.mask,\n" +
            "created = excluded.created",
            nativeQuery = true)
    void copyFolderPermissions(@Param("datastorage_root_id") Long root,
                               @Param("datastorage_old_path") String oldPath,
                               @Param("datastorage_new_path") String newPath);

    @Modifying
    @Query(value = "" +
            "DELETE\n" +
            "FROM pipeline.datastorage_permission p\n" +
            "WHERE p.datastorage_root_id = :datastorage_root_id\n" +
            "AND p.datastorage_type = 'FILE'\n" +
            "AND p.datastorage_path = :datastorage_path",
            nativeQuery = true)
    void deleteFilePermissions(@Param("datastorage_root_id") Long root,
                               @Param("datastorage_path") String path);

    @Modifying
    @Query(value = "" +
            "DELETE\n" +
            "FROM pipeline.datastorage_permission p\n" +
            "WHERE p.datastorage_root_id = :datastorage_root_id\n" +
            "AND (p.datastorage_path = :datastorage_path\n" +
            "\tAND p.datastorage_type = 'FOLDER'\n" +
            "\tOR p.datastorage_path LIKE :datastorage_path || '/%')",
            nativeQuery = true)
    void deleteFolderPermissions(@Param("datastorage_root_id") Long root,
                                 @Param("datastorage_path") String path);

    @Query(value = "" +
            "SELECT\n" +
            "\tbit_or(COALESCE(user_permission.mask, group_permission.mask)) as mask\n" +
            "FROM\n" +
            "(\n" +
            "\tSELECT p.datastorage_path, bit_or(p.mask) as mask\n" +
            "\tFROM pipeline.datastorage_permission p\n" +
            "\tWHERE p.datastorage_root_id = :datastorage_root_id\n" +
            "\tAND (p.datastorage_path = :datastorage_path\n" +
            "\t\tAND p.datastorage_type = 'FOLDER'\n" +
            "\t\tOR p.datastorage_path LIKE :datastorage_path || '/%')\n" +
            "\tAND p.sid_type = 'USER'\n" +
            "\tAND p.sid_name = :user_sid_name\n" +
            "\tGROUP BY p.datastorage_type, p.datastorage_path\n" +
            ") user_permission\n" +
            "FULL JOIN\n" +
            "(\n" +
            "\tSELECT p.datastorage_path, bit_or(p.mask) as mask\n" +
            "\tFROM pipeline.datastorage_permission p\n" +
            "\tWHERE p.datastorage_root_id = :datastorage_root_id\n" +
            "\tAND (p.datastorage_path = :datastorage_path\n" +
            "\t\tAND p.datastorage_type = 'FOLDER'\n" +
            "\t\tOR p.datastorage_path LIKE :datastorage_path || '/%')\n" +
            "\tAND p.sid_type = 'GROUP'\n" +
            "\tAND p.sid_name in :group_sid_names\n" +
            "\tGROUP BY p.datastorage_type, p.datastorage_path\n" +
            ") group_permission\n" +
            "ON (user_permission.datastorage_path = group_permission.datastorage_path)",
            nativeQuery = true)
    Optional<Integer> findAggregatedMask(@Param("datastorage_root_id") Long root,
                                         @Param("datastorage_path") String path,
                                         @Param("user_sid_name") String user,
                                         @Param("group_sid_names") List<String> groups);

    @Query(value = "" +
            "SELECT bit_or(p.mask) as mask\n" +
            "FROM pipeline.datastorage_permission p\n" +
            "WHERE p.datastorage_root_id = :datastorage_root_id\n" +
            "AND (p.datastorage_path = :datastorage_path\n" +
            "\tAND p.datastorage_type = 'FOLDER'\n" +
            "\tOR p.datastorage_path LIKE :datastorage_path || '/%')\n" +
            "AND p.sid_type = 'USER'\n" +
            "AND p.sid_name = :user_sid_name",
            nativeQuery = true)
    Optional<Integer> findAggregatedMask(@Param("datastorage_root_id") Long root,
                                         @Param("datastorage_path") String path,
                                         @Param("user_sid_name") String user);
}
