package com.epam.pipeline.repository.datastorage.security;

import com.epam.pipeline.entity.datastorage.security.StoragePermissionEntity;
import com.epam.pipeline.entity.datastorage.security.StoragePermissionEntityId;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StoragePermissionRepository
        extends CrudRepository<StoragePermissionEntity, StoragePermissionEntityId> {

    // TODO: 16.08.2021 Filter by target sids
    @Query(value = "" +
            "SELECT length(datastorage_path), * FROM pipeline.datastorage_permission\n" +
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
    List<StoragePermissionEntity> findPermissions(
            @Param("datastorage_root_id") final Long rootId,
            @Param("datastorage_path") final String path,
            @Param("datastorage_type") final String type);
}
