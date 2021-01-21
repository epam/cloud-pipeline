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

import com.epam.pipeline.controller.vo.EntityVO;
import com.epam.pipeline.controller.vo.PermissionVO;
import com.epam.pipeline.controller.vo.data.storage.DataStorageWithMetadataVO;
import com.epam.pipeline.entity.SecuredEntityWithAction;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage;
import com.epam.pipeline.entity.metadata.MetadataEntry;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.security.acl.AclPermissionEntry;
import com.epam.pipeline.entity.security.acl.AclSecuredEntry;
import com.epam.pipeline.entity.security.acl.AclSid;
import com.epam.pipeline.entity.templates.FolderTemplate;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import com.epam.pipeline.manager.metadata.MetadataManager;
import com.epam.pipeline.manager.security.GrantPermissionManager;
import com.epam.pipeline.security.acl.AclPermission;
import com.epam.pipeline.test.acl.AbstractAclTest;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

public class FolderTemplateManagerTest extends AbstractAclTest {
    private static final String TEST_PATH = "path";
    private static final String TEST_ROLE = "TEST_ROLE";

    private static final String DATA_KEY_1 = "tag";
    private static final String DATA_TYPE_1 = "string";
    private static final String DATA_VALUE_1 = "OWNER";

    private static final String TEMPLATE_FOLDER_NAME = "test-folder";
    private static final String CHILD_TEMPLATE_FOLDER_NAME_1 = "test-1";
    private static final String DATASTORAGE_NAME_1 = "ds-1";

    @SpyBean
    private FolderTemplateManager folderTemplateManager;
    @Autowired
    private FolderManager folderManager;
    @Autowired
    private MetadataManager metadataManager;
    @MockBean
    private DataStorageManager dataStorageManager;
    @MockBean
    private GrantPermissionManager permissionManager;
    @MockBean
    private FolderCrudManager crudManager;

    private Map<String, PipeConfValue> metadata;
    private PermissionVO permissionVO;
    private FolderTemplate folderTemplate;

    @Test
    public void createFolderFromTemplateTest() throws IOException {
        initializeParameters();
        preConditions();

        Folder folder = new Folder();
        folder.setName(TEMPLATE_FOLDER_NAME);
        folderTemplateManager.createFolderFromTemplate(folder, folderTemplate);

        Folder savedRootFolder = folderManager.loadByNameOrId(TEMPLATE_FOLDER_NAME);
        savedRootFolder = folderManager.load(savedRootFolder.getId());
        Assert.assertNotNull(savedRootFolder);

        Long rootFolderId = savedRootFolder.getId();
        List<EntityVO> metadataEntries = Collections.singletonList(new EntityVO(rootFolderId, AclClass.FOLDER));
        Assert.assertEquals(metadata, metadataManager.listMetadataItems(metadataEntries).get(0).getData());

        AbstractDataStorage clonedDataStorage = savedRootFolder.getStorages().get(0);
        clonedDataStorage = dataStorageManager.load(clonedDataStorage.getId());
        Assert.assertTrue(clonedDataStorage.getName().startsWith(DATASTORAGE_NAME_1));
        Assert.assertTrue(clonedDataStorage.getPath().startsWith(TEST_PATH));

        metadataEntries = Collections.singletonList(new EntityVO(clonedDataStorage.getId(), AclClass.DATA_STORAGE));
        Assert.assertEquals(metadata, metadataManager.listMetadataItems(metadataEntries).get(0).getData());

        List<AclPermissionEntry> rootFolderPermissions = permissionManager.getPermissions(rootFolderId, AclClass.FOLDER)
                .getPermissions();
        Assert.assertEquals(1, rootFolderPermissions.size());

        AclPermissionEntry actualPermission = rootFolderPermissions.get(0);
        Assert.assertEquals(permissionVO.getMask(), actualPermission.getMask());
        Assert.assertEquals(permissionVO.getPrincipal(), actualPermission.getSid().isPrincipal());
        Assert.assertEquals(permissionVO.getUserName(), actualPermission.getSid().getName());

        Folder savedChildFolder = folderManager.loadByNameOrId(TEMPLATE_FOLDER_NAME + "/" +
                CHILD_TEMPLATE_FOLDER_NAME_1);
        Assert.assertNotNull(savedChildFolder);
        Assert.assertEquals(rootFolderId, savedChildFolder.getParentId());

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

        FolderTemplate childFolderTemplate1 = FolderTemplate.builder().name(CHILD_TEMPLATE_FOLDER_NAME_1).build();

        folderTemplate = FolderTemplate.builder()
                .name(TEMPLATE_FOLDER_NAME)
                .datastorages(Stream.of(dataStorageVO).collect(Collectors.toList()))
                .children(Stream.of(childFolderTemplate1).collect(Collectors.toList()))
                .metadata(metadata)
                .permissions(Stream.of(permissionVO).collect(Collectors.toList()))
                .build();
    }

