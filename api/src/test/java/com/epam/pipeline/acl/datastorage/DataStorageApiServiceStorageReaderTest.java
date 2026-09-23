/*
 * Copyright 2026 EPAM Systems, Inc. (https://www.epam.com/)
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
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageAction;
import com.epam.pipeline.entity.datastorage.TemporaryCredentials;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.manager.cloud.TemporaryCredentialsManager;
import com.epam.pipeline.manager.datastorage.DataStorageApiService;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import com.epam.pipeline.security.UserContext;
import com.epam.pipeline.security.acl.AclPermission;
import com.epam.pipeline.test.acl.AbstractAclTest;
import com.epam.pipeline.test.creator.datastorage.DatastorageCreatorUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.util.AopTestUtils;

import java.util.Collections;
import java.util.List;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

/**
 * ROLE_STORAGE_READER checks of {@link DataStorageApiService}, which is not scanned into the ACL test context
 * on this branch, hence the extra context class.
 */
@ContextConfiguration(classes = DataStorageApiService.class)
public class DataStorageApiServiceStorageReaderTest extends AbstractAclTest {

    private static final int READ_PERMISSION = 1;
    private static final int READ_AND_WRITE_PERMISSION = 3;
    private static final long SECS_IN_HOUR = 3600L;

    private final AbstractDataStorage s3bucket = DatastorageCreatorUtils.getS3bucketDataStorage(ID, OWNER_USER);
    private final DataStorageVO dataStorageVO = DatastorageCreatorUtils.getDataStorageVO();
    private final TemporaryCredentials temporaryCredentials = DatastorageCreatorUtils.getTemporaryCredentials();
    private final UserContext context = new UserContext();
    private final UserContext externalContext = getExternalUserContext();

    @Autowired
    private DataStorageApiService dataStorageApiService;

    @Autowired
    private DataStorageManager mockDataStorageManager;

    @Autowired
    private TemporaryCredentialsManager mockTemporaryCredentialsManager;

