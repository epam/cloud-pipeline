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

package com.epam.pipeline.acl.security;

import com.epam.pipeline.controller.vo.EntityPermissionVO;
import com.epam.pipeline.controller.vo.PermissionGrantVO;
import com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.security.acl.AclSecuredEntry;
import com.epam.pipeline.manager.EntityManager;
import com.epam.pipeline.manager.security.GrantPermissionManager;
import com.epam.pipeline.security.acl.AclPermission;
import com.epam.pipeline.test.acl.AbstractAclTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.datastorage.DatastorageCreatorUtils.getS3bucketDataStorage;
import static com.epam.pipeline.test.creator.security.PermissionCreatorUtils.getAclSecuredEntry;
import static com.epam.pipeline.test.creator.security.PermissionCreatorUtils.getEntityPermissionVO;
import static com.epam.pipeline.test.creator.security.PermissionCreatorUtils.getPermissionGrantVO;
import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

public class AclPermissionApiServiceTest extends AbstractAclTest {

    private static final AclClass DATA_STORAGE = AclClass.DATA_STORAGE;
    private final AclSecuredEntry aclSecuredEntry = getAclSecuredEntry();
    private final PermissionGrantVO permissionGrantVO = getPermissionGrantVO();
    private final EntityPermissionVO entityPermissionVO = getEntityPermissionVO();
    private final S3bucketDataStorage s3bucket = getS3bucketDataStorage(ID, SIMPLE_USER);
    private final S3bucketDataStorage anotherS3bucket = getS3bucketDataStorage(ID, ANOTHER_SIMPLE_USER);

    @Autowired
    private GrantPermissionManager spyPermissionManager;

    @Autowired
    private AclPermissionApiService aclPermissionApiService;

    @Autowired
    private EntityManager entityManager;

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldSetPermissionForAdmin() {
        doReturn(aclSecuredEntry).when(spyPermissionManager).setPermissions(permissionGrantVO);

        assertThat(aclPermissionApiService.setPermissions(permissionGrantVO)).isEqualTo(aclSecuredEntry);
    }

    @Test
    @WithMockUser
    public void shouldSetPermissionsForOwner() {
        doReturn(s3bucket).when(entityManager).load(DATA_STORAGE, ID);
        doReturn(aclSecuredEntry).when(spyPermissionManager).setPermissions(permissionGrantVO);
        mockUser(SIMPLE_USER);

        assertThat(aclPermissionApiService.setPermissions(permissionGrantVO)).isEqualTo(aclSecuredEntry);
    }

