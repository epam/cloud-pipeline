package com.epam.pipeline.test.creator.folderTemplate;

import com.epam.pipeline.controller.vo.PermissionVO;
import com.epam.pipeline.controller.vo.data.storage.DataStorageWithMetadataVO;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import com.epam.pipeline.entity.templates.FolderTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_NAME;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_NAME_2;

public final class FolderTemplateCreatorUtils {

    private FolderTemplateCreatorUtils() {
    }

    public static FolderTemplate buildFolderTemplate(DataStorageWithMetadataVO dataStorage,
                                                     FolderTemplate childFolderTemplate,
                                                     Map<String, PipeConfValue> metadata,
                                                     PermissionVO permissionVO) {
        FolderTemplate template = build(dataStorage, childFolderTemplate, metadata, permissionVO);
        template.setName(TEST_NAME);
        return template;
    }

    public static FolderTemplate buildFolderTemplateChild(DataStorageWithMetadataVO dataStorage,
                                                          Map<String, PipeConfValue> metadata,
                                                          PermissionVO permissionVO) {
        FolderTemplate template = build(dataStorage, null, metadata, permissionVO);
        template.setName(TEST_NAME_2);
        return template;
    }

    private static FolderTemplate build(DataStorageWithMetadataVO dataStorage,
                                        FolderTemplate childFolderTemplate,
                                        Map<String, PipeConfValue> metadata,
                                        PermissionVO permissionVO) {
        return FolderTemplate.builder()
                .datastorages(validateAndGetListOf(dataStorage))
                .children(validateAndGetListOf(childFolderTemplate))
                .metadata(metadata != null ? metadata : new HashMap<>())
                .permissions(validateAndGetListOf(permissionVO))
                .build();
    }

    private static <T> List<T> validateAndGetListOf(T t) {
        return t != null ? Stream.of(t).collect(Collectors.toList()) : new ArrayList<>();
    }
}
