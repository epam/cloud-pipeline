/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.manager.pipeline;

import com.epam.pipeline.controller.vo.PermissionVO;
import com.epam.pipeline.controller.vo.data.storage.DataStorageWithMetadataVO;
import com.epam.pipeline.entity.SecuredEntityWithAction;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.templates.FolderTemplate;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import com.epam.pipeline.manager.metadata.MetadataManager;
import com.epam.pipeline.manager.security.GrantPermissionManager;
import com.epam.pipeline.test.acl.AbstractAclTest;
import com.epam.pipeline.test.creator.CommonCreatorConstants;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

import static com.epam.pipeline.test.creator.datastorage.DatastorageCreatorUtils.getS3BucketDataStorageWithMetadataNameAndPath;
import static com.epam.pipeline.test.creator.folderTemplate.FolderTemplateCreatorUtils.buildFolderTemplate;
import static com.epam.pipeline.test.creator.folderTemplate.FolderTemplateCreatorUtils.buildFolderTemplateChild;
import static com.epam.pipeline.test.creator.security.PermissionCreatorUtils.getPermissionVO;
import static com.epam.pipeline.util.CustomMatchers.matches;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyMapOf;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ComponentScan(basePackageClasses = FolderTemplateManager.class)
public class FolderTemplateManagerTest extends AbstractAclTest {
    private static final String TEST_NAME = CommonCreatorConstants.TEST_NAME;

    private static final String DATA_KEY_1 = "tag";
    private static final String DATA_TYPE_1 = "string";
    private static final String DATA_VALUE_1 = "OWNER";
    private static final String DATA_KEY_2 = "tag-2";
    private static final String DATA_TYPE_2 = "string-2";
    private static final String DATA_VALUE_2 = "OWNER-2";

    private static final String CHILD_TEMPLATE_FOLDER_NAME = "test-1";
    private static final String DATASTORAGE_NAME = "ds-1";
    private static final String DATASTORAGE_NAME_2 = "ds-2";

    private static final Long FOLDER_ID = 1L;
    private static final Long FOLDER_CHILD_ID = 2L;

    @Autowired
    private FolderTemplateManager folderTemplateManager;
    @Autowired
    private MetadataManager mockMetadataManager;
    @Autowired
    private DataStorageManager mockDataStorageManager;
    @Autowired
    private GrantPermissionManager spyGrantPermissionManager;
    @Autowired
    private FolderCrudManager mockFolderCrudManager;

