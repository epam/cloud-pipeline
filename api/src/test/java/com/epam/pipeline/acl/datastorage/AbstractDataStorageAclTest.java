/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.acl.datastorage;

import com.epam.pipeline.controller.vo.DataStorageVO;
import com.epam.pipeline.controller.vo.data.storage.UpdateDataStorageItemVO;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.AbstractDataStorageItem;
import com.epam.pipeline.entity.datastorage.DataStorageDownloadFileUrl;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.DataStorageItemContent;
import com.epam.pipeline.entity.datastorage.DataStorageListing;
import com.epam.pipeline.entity.datastorage.DataStorageStreamingContent;
import com.epam.pipeline.entity.datastorage.DataStorageWithShareMount;
import com.epam.pipeline.entity.datastorage.PathDescription;
import com.epam.pipeline.entity.datastorage.StorageMountPath;
import com.epam.pipeline.entity.datastorage.StorageUsage;
import com.epam.pipeline.entity.datastorage.rules.DataStorageRule;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.manager.EntityManager;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import com.epam.pipeline.manager.datastorage.DataStorageRuleManager;
import com.epam.pipeline.manager.datastorage.RunMountService;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.security.GrantPermissionManager;
import com.epam.pipeline.security.UserContext;
import com.epam.pipeline.test.acl.AbstractAclTest;
import com.epam.pipeline.test.creator.datastorage.DatastorageCreatorUtils;
import com.epam.pipeline.test.creator.pipeline.PipelineCreatorUtils;
import com.epam.pipeline.test.creator.security.SecurityCreatorUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID_2;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static org.mockito.Mockito.doReturn;

abstract class AbstractDataStorageAclTest extends AbstractAclTest {

    protected final AbstractDataStorage s3bucket = DatastorageCreatorUtils.getS3bucketDataStorage(ID, OWNER_USER);
    protected final AbstractDataStorage anotherS3bucket =
            DatastorageCreatorUtils.getS3bucketDataStorage(ID_2, TEST_STRING);
    protected final DataStorageWithShareMount storageShareMount =
            DatastorageCreatorUtils.getDataStorageWithShareMount();
    protected final Authentication authentication = new TestingAuthenticationToken(new Object(), new Object());
    protected final UserContext context = SecurityCreatorUtils.getUserContext();
    protected final DataStorageListing dataStorageListing = DatastorageCreatorUtils.getDataStorageListing();
    protected final AbstractDataStorageItem dataStorageFile = DatastorageCreatorUtils.getDataStorageFile();
    protected final InputStream inputStream = new ByteArrayInputStream(TEST_STRING.getBytes());
    protected final DataStorageDownloadFileUrl downloadFileUrl =
            DatastorageCreatorUtils.getDataStorageDownloadFileUrl();
    protected final Pipeline pipeline = PipelineCreatorUtils.getPipeline(OWNER_USER);
    protected final DataStorageRule dataStorageRule = DatastorageCreatorUtils.getDataStorageRule();
    protected final DataStorageItemContent dataStorageItemContent =
            DatastorageCreatorUtils.getDefaultDataStorageItemContent();
    protected final DataStorageStreamingContent dataStorageStreamingContent =
            DatastorageCreatorUtils.getDefaultDataStorageStreamingContent(inputStream);
    protected final List<PathDescription> pathDescriptionList = DatastorageCreatorUtils.getPathDescriptionList();
    protected final StorageUsage storageUsage = DatastorageCreatorUtils.getStorageUsage();
    protected final StorageMountPath storageMountPath = DatastorageCreatorUtils.getStorageMountPath();
    protected final DataStorageVO dataStorageVO = DatastorageCreatorUtils.getDataStorageVO();

    protected final List<String> testList = Collections.singletonList(TEST_STRING);
    protected final List<UpdateDataStorageItemVO> dataStorageItemVOList =
            DatastorageCreatorUtils.getUpdateDataStorageItemVOList();
    protected final List<DataStorageFile> dataStorageFileList = DatastorageCreatorUtils.getDataStorageFileList();
    protected final List<DataStorageDownloadFileUrl> downloadFileUrlList =
            DatastorageCreatorUtils.getDataStorageDownloadFileUrlList();
    protected final List<DataStorageRule> dataStorageRuleList = DatastorageCreatorUtils.getDataStorageRuleList();

    @Autowired
    protected DataStorageApiService dataStorageApiService;

    @Autowired
    protected GrantPermissionManager grantPermissionManager;

    @Autowired
    protected DataStorageManager mockDataStorageManager;

    @Autowired
    protected DataStorageRuleManager mockDataStorageRuleManager;

    @Autowired
    protected AuthManager mockAuthManager;

    @Autowired
    protected EntityManager mockEntityManager;

    @Autowired
    protected RunMountService mockRunMountService;

    protected void initMocks(String user, UserContext context) {
        mockAuthUser(user);
        mockS3bucket();
        doReturn(context).when(mockAuthManager).getUserContext();
    }

    protected void mockAuthUser(String user) {
        doReturn(user).when(mockAuthManager).getAuthorizedUser();
        doReturn(authentication).when(mockAuthManager).getAuthentication();
    }

    protected void mockS3bucket() {
        doReturn(s3bucket).when(mockEntityManager).load(AclClass.DATA_STORAGE, ID);
    }
}
