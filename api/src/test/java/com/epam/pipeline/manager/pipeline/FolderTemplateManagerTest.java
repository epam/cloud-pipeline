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

import com.epam.pipeline.controller.vo.DataStorageVO;
import com.epam.pipeline.controller.vo.PermissionVO;
import com.epam.pipeline.controller.vo.data.storage.DataStorageWithMetadataVO;
import com.epam.pipeline.entity.SecuredEntityWithAction;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.templates.FolderTemplate;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import com.epam.pipeline.manager.metadata.MetadataManager;
import com.epam.pipeline.manager.security.GrantPermissionManager;
import com.epam.pipeline.security.acl.AclPermission;
import com.epam.pipeline.test.acl.AbstractAclTest;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyMapOf;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;


public class FolderTemplateManagerTest extends AbstractAclTest {
    private static final String TEST_PATH = "path";
    private static final String TEST_ROLE = "TEST_ROLE";

    private static final String DATA_KEY_1 = "tag";
    private static final String DATA_TYPE_1 = "string";
    private static final String DATA_VALUE_1 = "OWNER";

    private static final String TEMPLATE_FOLDER_NAME = "test-folder";
    private static final String CHILD_TEMPLATE_FOLDER_NAME_1 = "test-1";
    private static final String DATASTORAGE_NAME_1 = "ds-1";

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

    private Map<String, PipeConfValue> metadata;
    private PermissionVO permissionVO;
    private FolderTemplate folderTemplate;

    @Test
    public void createFolderFromTemplateTest() throws IOException {
        initializeParameters();
        preConditions();

        Folder folder = new Folder();
        folder.setName(TEST_NAME_2);
        folder.setId(FOLDER_ID);
        folderTemplateManager.createFolderFromTemplate(folder, folderTemplate);

        Assert.assertEquals(TEMPLATE_FOLDER_NAME, folder.getName());

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

        postValidation();
    }

    private void initializeParameters() {
        metadata = getMetadata();

        DataStorageWithMetadataVO dataStorageVO = new DataStorageWithMetadataVO();
        dataStorageVO.setName(DATASTORAGE_NAME_1);
        dataStorageVO.setType(DataStorageType.S3);
        dataStorageVO.setPath(TEST_PATH);
        dataStorageVO.setMetadata(metadata);

        permissionVO = new PermissionVO();
        permissionVO.setMask(AclPermission.READ.getMask());
        permissionVO.setUserName(TEST_ROLE);
        permissionVO.setPrincipal(false);

        FolderTemplate childFolderTemplate1 = FolderTemplate.builder()
                .name(CHILD_TEMPLATE_FOLDER_NAME_1)
                .datastorages(Stream.of(new DataStorageWithMetadataVO()).collect(Collectors.toList()))
                .build();

        folderTemplate = FolderTemplate.builder()
                .name(TEMPLATE_FOLDER_NAME)
                .datastorages(Stream.of(dataStorageVO).collect(Collectors.toList()))
                .children(Stream.of(childFolderTemplate1).collect(Collectors.toList()))
                .metadata(metadata)
                .permissions(Stream.of(permissionVO).collect(Collectors.toList()))
                .build();
    }

    private void preConditions() {
        doAnswer(invocation -> {
            Folder parameterFolder = invocation.getArgumentAt(0, Folder.class);
            return (FOLDER_ID.equals(parameterFolder.getParentId())) ? getFolderChild() : getFolder();
        }).when(mockFolderCrudManager).create(any(Folder.class));

        doReturn(getMockSecurityEntity()).when(mockDataStorageManager)
                .create(any(DataStorageVO.class), anyBoolean(), anyBoolean(), anyBoolean());
        doNothing().when(mockMetadataManager).updateEntityMetadata(anyMapOf(String.class, PipeConfValue.class),
                anyLong(), any(AclClass.class));
        doNothing().when(spyGrantPermissionManager).setPermissionsToEntity(anyListOf(PermissionVO.class),
                anyLong(), any(AclClass.class));
    }

    private void postValidation() {
        int twoInvocations = 2;
        int fourInvocations = 4;

        verify(mockFolderCrudManager, times(twoInvocations)).create(any(Folder.class));
        verify(mockDataStorageManager, times(twoInvocations)).create(any(DataStorageVO.class), anyBoolean(),
                anyBoolean(), anyBoolean());
        verify(mockMetadataManager, times(fourInvocations))
                .updateEntityMetadata(anyMapOf(String.class, PipeConfValue.class), anyLong(), any(AclClass.class));
        verify(spyGrantPermissionManager, times(twoInvocations)).setPermissionsToEntity(anyListOf(PermissionVO.class),
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
        folder.setName(CHILD_TEMPLATE_FOLDER_NAME_1);
        folder.setParentId(FOLDER_ID);
        return folder;
    }

    private Map<String, PipeConfValue> getMetadata() {
        Map<String, PipeConfValue> metadata = new HashMap<>();
        metadata.put(DATA_KEY_1, new PipeConfValue(DATA_TYPE_1, DATA_VALUE_1));
        return metadata;
    }
}