    @Test
    public void createFolderFromTemplateTest() {
        Map<String, PipeConfValue> metadata = getMetadata();
        Map<String, PipeConfValue> childMetadata = getChildMetadata();

        DataStorageWithMetadataVO dataStorageVO = getS3BucketDataStorageWithMetadataNameAndPath(metadata,
                DATASTORAGE_NAME, TEST_PATH);
        DataStorageWithMetadataVO dataStorageForChild = getS3BucketDataStorageWithMetadataNameAndPath(childMetadata,
                DATASTORAGE_NAME_2, TEST_PATH_2);
        PermissionVO permissionVO = getPermissionVO();
        FolderTemplate childFolderTemplate = buildFolderTemplateChild(dataStorageForChild,
                null, null);
        FolderTemplate folderTemplate = buildFolderTemplate(dataStorageVO, childFolderTemplate,
                metadata, permissionVO);

        doReturn(getFolder()).when(mockFolderCrudManager)
                .create(argThat(matches(Predicates.forFolderTemplate(folderTemplate.getName()))));
        doReturn(getFolderChild()).when(mockFolderCrudManager)
                .create(argThat(matches(Predicates.forChildFolderTemplate(childFolderTemplate.getName()))));
        doReturn(getMockSecurityEntity()).when(mockDataStorageManager)
                .create(argThat(matches(Predicates.forDataStorage())), eq(true), eq(true), eq(false));
        doReturn(getMockSecurityEntity()).when(mockDataStorageManager)
                .create(argThat(matches(Predicates.forChildDataStorage())), eq(true), eq(true), eq(false));

        doNothing().when(spyGrantPermissionManager).setPermissionsToEntity(anyListOf(PermissionVO.class),
                anyLong(), any(AclClass.class));


        Folder folder = new Folder();
        folder.setName(TEST_NAME_3);
        folder.setId(FOLDER_ID);
        folderTemplateManager.createFolderFromTemplate(folder, folderTemplate);

        Assert.assertEquals(TEST_NAME, folder.getName());
        Assert.assertTrue(folderTemplate.getDatastorages()
                .stream()
                .allMatch(storage -> FOLDER_ID.equals(storage.getParentFolderId())));
        Assert.assertTrue(folderTemplate.getChildren()
                .stream()
                .flatMap(childTemplate -> childTemplate.getDatastorages().stream())
                .allMatch(storage -> FOLDER_CHILD_ID.equals(storage.getParentFolderId())));
        Assert.assertEquals(metadata, folderTemplate.getMetadata());
        Assert.assertEquals(1, folderTemplate.getPermissions().size());
        Assert.assertEquals(permissionVO, folderTemplate.getPermissions().get(0));


        verify(mockFolderCrudManager, times(2)).create(any(Folder.class));
        verify(mockDataStorageManager, times(1)).create(eq(dataStorageVO), eq(true),
                eq(true), eq(false));
        verify(mockDataStorageManager, times(1)).create(eq(dataStorageForChild), eq(true),
                eq(true), eq(false));
        verify(mockMetadataManager, times(4))
                .updateEntityMetadata(anyMapOf(String.class, PipeConfValue.class), anyLong(), any(AclClass.class));
        verify(spyGrantPermissionManager, times(2)).setPermissionsToEntity(anyListOf(PermissionVO.class),
                anyLong(), any(AclClass.class));
    }

    private SecuredEntityWithAction<AbstractDataStorage> getMockSecurityEntity() {
        SecuredEntityWithAction<AbstractDataStorage> action = new SecuredEntityWithAction<>();
        action.setEntity(new S3bucketDataStorage());
        return action;
    }

    private Folder getFolder() {
        Folder folder = new Folder();
        folder.setId(FOLDER_ID);
        folder.setStorages(Collections.singletonList(new S3bucketDataStorage()));
        return folder;
    }

    private Folder getFolderChild() {
        Folder folder = new Folder();
        folder.setId(FOLDER_CHILD_ID);
        folder.setName(CHILD_TEMPLATE_FOLDER_NAME);
        folder.setParentId(FOLDER_ID);
        return folder;
    }

    private Map<String, PipeConfValue> getMetadata() {
        Map<String, PipeConfValue> metadata = new HashMap<>();
        metadata.put(DATA_KEY_1, new PipeConfValue(DATA_TYPE_1, DATA_VALUE_1));
        return metadata;
    }

    private Map<String, PipeConfValue> getChildMetadata() {
        Map<String, PipeConfValue> metadata = new HashMap<>();
        metadata.put(DATA_KEY_2, new PipeConfValue(DATA_TYPE_2, DATA_VALUE_2));
        return metadata;
    }

    private static class Predicates {
        static Predicate<Folder> forFolderTemplate(String name) {
            return f -> !FOLDER_ID.equals(f.getParentId())
                    && f.getName().equals(name);
        }

        static Predicate<Folder> forChildFolderTemplate(String name) {
            return f -> FOLDER_ID.equals(f.getParentId())
                    && f.getName().equals(name);
        }

        static Predicate<DataStorageWithMetadataVO> forDataStorage() {
            return dataStorage -> DATASTORAGE_NAME.equals(dataStorage.getName())
                    && TEST_PATH.equals(dataStorage.getPath());
        }

        static Predicate<DataStorageWithMetadataVO> forChildDataStorage() {
            return dataStorage -> DATASTORAGE_NAME_2.equals(dataStorage.getName())
                    && TEST_PATH_2.equals(dataStorage.getPath());
        }
    }
}