    private void preConditions() {
        doReturn(getMockFolder()).when(crudManager).create(any());
        doReturn(getMockSecurityEntity()).when(dataStorageManager)
                .create(any(), anyBoolean(), anyBoolean(), anyBoolean());
        doNothing().when(metadataManager).updateEntityMetadata(any(), anyLong(), any());
        doNothing().when(permissionManager).setPermissionsToEntity(any(), anyLong(), any());

        doReturn(getMockFolder()).when(folderManager).loadByNameOrId(anyString());
        doReturn(getMockFolderChild()).when(folderManager).loadByNameOrId(startsWith(TEMPLATE_FOLDER_NAME));
        doReturn(getMockFolder()).when(folderManager).load(anyLong());

        doReturn(getMockListMetadataEntry()).when(metadataManager).listMetadataItems(any());
        doReturn(getS3BucketDataStorage()).when(dataStorageManager).load(anyLong());

        doReturn(getMockAclSecuredEntry()).when(permissionManager).getPermissions(anyLong(), any());
    }

    private void postValidation() {
        int oneInvocation = 1;
        int twoInvocations = 2;
        int threeInvocations = 3;

        verify(crudManager, times(twoInvocations)).create(any());
        verify(dataStorageManager, times(oneInvocation)).create(any(), anyBoolean(), anyBoolean(), anyBoolean());
        verify(dataStorageManager, times(oneInvocation)).load(anyLong());
        verify(metadataManager, times(threeInvocations)).updateEntityMetadata(any(), anyLong(), any());
        verify(metadataManager, times(twoInvocations)).listMetadataItems(any());
        verify(permissionManager, times(twoInvocations)).setPermissionsToEntity(any(), anyLong(), any());
        verify(permissionManager, times(oneInvocation)).getPermissions(anyLong(), any());
        verify(folderManager, times(oneInvocation)).load(anyLong());
        verify(folderManager, times(twoInvocations)).loadByNameOrId(any());
    }

    private SecuredEntityWithAction<AbstractDataStorage> getMockSecurityEntity() {
        SecuredEntityWithAction<AbstractDataStorage> action = new SecuredEntityWithAction<>();
        action.setEntity(new S3bucketDataStorage());
        return action;
    }

    private Folder getMockFolder() {
        Folder folder = new Folder();
        folder.setId(1L);
        folder.setStorages(Collections.singletonList(new S3bucketDataStorage()));
        return folder;
    }

    private Folder getMockFolderChild() {
        Folder folder = getMockFolder();
        folder.setParentId(1L);
        return folder;
    }

    private List<MetadataEntry> getMockListMetadataEntry() {
        List<MetadataEntry> list = new ArrayList<>();
        MetadataEntry entry = new MetadataEntry();
        entry.setData(metadata);
        list.add(entry);
        return list;
    }

    private Map<String, PipeConfValue> getMetadata() {
        Map<String, PipeConfValue> metadata = new HashMap<>();
        metadata.put(DATA_KEY_1, new PipeConfValue(DATA_TYPE_1, DATA_VALUE_1));
        return metadata;
    }

    private AbstractDataStorage getS3BucketDataStorage() {
        AbstractDataStorage storage = new S3bucketDataStorage();
        storage.setName(DATASTORAGE_NAME_1);
        storage.setPath(TEST_PATH);
        return storage;
    }

    private AclSecuredEntry getMockAclSecuredEntry() {
        AclSecuredEntry securedEntry = new AclSecuredEntry();
        AclPermissionEntry permissionEntry = new AclPermissionEntry();
        permissionEntry.setMask(permissionVO.getMask());
        AclSid sid = new AclSid();
        sid.setName(permissionVO.getUserName());
        sid.setPrincipal(permissionVO.getPrincipal());
        permissionEntry.setSid(sid);
        securedEntry.addPermission(permissionEntry);
        return securedEntry;
    }
}
