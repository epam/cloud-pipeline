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

import com.epam.pipeline.AbstractSpringTest;
import com.epam.pipeline.app.TestApplicationWithAclSecurity;
import com.epam.pipeline.controller.vo.EntityVO;
import com.epam.pipeline.controller.vo.PermissionVO;
import com.epam.pipeline.controller.vo.data.storage.DataStorageWithMetadataVO;
import com.epam.pipeline.dao.region.CloudRegionDao;
import com.epam.pipeline.dao.util.AclTestDao;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.security.acl.AclPermissionEntry;
import com.epam.pipeline.entity.templates.FolderTemplate;
import com.epam.pipeline.manager.MockS3Helper;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import com.epam.pipeline.manager.datastorage.providers.aws.s3.S3StorageProvider;
import com.epam.pipeline.manager.metadata.MetadataManager;
import com.epam.pipeline.manager.security.GrantPermissionManager;
import com.epam.pipeline.security.acl.AclPermission;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;

@DirtiesContext
@ContextConfiguration(classes = TestApplicationWithAclSecurity.class)
@Transactional
public class FolderTemplateManagerTest extends AbstractSpringTest {
    private static final String TEST_PATH = "path";
    private static final String TEST_USER = "USER1";
    private static final String TEST_ROLE = "TEST_ROLE";

    private static final String DATA_KEY_1 = "tag";
    private static final String DATA_TYPE_1 = "string";
    private static final String DATA_VALUE_1 = "OWNER";

    private static final String TEMPLATE_FOLDER_NAME = "test-folder";
    private static final String CHILD_TEMPLATE_FOLDER_NAME_1 = "test-1";
    private static final String DATASTORAGE_NAME_1 = "ds-1";

    @Autowired
    private FolderTemplateManager folderTemplateManager;
    @Autowired
    private FolderManager folderManager;
    @Autowired
    private MetadataManager metadataManager;
    @Autowired
    private DataStorageManager dataStorageManager;
    @SpyBean
    private S3StorageProvider storageProviderManager;
    @Autowired
    private AclTestDao aclTestDao;
    @Autowired
    private GrantPermissionManager permissionManager;
    @Autowired
    private CloudRegionDao cloudRegionDao;

    private AwsRegion awsRegion;


    @Before
    public void setUp() {
        doReturn(new MockS3Helper()).when(storageProviderManager).getS3Helper(any());

        awsRegion = new AwsRegion();
        awsRegion.setName("US");
        awsRegion.setRegionCode("us-east-1");
        awsRegion.setDefault(true);
        cloudRegionDao.create(awsRegion);

        AclTestDao.AclSid testUserSid = new AclTestDao.AclSid(true, TEST_USER);
        aclTestDao.createAclSid(testUserSid);
        AclTestDao.AclSid testRole = new AclTestDao.AclSid(false, TEST_ROLE);
        aclTestDao.createAclSid(testRole);

        AclTestDao.AclClass folderAclClass = new AclTestDao.AclClass(Folder.class.getCanonicalName());
        aclTestDao.createAclClassIfNotPresent(folderAclClass);
    }

    @Test
    @WithMockUser(username = TEST_USER)
    public void createFolderFromTemplateTest() throws IOException {
        Map<String, PipeConfValue> metadata = new HashMap<>();
        metadata.put(DATA_KEY_1, new PipeConfValue(DATA_TYPE_1, DATA_VALUE_1));

        DataStorageWithMetadataVO dataStorageVO = new DataStorageWithMetadataVO();
        dataStorageVO.setName(DATASTORAGE_NAME_1);
        dataStorageVO.setType(DataStorageType.S3);
        dataStorageVO.setPath(TEST_PATH);
        dataStorageVO.setMetadata(metadata);

        PermissionVO permissionVO = new PermissionVO();
        permissionVO.setMask(AclPermission.READ.getMask());
        permissionVO.setUserName(TEST_ROLE);
        permissionVO.setPrincipal(false);

        FolderTemplate childFolderTemplate1 = FolderTemplate.builder().name(CHILD_TEMPLATE_FOLDER_NAME_1).build();
        FolderTemplate folderTemplate = FolderTemplate.builder()
                .name(TEMPLATE_FOLDER_NAME)
                .datastorages(Stream.of(dataStorageVO).collect(Collectors.toList()))
                .children(Stream.of(childFolderTemplate1).collect(Collectors.toList()))
                .metadata(metadata)
                .permissions(Stream.of(permissionVO).collect(Collectors.toList()))
                .build();

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
    }
}
