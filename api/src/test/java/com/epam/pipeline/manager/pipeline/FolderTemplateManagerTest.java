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
import com.epam.pipeline.manager.EntityManager;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import com.epam.pipeline.manager.metadata.MetadataManager;
import com.epam.pipeline.manager.security.GrantPermissionManager;
import com.epam.pipeline.mapper.PermissionGrantVOMapper;
import com.epam.pipeline.security.acl.AclPermission;
import com.epam.pipeline.security.acl.JdbcMutableAclServiceImpl;
import com.epam.pipeline.test.acl.AbstractAclTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.acls.domain.PrincipalSid;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

import static com.epam.pipeline.test.creator.pipeline.FolderTemplateCreatorUtils.getFolderTemplate;
import static com.epam.pipeline.test.creator.pipeline.FolderTemplateCreatorUtils.getS3BucketDataStorageWithMetadataNameAndPath;
import static com.epam.pipeline.test.creator.security.PermissionCreatorUtils.getPermissionGrantVOFrom;
import static com.epam.pipeline.test.creator.security.PermissionCreatorUtils.getPermissionVO;
import static com.epam.pipeline.util.CustomMatchers.matches;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

public class FolderTemplateManagerTest extends AbstractAclTest {
    private static final String DATA_KEY_1 = "tag";
    private static final String DATA_TYPE_1 = "string";
    private static final String DATA_VALUE_1 = "OWNER";
    private static final String DATA_KEY_2 = "tag-2";
    private static final String DATA_TYPE_2 = "string-2";
    private static final String DATA_VALUE_2 = "OWNER-2";

    private static final String CHILD_TEMPLATE_FOLDER_NAME = "test-1";
    private static final String DATASTORAGE_NAME = "ds-1";
    private static final String DATASTORAGE_NAME_2 = "ds-2";

    private static final String PERMISSION_NAME = "PERMISSION";
    private static final String PERMISSION_NAME_2 = "PERMISSION_2";

    private static final Long FOLDER_ID = 1L;
    private static final Long FOLDER_CHILD_ID = 2L;

    private Folder folder;
    private Folder childFolder;
    private AbstractDataStorage folderDataStorage;
    private AbstractDataStorage childFolderDataStorage;

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
    @Autowired
    private PermissionGrantVOMapper mockPermissionGrantVOMapper;
    @Autowired
    private EntityManager mockEntityManager;
    @Autowired
    private JdbcMutableAclServiceImpl aclService;

