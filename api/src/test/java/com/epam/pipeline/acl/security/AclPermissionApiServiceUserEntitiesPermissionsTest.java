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

package com.epam.pipeline.acl.security;

import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.security.acl.EntityPermission;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.manager.security.AclPermissionApiService;
import com.epam.pipeline.manager.security.GrantPermissionManager;
import com.epam.pipeline.security.acl.AclPermission;
import com.epam.pipeline.test.acl.AbstractAclTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.List;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID_2;
import static com.epam.pipeline.test.creator.datastorage.DatastorageCreatorUtils.getS3bucketDataStorage;
import static com.epam.pipeline.test.creator.user.UserCreatorUtils.getPipelineUser;
import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

public class AclPermissionApiServiceUserEntitiesPermissionsTest extends AbstractAclTest {

    private static final AclClass DATA_STORAGE = AclClass.DATA_STORAGE;

    private final S3bucketDataStorage anotherS3bucket = getS3bucketDataStorage(ID, ANOTHER_SIMPLE_USER);
    private final S3bucketDataStorage anotherS3bucket2 = getS3bucketDataStorage(ID_2, ANOTHER_SIMPLE_USER);
    private final PipelineUser targetUser = getPipelineUser(ANOTHER_SIMPLE_USER, ID);

    @Autowired
    private GrantPermissionManager spyPermissionManager;

    @Autowired
    private AclPermissionApiService aclPermissionApiService;

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadUserEntitiesPermissionsOfAllEntitiesForAdmin() {
        final List<EntityPermission> permissions = mutableListOf(
                getEntityPermission(anotherS3bucket), getEntityPermission(anotherS3bucket2));
        doReturn(permissions).when(spyPermissionManager).loadUserEntitiesPermissions(ID, DATA_STORAGE);

        assertThat(aclPermissionApiService.loadUserEntitiesPermissions(ID, DATA_STORAGE))
                .extracting(EntityPermission::getEntity)
                .containsExactly(anotherS3bucket, anotherS3bucket2);
    }

    @Test
    @WithMockUser(SIMPLE_USER)
    public void shouldLoadUserEntitiesPermissionsOfReadableEntitiesOnlyForNonAdmin() {
        initAclEntity(targetUser, AclPermission.READ);
        initAclEntity(anotherS3bucket, AclPermission.READ);
        initAclEntity(anotherS3bucket2);
        final List<EntityPermission> permissions = mutableListOf(
                getEntityPermission(anotherS3bucket), getEntityPermission(anotherS3bucket2));
        doReturn(permissions).when(spyPermissionManager).loadUserEntitiesPermissions(ID, DATA_STORAGE);
        mockAuthUser(SIMPLE_USER);

        assertThat(aclPermissionApiService.loadUserEntitiesPermissions(ID, DATA_STORAGE))
                .extracting(EntityPermission::getEntity)
                .containsExactly(anotherS3bucket);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, roles = USER_READER_ROLE)
    public void shouldLoadUserEntitiesPermissionsOfReadableEntitiesOnlyForUserReader() {
        initAclEntity(anotherS3bucket, AclPermission.READ);
        initAclEntity(anotherS3bucket2);
        final List<EntityPermission> permissions = mutableListOf(
                getEntityPermission(anotherS3bucket), getEntityPermission(anotherS3bucket2));
        doReturn(permissions).when(spyPermissionManager).loadUserEntitiesPermissions(ID, DATA_STORAGE);
        mockAuthUser(SIMPLE_USER);

        assertThat(aclPermissionApiService.loadUserEntitiesPermissions(ID, DATA_STORAGE))
                .extracting(EntityPermission::getEntity)
                .containsExactly(anotherS3bucket);
    }

    @Test
    @WithMockUser(SIMPLE_USER)
    public void shouldDenyLoadUserEntitiesPermissionsWithoutReadPermissionOnUser() {
        initAclEntity(targetUser);
        initAclEntity(anotherS3bucket, AclPermission.READ);
        doReturn(mutableListOf(getEntityPermission(anotherS3bucket)))
                .when(spyPermissionManager).loadUserEntitiesPermissions(ID, DATA_STORAGE);
        mockAuthUser(SIMPLE_USER);

        assertThrows(AccessDeniedException.class,
            () -> aclPermissionApiService.loadUserEntitiesPermissions(ID, DATA_STORAGE));
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, roles = STORAGE_READER_ROLE)
    public void shouldLoadUserEntitiesPermissionsOfAllStoragesForStorageReader() {
        initAclEntity(targetUser, AclPermission.READ);
        initAclEntity(anotherS3bucket);
        initAclEntity(anotherS3bucket2);
        final List<EntityPermission> permissions = mutableListOf(
                getEntityPermission(anotherS3bucket), getEntityPermission(anotherS3bucket2));
        doReturn(permissions).when(spyPermissionManager).loadUserEntitiesPermissions(ID, DATA_STORAGE);
        mockAuthUser(SIMPLE_USER);

        assertThat(aclPermissionApiService.loadUserEntitiesPermissions(ID, DATA_STORAGE))
                .extracting(EntityPermission::getEntity)
                .containsExactly(anotherS3bucket, anotherS3bucket2);
    }

    private EntityPermission getEntityPermission(final AbstractSecuredEntity entity) {
        final EntityPermission entityPermission = new EntityPermission();
        entityPermission.setEntity(entity);
        return entityPermission;
    }
}
