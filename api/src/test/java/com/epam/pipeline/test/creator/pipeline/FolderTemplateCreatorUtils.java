package com.epam.pipeline.test.creator.pipeline;

import com.epam.pipeline.controller.Result;
import com.epam.pipeline.controller.vo.PermissionVO;
import com.epam.pipeline.controller.vo.data.storage.DataStorageWithMetadataVO;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import com.epam.pipeline.entity.templates.FolderTemplate;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class FolderTemplateCreatorUtils {

    public static final TypeReference<Result<FolderTemplate>> FOLDER_TEMPLATE_TYPE =
            new TypeReference<Result<FolderTemplate>>() {};
    public static final TypeReference<Result<DataStorageWithMetadataVO>> DATASTORAGE_WITH_METADATA_VO_TYPE =
            new TypeReference<Result<DataStorageWithMetadataVO>>() { };

    private FolderTemplateCreatorUtils() {
    }

    public static FolderTemplate getFolderTemplate(final DataStorageWithMetadataVO dataStorage,
                                                   final FolderTemplate childFolderTemplate,
                                                   final Map<String, PipeConfValue> metadata,
                                                   final PermissionVO permissionVO,
                                                   final String name) {
        final FolderTemplate folderTemplate = new FolderTemplate();
        folderTemplate.setName(name);
        folderTemplate.setDatastorages(getListOfNullable(dataStorage));
        folderTemplate.setChildren(getListOfNullable(childFolderTemplate));
        folderTemplate.setMetadata(metadata != null ? metadata : new HashMap<>());
        folderTemplate.setPermissions(getListOfNullable(permissionVO));
        return folderTemplate;
    }

    public static DataStorageWithMetadataVO getS3BucketDataStorageWithMetadataNameAndPath(
            final Map<String, PipeConfValue> metadata,
            final String name,
            final String path) {
        final DataStorageWithMetadataVO storage = new DataStorageWithMetadataVO();
        storage.setName(name);
        storage.setType(DataStorageType.S3);
        storage.setPath(path);
        storage.setMetadata(metadata);
        return storage;
    }

    private static <T> List<T> getListOfNullable(T t) {
        return Optional.ofNullable(t)
                .map(Collections::singletonList)
                .orElse(Collections.emptyList());
    }
}
