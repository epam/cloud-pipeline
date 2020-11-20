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

package com.epam.pipeline.acl.entity;

import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.security.acl.AclSid;
import com.epam.pipeline.manager.EntityManager;
import com.epam.pipeline.manager.HierarchicalEntityManager;
import com.epam.pipeline.security.acl.AclPermission;
import com.epam.pipeline.test.acl.AbstractAclTest;
import com.epam.pipeline.test.creator.datastorage.DatastorageCreatorUtils;
import com.epam.pipeline.test.creator.security.SecurityCreatorUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

public class EntityApiServiceTest extends AbstractAclTest {

    private final S3bucketDataStorage s3bucket = DatastorageCreatorUtils.getS3bucketDataStorage(ID, OWNER_USER);
    private final Map<AclClass, List<AbstractSecuredEntity>> aclEntityMap =
            Collections.singletonMap(AclClass.DATA_STORAGE, Collections.singletonList(s3bucket));
    private final AclSid aclSid = SecurityCreatorUtils.getAclSid();

    @Autowired
    private EntityApiService entityApiService;

    @Autowired
    private EntityManager mockEntityManager;

    @Autowired
    private HierarchicalEntityManager mockHierarchicalEntityManager;

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadEntityForAdmin() {
        doReturn(s3bucket).when(mockEntityManager).loadByNameOrId(AclClass.DATA_STORAGE, TEST_STRING);

        assertThat(entityApiService.loadByNameOrId(AclClass.DATA_STORAGE, TEST_STRING)).isEqualTo(s3bucket);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldLoadEntityWhenPermissionIsGranted() {
        s3bucket.setAclClass(AclClass.DATA_STORAGE);
        initAclEntity(s3bucket, AclPermission.READ);
        doReturn(s3bucket).when(mockEntityManager).loadByNameOrId(AclClass.DATA_STORAGE, TEST_STRING);
        doReturn(s3bucket).when(mockEntityManager).load(AclClass.DATA_STORAGE, ID);
        mockSecurityContext();
        assertThat(entityApiService.loadByNameOrId(AclClass.DATA_STORAGE, TEST_STRING)).isEqualTo(s3bucket);
    }

    @Test
    @WithMockUser
    public void shouldDenyLoadEntityWhenPermissionIsNotGranted() {
        s3bucket.setAclClass(AclClass.DATA_STORAGE);
        initAclEntity(s3bucket);
        doReturn(s3bucket).when(mockEntityManager).loadByNameOrId(AclClass.DATA_STORAGE, TEST_STRING);
        doReturn(s3bucket).when(mockEntityManager).load(AclClass.DATA_STORAGE, ID);
        mockSecurityContext();

        assertThrows(AccessDeniedException.class, () ->
                entityApiService.loadByNameOrId(AclClass.DATA_STORAGE, TEST_STRING));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadAvailableEntityForAdmin() {
        doReturn(aclEntityMap).when(mockHierarchicalEntityManager).loadAvailable(aclSid, AclClass.DATA_STORAGE);

        assertThat(entityApiService.loadAvailable(aclSid, AclClass.DATA_STORAGE)).isEqualTo(aclEntityMap);
    }

    @Test
    @WithMockUser()
    public void shouldDenyLoadAvailableEntityWhenRoleIsNotValid() {
        doReturn(aclEntityMap).when(mockHierarchicalEntityManager).loadAvailable(aclSid, AclClass.DATA_STORAGE);

        assertThrows(AccessDeniedException.class, () ->
                entityApiService.loadAvailable(aclSid, AclClass.DATA_STORAGE));
    }
}