    @Test
    public void createFolderFromTemplateTest() {
        Map<String, PipeConfValue> metadata = getMetadata();
        Map<String, PipeConfValue> childMetadata = getChildMetadata();
        DataStorageWithMetadataVO dataStorageVO = getS3BucketDataStorageWithMetadataNameAndPath(metadata,
                DATASTORAGE_NAME, TEST_PATH);
        DataStorageWithMetadataVO dataStorageForChild = getS3BucketDataStorageWithMetadataNameAndPath(childMetadata,
                DATASTORAGE_NAME_2, TEST_PATH_2);
        PermissionVO permissionVO = getPermissionVO(PERMISSION_NAME);
        PermissionVO childPermissionVO = getPermissionVO(PERMISSION_NAME_2);
        FolderTemplate childFolderTemplate = getFolderTemplate(dataStorageForChild, null,
                childMetadata, childPermissionVO, TEST_NAME_2);
        FolderTemplate folderTemplate = getFolderTemplate(dataStorageVO, childFolderTemplate,
                metadata, permissionVO, TEST_NAME);

        folderDataStorage = new S3bucketDataStorage(FOLDER_ID, DATASTORAGE_NAME, TEST_PATH);
        childFolderDataStorage = new S3bucketDataStorage(FOLDER_CHILD_ID, DATASTORAGE_NAME_2, TEST_PATH_2);

        childFolder = getFolderChild(childFolderDataStorage);
        folder = getFolder(folderDataStorage);

        doReturn(folder).when(mockFolderCrudManager)
                .create(argThat(matches(Predicates.forFolderTemplate(folderTemplate.getName()))));
        doReturn(childFolder).when(mockFolderCrudManager)
                .create(argThat(matches(Predicates.forChildFolderTemplate(childFolderTemplate.getName()))));
        doReturn(getSecurityEntityForFolder()).when(mockDataStorageManager)
                .create(argThat(matches(Predicates.forDataStorage())), eq(true), eq(true), eq(false));
        doReturn(getSecurityEntityForFolderChild()).when(mockDataStorageManager)
                .create(argThat(matches(Predicates.forChildDataStorage())), eq(true), eq(true), eq(false));

        //permissionGrantVOMapper doesn't work, so mapping it manually
        doReturn(getPermissionGrantVOFrom(permissionVO, AclClass.FOLDER, FOLDER_ID)).when(mockPermissionGrantVOMapper)
                .toPermissionGrantVO(eq(permissionVO));
        doReturn(getPermissionGrantVOFrom(childPermissionVO, AclClass.FOLDER, FOLDER_CHILD_ID))
                .when(mockPermissionGrantVOMapper).toPermissionGrantVO(eq(childPermissionVO));

        doReturn(folder).when(mockEntityManager).load(eq(AclClass.FOLDER), eq(FOLDER_ID));
        doReturn(childFolder).when(mockEntityManager).load(eq(AclClass.FOLDER), eq(FOLDER_CHILD_ID));
        doReturn(new PrincipalSid(folder.getOwner())).when(aclService).createOrGetSid(
                eq(permissionVO.getUserName().toUpperCase()), eq(permissionVO.getPrincipal()));
        doReturn(new PrincipalSid(childFolder.getOwner())).when(aclService).createOrGetSid(
                eq(childPermissionVO.getUserName().toUpperCase()), eq(childPermissionVO.getPrincipal()));

        initAclEntity(folder, AclPermission.READ);
        initAclEntity(childFolder, AclPermission.READ);

        folderTemplateManager.createFolderFromTemplate(folder, folderTemplate);

        verify(mockFolderCrudManager).create(argThat(matches(Predicates.forFolderTemplate(folderTemplate.getName()))));
        verify(mockFolderCrudManager).create(argThat(matches(
                Predicates.forChildFolderTemplate(childFolderTemplate.getName()))));
        verify(mockDataStorageManager).create(argThat(matches(Predicates.forDataStorage())),
                eq(true), eq(true), eq(false));
        verify(mockDataStorageManager).create(argThat(matches(Predicates.forDataStorage())),
                eq(true), eq(true), eq(false));
        verify(mockMetadataManager).updateEntityMetadata(eq(metadata), eq(FOLDER_ID), eq(AclClass.DATA_STORAGE));
        verify(mockMetadataManager).updateEntityMetadata(eq(metadata), eq(FOLDER_ID), eq(AclClass.FOLDER));
        verify(mockMetadataManager).updateEntityMetadata(eq(childMetadata),
                eq(FOLDER_CHILD_ID), eq(AclClass.DATA_STORAGE));
        verify(mockMetadataManager).updateEntityMetadata(eq(childMetadata), eq(FOLDER_CHILD_ID), eq(AclClass.FOLDER));

        verify(mockPermissionGrantVOMapper).toPermissionGrantVO(eq(permissionVO));
        verify(mockPermissionGrantVOMapper).toPermissionGrantVO(eq(childPermissionVO));
        verify(mockEntityManager).load(eq(AclClass.FOLDER), eq(FOLDER_ID));
        verify(mockEntityManager).load(eq(AclClass.FOLDER), eq(FOLDER_CHILD_ID));
        verify(aclService).createOrGetSid(eq(permissionVO.getUserName()), eq(permissionVO.getPrincipal()));
        verify(aclService).createOrGetSid(eq(childPermissionVO.getUserName()), eq(childPermissionVO.getPrincipal()));
    }

    private SecuredEntityWithAction<AbstractDataStorage> getSecurityEntityForFolder() {
        SecuredEntityWithAction<AbstractDataStorage> action = new SecuredEntityWithAction<>();
        action.setEntity(folderDataStorage);
        return action;
    }

    private SecuredEntityWithAction<AbstractDataStorage> getSecurityEntityForFolderChild() {
        SecuredEntityWithAction<AbstractDataStorage> action = new SecuredEntityWithAction<>();
        action.setEntity(childFolderDataStorage);
        return action;
    }

    private Folder getFolder(AbstractDataStorage dataStorage) {
        Folder folder = new Folder();
        folder.setId(FOLDER_ID);
        folder.setOwner(SIMPLE_USER);
        folder.setChildFolders(Collections.singletonList(childFolder));
        folder.setStorages(Collections.singletonList(dataStorage));
        return folder;
    }

    private Folder getFolderChild(AbstractDataStorage dataStorage) {
        Folder folder = new Folder();
        folder.setId(FOLDER_CHILD_ID);
        folder.setOwner(SIMPLE_USER);
        folder.setName(CHILD_TEMPLATE_FOLDER_NAME);
        folder.setParentId(FOLDER_ID);
        folder.setStorages(Collections.singletonList(dataStorage));
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