    @Test
    @WithMockUser
    public void shouldDenySetPermissionForNotOwner() {
        doReturn(s3bucket).when(entityManager).load(DATA_STORAGE, ID);
        mockUser(ANOTHER_SIMPLE_USER);

        assertThrows(AccessDeniedException.class, () -> aclPermissionApiService.setPermissions(permissionGrantVO));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldGetPermissionsForAdmin() {
        doReturn(s3bucket).when(entityManager).load(DATA_STORAGE, ID);
        doReturn(aclSecuredEntry).when(spyPermissionManager).getPermissions(ID, DATA_STORAGE);

        assertThat(aclPermissionApiService.getPermissions(ID, DATA_STORAGE)).isEqualTo(aclSecuredEntry);
    }

    @Test
    @WithMockUser
    public void shouldGetPermissionForOwner() {
        doReturn(s3bucket).when(entityManager).load(AclClass.DATA_STORAGE, ID);
        doReturn(aclSecuredEntry).when(spyPermissionManager).getPermissions(ID, AclClass.DATA_STORAGE);
        mockSecurityContext();
        mockUser(SIMPLE_USER);

        assertThat(aclPermissionApiService.getPermissions(ID, AclClass.DATA_STORAGE)).isEqualTo(aclSecuredEntry);
    }

    @Test
    @WithMockUser
    public void shouldDenyGetPermissionForNotOwner() {
        initAclEntity(s3bucket);
        doReturn(s3bucket).when(entityManager).load(AclClass.DATA_STORAGE, ID);
        doReturn(aclSecuredEntry).when(spyPermissionManager).getPermissions(ID, AclClass.DATA_STORAGE);
        mockSecurityContext();

        assertThrows(AccessDeniedException.class,
            () -> aclPermissionApiService.getPermissions(ID, AclClass.DATA_STORAGE));
    }

    @Test
    @WithMockUser(SIMPLE_USER)
    public void shouldGetPermissionWhenPermissionIsGranted() {
        initAclEntity(anotherS3bucket, AclPermission.READ);
        doReturn(anotherS3bucket).when(entityManager).load(AclClass.DATA_STORAGE, ID);
        doReturn(aclSecuredEntry).when(spyPermissionManager).getPermissions(ID, AclClass.DATA_STORAGE);
        mockSecurityContext();

        assertThat(aclPermissionApiService.getPermissions(ID, AclClass.DATA_STORAGE)).isEqualTo(aclSecuredEntry);
    }

    @Test
    @WithMockUser(SIMPLE_USER)
    public void shouldDenyGetPermissionWhenPermissionIsNotGranted() {
        initAclEntity(anotherS3bucket);
        doReturn(anotherS3bucket).when(entityManager).load(AclClass.DATA_STORAGE, ID);
        doReturn(aclSecuredEntry).when(spyPermissionManager).getPermissions(ID, AclClass.DATA_STORAGE);
        mockSecurityContext();

        assertThrows(AccessDeniedException.class,
            () -> aclPermissionApiService.getPermissions(ID, AclClass.DATA_STORAGE));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldDeletePermissionsForAdmin() {
        doReturn(aclSecuredEntry).when(spyPermissionManager)
                .deletePermissions(ID, AclClass.DATA_STORAGE, SIMPLE_USER, true);

        assertThat(aclPermissionApiService.deletePermissions(ID, AclClass.DATA_STORAGE, SIMPLE_USER, true))
                .isEqualTo(aclSecuredEntry);
    }

    @Test
    @WithMockUser
    public void shouldDeletePermissionsForOwner() {
        doReturn(s3bucket).when(entityManager).load(AclClass.DATA_STORAGE, ID);
        doReturn(aclSecuredEntry).when(spyPermissionManager)
                .deletePermissions(ID, AclClass.DATA_STORAGE, SIMPLE_USER, true);
        mockUser(SIMPLE_USER);

        assertThat(aclPermissionApiService.deletePermissions(ID, AclClass.DATA_STORAGE, SIMPLE_USER, true))
                .isEqualTo(aclSecuredEntry);
    }

    @Test
    @WithMockUser
    public void shouldDenyDeletePermissionsForNotOwner() {
        doReturn(s3bucket).when(entityManager).load(AclClass.DATA_STORAGE, ID);
        mockUser(ANOTHER_SIMPLE_USER);

        assertThrows(AccessDeniedException.class,
            () -> aclPermissionApiService.deletePermissions(ID, AclClass.DATA_STORAGE, SIMPLE_USER, true));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldDeleteAllPermissionsForAdmin() {
        doReturn(aclSecuredEntry).when(spyPermissionManager)
                .deleteAllPermissions(ID, AclClass.DATA_STORAGE);

        assertThat(aclPermissionApiService.deleteAllPermissions(ID, AclClass.DATA_STORAGE))
                .isEqualTo(aclSecuredEntry);
    }

    @Test
    @WithMockUser
    public void shouldDeleteAllPermissionsForOwner() {
        doReturn(s3bucket).when(entityManager).load(AclClass.DATA_STORAGE, ID);
        doReturn(aclSecuredEntry).when(spyPermissionManager)
                .deleteAllPermissions(ID, AclClass.DATA_STORAGE);
        mockUser(SIMPLE_USER);

        assertThat(aclPermissionApiService.deleteAllPermissions(ID, AclClass.DATA_STORAGE))
                .isEqualTo(aclSecuredEntry);
    }

    @Test
    @WithMockUser
    public void shouldDenyDeleteAllPermissionsForNotOwner() {
        doReturn(s3bucket).when(entityManager).load(AclClass.DATA_STORAGE, ID);
        mockUser(ANOTHER_SIMPLE_USER);

        assertThrows(AccessDeniedException.class,
            () -> aclPermissionApiService.deleteAllPermissions(ID, AclClass.DATA_STORAGE));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldChangeOwnerForAdmin() {
        doReturn(aclSecuredEntry).when(spyPermissionManager)
                .changeOwner(ID, AclClass.DATA_STORAGE, SIMPLE_USER);

        assertThat(aclPermissionApiService.changeOwner(ID, AclClass.DATA_STORAGE, SIMPLE_USER))
                .isEqualTo(aclSecuredEntry);
    }

    @Test
    @WithMockUser
    public void shouldChangeOwnerForOwner() {
        doReturn(s3bucket).when(entityManager).load(AclClass.DATA_STORAGE, ID);
        doReturn(aclSecuredEntry).when(spyPermissionManager)
                .changeOwner(ID, AclClass.DATA_STORAGE, SIMPLE_USER);
        mockUser(SIMPLE_USER);

        assertThat(aclPermissionApiService.changeOwner(ID, AclClass.DATA_STORAGE, SIMPLE_USER))
                .isEqualTo(aclSecuredEntry);
    }

    @Test
    @WithMockUser
    public void shouldDenyChangeOwnerForNotOwner() {
        doReturn(s3bucket).when(entityManager).load(AclClass.DATA_STORAGE, ID);
        mockUser(ANOTHER_SIMPLE_USER);

        assertThrows(AccessDeniedException.class,
            () -> aclPermissionApiService.changeOwner(ID, AclClass.DATA_STORAGE, SIMPLE_USER));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadEntityPermissionForAdmin() {
        doReturn(entityPermissionVO).when(spyPermissionManager)
                .loadEntityPermission(AclClass.DATA_STORAGE, ID);

        assertThat(aclPermissionApiService.loadEntityPermission(ID, AclClass.DATA_STORAGE))
                .isEqualTo(entityPermissionVO);
    }

    @Test
    @WithMockUser
    public void shouldLoadEntityPermissionForOwner() {
        doReturn(s3bucket).when(entityManager).load(AclClass.DATA_STORAGE, ID);
        doReturn(entityPermissionVO).when(spyPermissionManager)
                .loadEntityPermission(AclClass.DATA_STORAGE, ID);
        mockUser(SIMPLE_USER);

        assertThat(aclPermissionApiService.loadEntityPermission(ID, AclClass.DATA_STORAGE))
                .isEqualTo(entityPermissionVO);
    }

    @Test
    @WithMockUser
    public void shouldDenyLoadEntityPermissionForNotOwner() {
        doReturn(s3bucket).when(entityManager).load(AclClass.DATA_STORAGE, ID);
        mockUser(ANOTHER_SIMPLE_USER);

        assertThrows(AccessDeniedException.class,
            () -> aclPermissionApiService.loadEntityPermission(ID, AclClass.DATA_STORAGE));
    }
}
