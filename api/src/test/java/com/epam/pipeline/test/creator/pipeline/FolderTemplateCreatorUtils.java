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

    public static FolderTemplate getFolderTemplate(DataStorageWithMetadataVO dataStorage,
                                                   FolderTemplate childFolderTemplate,
                                                   Map<String, PipeConfValue> metadata,
                                                   PermissionVO permissionVO,
                                                   String name) {
        return FolderTemplate.builder()
                .name(name)
                .datastorages(getListOfNullable(dataStorage))
                .children(getListOfNullable(childFolderTemplate))
                .metadata(metadata != null ? metadata : new HashMap<>())
                .permissions(getListOfNullable(permissionVO))
                .build();
    }

    public static DataStorageWithMetadataVO getS3BucketDataStorageWithMetadataNameAndPath(
            Map<String, PipeConfValue> metadata, String name, String path) {
        DataStorageWithMetadataVO storage = new DataStorageWithMetadataVO();
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
