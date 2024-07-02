/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.manager.EntityManager;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import com.epam.pipeline.manager.datastorage.convert.DataStorageConvertManager;
import com.epam.pipeline.manager.datastorage.tag.DataStorageTagBatchManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.security.UserContext;
import com.epam.pipeline.test.acl.AbstractAclTest;
import com.epam.pipeline.test.creator.datastorage.DatastorageCreatorUtils;
import com.epam.pipeline.test.creator.pipeline.PipelineCreatorUtils;
import com.epam.pipeline.test.creator.security.SecurityCreatorUtils;
import org.springframework.beans.factory.annotation.Autowired;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID_2;
import static org.mockito.Mockito.doReturn;

abstract class AbstractDataStorageAclTest extends AbstractAclTest {

    protected final AbstractDataStorage s3bucket = DatastorageCreatorUtils.getS3bucketDataStorage(ID, OWNER_USER);
    protected final AbstractDataStorage anotherS3bucket =
            DatastorageCreatorUtils.getS3bucketDataStorage(ID_2, ANOTHER_SIMPLE_USER);
    protected final AbstractDataStorage notSharedS3bucket =
            DatastorageCreatorUtils.getS3bucketDataStorage(ID, SIMPLE_USER, false);
    protected final UserContext context = SecurityCreatorUtils.getUserContext();
    protected final UserContext externalContext = SecurityCreatorUtils.getUserContext(true);
    protected final Pipeline pipeline = PipelineCreatorUtils.getPipeline();

    @Autowired
    protected DataStorageApiService dataStorageApiService;

    @Autowired
    protected DataStorageManager mockDataStorageManager;

    @Autowired
    protected DataStorageTagBatchManager mockDataStorageTagBatchManager;

    @Autowired
    protected DataStorageConvertManager mockDataStorageConvertManager;

    @Autowired
    protected AuthManager mockAuthManager;

    @Autowired
    protected EntityManager mockEntityManager;

    @Autowired
    protected PreferenceManager preferenceManager;

    protected void initUserAndEntityMocks(final String user,
                                          final AbstractDataStorage entity,
                                          final UserContext context) {
        mockAuthUser(user);
        mockStorage(entity);
        mockUserContext(context);
    }

    protected void mockStorage(final AbstractDataStorage entity) {
        doReturn(entity).when(mockEntityManager).load(AclClass.DATA_STORAGE, entity.getId());
    }

    @Override
    protected void mockUserContext(final UserContext context) {
        doReturn(context).when(mockAuthManager).getUserContext();
    }
}
