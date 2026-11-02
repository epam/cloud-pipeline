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
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.manager.datastorage.DataStorageApiService;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import com.epam.pipeline.security.UserContext;
import com.epam.pipeline.test.acl.AbstractAclTest;
import com.epam.pipeline.test.creator.datastorage.DatastorageCreatorUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.util.AopTestUtils;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

/**
 * S3 access point ARN checks of {@link DataStorageApiService#update(DataStorageVO)}: only admins may set or
 * clear it on an existing storage. {@link DataStorageApiService} is not scanned into the ACL test context
 * on this branch, hence the extra context class.
 */
@ContextConfiguration(classes = DataStorageApiService.class)
public class DataStorageApiServiceAccessPointArnTest extends AbstractAclTest {

    private static final String ACCESS_POINT_ARN = "arn:aws:s3:us-east-1:123456789012:accesspoint/test-ap";

    private final AbstractDataStorage s3bucket = DatastorageCreatorUtils.getS3bucketDataStorage(ID, OWNER_USER);
    private final UserContext context = new UserContext();

    @Autowired
    private DataStorageApiService dataStorageApiService;

    @Autowired
    private DataStorageManager mockDataStorageManager;

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldUpdateAccessPointArnForAdmin() {
        final DataStorageVO storageVO = getDataStorageVOWithAccessPointArn(ACCESS_POINT_ARN);
        initAclEntity(s3bucket);
        mockUpdate(storageVO);

        assertThat(dataStorageApiService.update(storageVO)).isEqualTo(s3bucket);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, roles = STORAGE_ADMIN_ROLE)
    public void shouldUpdateAccessPointArnForStorageAdmin() {
        final DataStorageVO storageVO = getDataStorageVOWithAccessPointArn(ACCESS_POINT_ARN);
        initAclEntity(s3bucket);
        initUserAndEntityMocks(SIMPLE_USER);
        mockUpdate(storageVO);

        assertThat(dataStorageApiService.update(storageVO)).isEqualTo(s3bucket);
    }

    @Test
    @WithMockUser(username = OWNER_USER)
    public void shouldUpdateStorageWithoutAccessPointArnForOwner() {
        final DataStorageVO storageVO = getDataStorageVOWithAccessPointArn(null);
        initAclEntity(s3bucket);
        initUserAndEntityMocks(OWNER_USER);
        mockUpdate(storageVO);

        assertThat(dataStorageApiService.update(storageVO)).isEqualTo(s3bucket);
    }

    @Test
    @WithMockUser(username = OWNER_USER)
    public void shouldDenyUpdateAccessPointArnForOwner() {
        final DataStorageVO storageVO = getDataStorageVOWithAccessPointArn(ACCESS_POINT_ARN);
        initAclEntity(s3bucket);
        initUserAndEntityMocks(OWNER_USER);
        mockUpdate(storageVO);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService.update(storageVO));
    }

    @Test
    @WithMockUser(username = OWNER_USER)
    public void shouldDenyClearAccessPointArnForOwner() {
        final DataStorageVO storageVO = getDataStorageVOWithAccessPointArn("");
        initAclEntity(s3bucket);
        initUserAndEntityMocks(OWNER_USER);
        mockUpdate(storageVO);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService.update(storageVO));
    }

    private void mockUpdate(final DataStorageVO storageVO) {
        final DataStorageManager target = AopTestUtils.getUltimateTargetObject(mockDataStorageManager);
        doReturn(s3bucket).when(target).update(storageVO);
    }

    private void initUserAndEntityMocks(final String user) {
        mockAuthUser(user);
        doReturn(s3bucket).when(mockEntityManager).load(AclClass.DATA_STORAGE, s3bucket.getId());
        doReturn(context).when(mockAuthManager).getUserContext();
    }

    private static DataStorageVO getDataStorageVOWithAccessPointArn(final String accessPointArn) {
        final DataStorageVO storageVO = DatastorageCreatorUtils.getDataStorageVO();
        storageVO.setAccessPointArn(accessPointArn);
        return storageVO;
    }
}
