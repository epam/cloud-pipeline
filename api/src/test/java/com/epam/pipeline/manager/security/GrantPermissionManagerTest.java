/*
 * Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
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
package com.epam.pipeline.manager.security;

import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.security.acl.AclPermissionEntry;
import com.epam.pipeline.entity.security.acl.AclSid;
import com.epam.pipeline.entity.security.acl.EntityPermission;
import com.epam.pipeline.entity.user.DefaultRoles;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.security.acl.AclPermission;
import com.epam.pipeline.test.acl.AbstractAclTest;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.acls.domain.AclImpl;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.*;
import static com.epam.pipeline.test.creator.datastorage.DatastorageCreatorUtils.getS3bucketDataStorage;
import static com.epam.pipeline.test.creator.folder.FolderCreatorUtils.getFolder;
import static com.epam.pipeline.test.creator.pipeline.PipelineCreatorUtils.getPipeline;
import static com.epam.pipeline.test.creator.user.UserCreatorUtils.getPipelineUser;
import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;

public class GrantPermissionManagerTest extends AbstractAclTest {

    private static final String GROUP_1_AUTHORITY = "GROUP_1";
    private static final String GROUP_2_AUTHORITY = "GROUP_2";
    private static final String ROLE_USER = "ROLE_USER";
    private static final String ROLE_STORAGE_READER = "ROLE_STORAGE_READER";
    private static final int SIMPLE_MASK_WRITE = new AclPermission(AclPermission.WRITE.getMask()).getSimpleMask();
    private static final int SIMPLE_MASK_READ = new AclPermission(AclPermission.READ.getMask()).getSimpleMask();

    private final Folder folder = getFolder(ID, null, ANOTHER_SIMPLE_USER);
    private final Folder anotherFolder = getFolder(ID_2, ID, ANOTHER_SIMPLE_USER);
    private final S3bucketDataStorage s3bucket = getS3bucketDataStorage(ID, ANOTHER_SIMPLE_USER);

    @Autowired
    private GrantPermissionManager permissionManager;

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void getPermissionsMaskReturnsCorrectWriteMaskWhenACLConfiguredForExactUser() {

        // mock existence of object and permissions
        initAclEntity(
                anotherFolder,
                Collections.singletonList(new UserPermission(SIMPLE_USER, AclPermission.WRITE.getMask()))
        );

        int permissionsMask = permissionManager.getPermissionsMask(anotherFolder, true, true);
        assertEquals(SIMPLE_MASK_WRITE, permissionsMask);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, authorities = GROUP_1_AUTHORITY)
    public void getPermissionsMaskReturnsCorrectWriteMaskWhenACLConfiguredForGroup() {

        // mock existence of object and permissions
        initAclEntity(
                anotherFolder,
                Collections.singletonList(
                        new AuthorityPermission(AclPermission.WRITE.getMask(), GROUP_1_AUTHORITY))
        );

        int permissionsMask = permissionManager.getPermissionsMask(anotherFolder, true, true);
        assertEquals(SIMPLE_MASK_WRITE, permissionsMask);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, authorities = ROLE_USER)
    public void getPermissionsMaskReturnsCorrectWriteMaskWhenACLConfiguredForRole() {

        // mock existence of object and permissions
        initAclEntity(
                anotherFolder,
                Collections.singletonList(
                        new AuthorityPermission(AclPermission.WRITE.getMask(), DefaultRoles.ROLE_USER.getName()))
        );

        int permissionsMask = permissionManager.getPermissionsMask(anotherFolder, true, true);
        assertEquals(SIMPLE_MASK_WRITE, permissionsMask);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, authorities = {ROLE_USER, GROUP_1_AUTHORITY})
    public void getPermissionsMaskReturnsCorrectZeroMaskWhenACLNotConfiguredAuthoritiesOfThisUser() {

        // mock existence of object and permissions
        initAclEntity(
                anotherFolder,
                Collections.singletonList(
                        new AuthorityPermission(AclPermission.WRITE.getMask(), GROUP_2_AUTHORITY))
        );
        initAclEntity(
                anotherFolder,
                Collections.singletonList(
                        new AuthorityPermission(AclPermission.WRITE.getMask(),
                                DefaultRoles.ROLE_ADVANCED_USER.getName()))
        );
        initAclEntity(
                anotherFolder,
                Collections.singletonList(
                        new UserPermission(ANOTHER_SIMPLE_USER,
                                AclPermission.WRITE.getMask()))
        );

        int permissionsMask = permissionManager.getPermissionsMask(anotherFolder, true, true);
        assertEquals(0L, permissionsMask);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void getPermissionsMaskReturnsCorrectWriteMaskWhenACLConfiguredForUserInParentEntity() {

        // mock existence of object and permissions
        AclImpl parentAcl = initAclEntity(
                folder, Collections.singletonList(new UserPermission(SIMPLE_USER, AclPermission.WRITE.getMask()))
        );
        initAclEntity(
                anotherFolder, Collections.singletonList(new UserPermission(SIMPLE_USER, 0)), parentAcl
        );

        int permissionsMask = permissionManager.getPermissionsMask(anotherFolder, true, true);
        assertEquals(SIMPLE_MASK_WRITE, permissionsMask);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, authorities = GROUP_1_AUTHORITY)
    public void getPermissionsMaskReturnsCorrectReadWriteMaskWhenACLConfiguredForUserAndGroupInParentEntity() {

        // mock existence of object and permissions
        AclImpl parentAcl = initAclEntity(
                folder,
                Collections.singletonList(
                        new AuthorityPermission(AclPermission.WRITE.getMask(), GROUP_1_AUTHORITY))
        );
        initAclEntity(
                anotherFolder,
                Collections.singletonList(new UserPermission(SIMPLE_USER, AclPermission.READ.getMask())), parentAcl
        );


        int permissionsMask = permissionManager.getPermissionsMask(anotherFolder, true, true);
        assertEquals(SIMPLE_MASK_READ | SIMPLE_MASK_WRITE, permissionsMask);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, authorities = ROLE_USER)
    public void getPermissionsMaskReturnsCorrectWriteMaskWhenACLConfiguredForUserAndRoleInParentEntity() {

        // mock existence of object and permissions
        AclImpl parentAcl = initAclEntity(
                folder, Collections.singletonList(
                            new AuthorityPermission(AclPermission.WRITE.getMask(), DefaultRoles.ROLE_USER.getName()))
        );
        initAclEntity(
                anotherFolder,
                Collections.singletonList(new UserPermission(SIMPLE_USER, AclPermission.READ.getMask())), parentAcl
        );

        int permissionsMask = permissionManager.getPermissionsMask(anotherFolder, true, true);
        assertEquals(SIMPLE_MASK_WRITE | SIMPLE_MASK_READ, permissionsMask);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, authorities = {ROLE_USER, GROUP_1_AUTHORITY})
    public void getPermissionsMaskReturnsMaskFromUserWhenBothUserAndGroupHaveItConfigured() {

        // mock existence of object and permissions
        initAclEntity(
                anotherFolder,
                Arrays.asList(
                        new UserPermission(SIMPLE_USER, AclPermission.READ.getMask()),
                        new AuthorityPermission(AclPermission.NO_READ.getMask(), GROUP_1_AUTHORITY)
                )
        );

        int permissionsMask = permissionManager.getPermissionsMask(anotherFolder, true, true);
        assertEquals(SIMPLE_MASK_READ, permissionsMask);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, authorities = {ROLE_USER, GROUP_1_AUTHORITY})
    public void getPermissionsMaskReturnsMaskFromGroupWhenBothGroupAndRoleHaveItConfigured() {

        // mock existence of object and permissions
        initAclEntity(
                anotherFolder,
                Arrays.asList(
                        new AuthorityPermission(AclPermission.NO_READ.getMask(), DefaultRoles.ROLE_USER.getName()),
                        new AuthorityPermission(AclPermission.READ.getMask(), GROUP_1_AUTHORITY))
        );

        int permissionsMask = permissionManager.getPermissionsMask(anotherFolder, true, true);
        assertEquals(SIMPLE_MASK_READ, permissionsMask);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, authorities = {ROLE_USER, GROUP_1_AUTHORITY})
    public void getPermissionsMaskReturnsMaskFromGroupWhenBothUserAndGroupHaveItConfiguredButUserConfiguredForParent() {

        // mock existence of object and permissions
        AclImpl parent = initAclEntity(
                folder,
                Collections.singletonList(new UserPermission(SIMPLE_USER, AclPermission.NO_WRITE.getMask()))
        );
        initAclEntity(
                anotherFolder,
                Collections.singletonList(new AuthorityPermission(AclPermission.WRITE.getMask(), GROUP_1_AUTHORITY)),
                parent
        );

        int permissionsMask = permissionManager.getPermissionsMask(anotherFolder, true, true);
        assertEquals(SIMPLE_MASK_WRITE, permissionsMask);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, authorities = {ROLE_USER, GROUP_1_AUTHORITY})
    public void getPermissionsMaskReturnsMaskForRoleWhenBothGroupAndRoleHaveItConfiguredButGroupConfiguredForParent() {

        // mock existence of object and permissions
        AclImpl parent = initAclEntity(
                folder,
                Collections.singletonList(new AuthorityPermission(AclPermission.NO_WRITE.getMask(), GROUP_1_AUTHORITY))
        );
        initAclEntity(
                anotherFolder,
                Collections.singletonList(
                        new AuthorityPermission(AclPermission.WRITE.getMask(), DefaultRoles.ROLE_USER.getName())),
                parent
        );

        int permissionsMask = permissionManager.getPermissionsMask(anotherFolder, true, true);
        assertEquals(SIMPLE_MASK_WRITE, permissionsMask);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, authorities = ROLE_STORAGE_READER)
    public void getPermissionsMaskReturnsExtendedReadMaskForStorageReaderWhenReadIsDenied() {
        initAclEntity(s3bucket, Arrays.asList(
                new UserPermission(SIMPLE_USER, AclPermission.NO_READ.getMask()),
                new UserPermission(SIMPLE_USER, AclPermission.WRITE.getMask())));
        mockAuthUser(SIMPLE_USER);

        int permissionsMask = permissionManager.getPermissionsMask(s3bucket, false, true);
        assertEquals(AclPermission.READ.getMask() | AclPermission.WRITE.getMask(), permissionsMask);
    }

    @Test
    public void loadUserEntitiesPermissionsShouldKeepOnlyEntriesOfUserItsRolesAndGroups() {
        mockUserWithAuthorities();
        final S3bucketDataStorage storage = getS3bucketDataStorage(ID, ANOTHER_SIMPLE_USER);
        mockStorages(storage);
        initAclEntity(Collections.singletonList(acl(storage,
                new UserPermission(SIMPLE_USER, AclPermission.READ.getMask()),
                new UserPermission(ANOTHER_SIMPLE_USER, AclPermission.WRITE.getMask()),
                new AuthorityPermission(AclPermission.WRITE.getMask(), GROUP_1_AUTHORITY),
                new AuthorityPermission(AclPermission.WRITE.getMask(), GROUP_2_AUTHORITY),
                new AuthorityPermission(AclPermission.EXECUTE.getMask(), ROLE_USER))));

        final List<EntityPermission> result = permissionManager.loadUserEntitiesPermissions(ID,
                AclClass.DATA_STORAGE);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEntity()).isEqualTo(storage);
        assertThat(result.get(0).getPermissions()).containsOnly(
                entry(SIMPLE_USER, true, AclPermission.READ.getMask()),
                entry(GROUP_1_AUTHORITY, false, AclPermission.WRITE.getMask()),
                entry(ROLE_USER, false, AclPermission.EXECUTE.getMask()));
    }

    @Test
    public void loadUserEntitiesPermissionsShouldIncludeEntriesInheritedFromParent() {
        mockUserWithAuthorities();
        final Folder parent = getFolder(ID, null, ANOTHER_SIMPLE_USER);
        final S3bucketDataStorage storage = getS3bucketDataStorage(ID, ANOTHER_SIMPLE_USER);
        storage.setParent(parent);
        mockStorages(storage);
        initAclEntity(Arrays.asList(
                acl(parent, new AuthorityPermission(AclPermission.WRITE.getMask(), GROUP_1_AUTHORITY)),
                acl(storage)));

        final List<EntityPermission> result = permissionManager.loadUserEntitiesPermissions(ID,
                AclClass.DATA_STORAGE);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPermissions()).containsOnly(
                entry(GROUP_1_AUTHORITY, false, AclPermission.WRITE.getMask()));
    }

    @Test
    public void loadUserEntitiesPermissionsShouldOmitEntitiesWithoutEntriesOfUser() {
        mockUserWithAuthorities();
        final S3bucketDataStorage granted = getS3bucketDataStorage(ID, ANOTHER_SIMPLE_USER);
        final S3bucketDataStorage notGranted = getS3bucketDataStorage(ID_2, ANOTHER_SIMPLE_USER);
        final S3bucketDataStorage empty = getS3bucketDataStorage(ID_3, ANOTHER_SIMPLE_USER);
        mockStorages(granted, notGranted, empty);
        initAclEntity(Arrays.asList(
                acl(granted, new UserPermission(SIMPLE_USER, AclPermission.READ.getMask())),
                acl(notGranted, new UserPermission(ANOTHER_SIMPLE_USER, AclPermission.READ.getMask()),
                        new AuthorityPermission(AclPermission.READ.getMask(), GROUP_2_AUTHORITY)),
                acl(empty)));

        final List<EntityPermission> result = permissionManager.loadUserEntitiesPermissions(ID,
                AclClass.DATA_STORAGE);

        assertThat(result).extracting(EntityPermission::getEntity).containsExactly(granted);
    }

    @Test
    public void loadUserEntitiesPermissionsShouldKeepDenyingEntriesOfUser() {
        mockUserWithAuthorities();
        final S3bucketDataStorage storage = getS3bucketDataStorage(ID, ANOTHER_SIMPLE_USER);
        mockStorages(storage);
        initAclEntity(Collections.singletonList(acl(storage,
                new UserPermission(SIMPLE_USER, AclPermission.NO_READ.getMask()))));

        final List<EntityPermission> result = permissionManager.loadUserEntitiesPermissions(ID,
                AclClass.DATA_STORAGE);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPermissions()).containsOnly(
                entry(SIMPLE_USER, true, AclPermission.NO_READ.getMask()));
    }

    @Test
    public void loadUserEntitiesPermissionsShouldLoadPipelines() {
        mockUserWithAuthorities();
        final Pipeline granted = getPipeline(ID, ANOTHER_SIMPLE_USER, null);
        final Pipeline notGranted = getPipeline(ID_2, ANOTHER_SIMPLE_USER, null);
        doReturn(new HashSet<>(Arrays.asList(granted, notGranted))).when(mockEntityManager)
                .loadAllWithParents(AclClass.PIPELINE, null, null);
        initAclEntity(Arrays.asList(
                acl(granted, new AuthorityPermission(AclPermission.EXECUTE.getMask(), GROUP_1_AUTHORITY)),
                acl(notGranted)));

        final List<EntityPermission> result = permissionManager.loadUserEntitiesPermissions(ID, AclClass.PIPELINE);

        assertThat(result).extracting(EntityPermission::getEntity).containsExactly(granted);
        assertThat(result.get(0).getPermissions()).containsOnly(
                entry(GROUP_1_AUTHORITY, false, AclPermission.EXECUTE.getMask()));
    }

    @Test
    public void loadUserEntitiesPermissionsShouldFailForUnsupportedClass() {
        assertThrows(IllegalArgumentException.class,
            () -> permissionManager.loadUserEntitiesPermissions(ID, AclClass.FOLDER));
    }

    private void mockUserWithAuthorities() {
        final PipelineUser user = getPipelineUser(SIMPLE_USER, ID);
        user.setRoles(Collections.singletonList(new Role(ROLE_USER)));
        user.setGroups(Collections.singletonList(GROUP_1_AUTHORITY));
        doReturn(user).when(mockUserManager).load(ID);
    }

    private void mockStorages(final S3bucketDataStorage... storages) {
        doReturn(Arrays.asList(storages)).when(mockEntityManager)
                .loadAllWithParents(AclClass.DATA_STORAGE, null, null);
    }

    private Pair<AbstractSecuredEntity, List<AbstractGrantPermission>> acl(final AbstractSecuredEntity entity,
                                                                            final AbstractGrantPermission... entries) {
        return Pair.of(entity, Arrays.asList(entries));
    }

    private AclPermissionEntry entry(final String sid, final boolean principal, final int mask) {
        return new AclPermissionEntry(new AclSid(sid, principal), mask);
    }
}