    @Test
    @WithMockUser(username = SIMPLE_USER, roles = STORAGE_READER_ROLE)
    public void shouldReturnDataStoragesWithReadMaskForStorageReader() {
        initAclEntity(s3bucket);
        initUserAndEntityMocks(SIMPLE_USER, s3bucket, context);
        doReturn(mutableListOf(s3bucket)).when(mockDataStorageManager).getDataStorages();

        final List<AbstractDataStorage> returnedDataStorages = dataStorageApiService.getDataStorages();

        assertThat(returnedDataStorages).hasSize(1).contains(s3bucket);
        assertThat(returnedDataStorages.get(0).getMask()).isEqualTo(READ_PERMISSION);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, roles = STORAGE_READER_ROLE)
    public void shouldReturnDataStoragesWithReadMaskForStorageReaderWhenReadIsDenied() {
        initAclEntity(s3bucket, AclPermission.NO_READ);
        initUserAndEntityMocks(SIMPLE_USER, s3bucket, context);
        doReturn(mutableListOf(s3bucket)).when(mockDataStorageManager).getDataStorages();

        final List<AbstractDataStorage> returnedDataStorages = dataStorageApiService.getDataStorages();

        assertThat(returnedDataStorages).hasSize(1).contains(s3bucket);
        assertThat(returnedDataStorages.get(0).getMask()).isEqualTo(READ_PERMISSION);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, roles = STORAGE_READER_ROLE)
    public void shouldKeepGrantedWriteInMaskForStorageReader() {
        initAclEntity(s3bucket, AclPermission.WRITE);
        initUserAndEntityMocks(SIMPLE_USER, s3bucket, context);
        doReturn(mutableListOf(s3bucket)).when(mockDataStorageManager).getDataStorages();

        final List<AbstractDataStorage> returnedDataStorages = dataStorageApiService.getAvailableStorages();

        assertThat(returnedDataStorages).hasSize(1).contains(s3bucket);
        assertThat(returnedDataStorages.get(0).getMask()).isEqualTo(READ_AND_WRITE_PERMISSION);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, roles = STORAGE_READER_ROLE)
    public void shouldReturnEmptyWritableDataStorageListForStorageReader() {
        initAclEntity(s3bucket);
        initUserAndEntityMocks(SIMPLE_USER, s3bucket, context);
        doReturn(mutableListOf(s3bucket)).when(mockDataStorageManager).getDataStorages();

        assertThat(dataStorageApiService.getWritableStorages()).isEmpty();
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, roles = STORAGE_READER_ROLE)
    public void shouldLoadDataStorageForStorageReader() {
        initAclEntity(s3bucket);
        doReturn(s3bucket).when(mockDataStorageManager).load(ID);
        initUserAndEntityMocks(SIMPLE_USER, s3bucket, context);

        final AbstractDataStorage returnedDataStorage = dataStorageApiService.load(ID);

        assertThat(returnedDataStorage).isEqualTo(s3bucket);
        assertThat(returnedDataStorage.getMask()).isEqualTo(READ_PERMISSION);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, roles = STORAGE_READER_ROLE)
    public void shouldDenyLoadNotSharedDataStorageForExternalStorageReader() {
        final AbstractDataStorage notSharedStorage =
                DatastorageCreatorUtils.getS3bucketDataStorage(ID, ANOTHER_SIMPLE_USER, false);
        initAclEntity(notSharedStorage);
        doReturn(notSharedStorage).when(mockDataStorageManager).load(ID);
        initUserAndEntityMocks(SIMPLE_USER, notSharedStorage, externalContext);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService.load(ID));
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, roles = STORAGE_READER_ROLE)
    public void shouldDenyUpdateDataStorageForStorageReader() {
        initAclEntity(s3bucket);
        initUserAndEntityMocks(SIMPLE_USER, s3bucket, context);
        final DataStorageManager target = AopTestUtils.getUltimateTargetObject(mockDataStorageManager);
        doReturn(s3bucket).when(target).update(dataStorageVO);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService.update(dataStorageVO));
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, roles = STORAGE_READER_ROLE)
    public void shouldDenyRequestDavMountForStorageReader() {
        initAclEntity(s3bucket);
        initUserAndEntityMocks(SIMPLE_USER, s3bucket, context);

        assertThrows(AccessDeniedException.class, () ->
                dataStorageApiService.requestDataStorageDavMount(s3bucket.getId(), SECS_IN_HOUR));
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, roles = STORAGE_READER_ROLE)
    public void shouldGenerateReadCredentialsForStorageReader() {
        final DataStorageAction dataStorageAction = DatastorageCreatorUtils.getDataStorageAction();
        dataStorageAction.setRead(true);
        final List<DataStorageAction> dataStorageActionList = Collections.singletonList(dataStorageAction);
        initAclEntity(s3bucket);
        initUserAndEntityMocks(SIMPLE_USER, s3bucket, context);
        doReturn(temporaryCredentials).when(mockTemporaryCredentialsManager).generate(dataStorageActionList);

        assertThat(dataStorageApiService.generateCredentials(dataStorageActionList)).isEqualTo(temporaryCredentials);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, roles = STORAGE_READER_ROLE)
    public void shouldDenyGenerateWriteCredentialsForStorageReader() {
        final DataStorageAction dataStorageAction = DatastorageCreatorUtils.getDataStorageAction();
        dataStorageAction.setRead(true);
        dataStorageAction.setWrite(true);
        final List<DataStorageAction> dataStorageActionList = Collections.singletonList(dataStorageAction);
        initAclEntity(s3bucket);
        initUserAndEntityMocks(SIMPLE_USER, s3bucket, context);
        doReturn(temporaryCredentials).when(mockTemporaryCredentialsManager).generate(dataStorageActionList);

        assertThrows(AccessDeniedException.class, () ->
                dataStorageApiService.generateCredentials(dataStorageActionList));
    }

    private void initUserAndEntityMocks(final String user, final AbstractDataStorage entity,
                                        final UserContext userContext) {
        mockAuthUser(user);
        doReturn(entity).when(mockEntityManager).load(AclClass.DATA_STORAGE, entity.getId());
        doReturn(userContext).when(mockAuthManager).getUserContext();
    }

    private static UserContext getExternalUserContext() {
        final UserContext userContext = new UserContext();
        userContext.setExternal(true);
        return userContext;
    }
}
