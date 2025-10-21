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

import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.entity.user.DefaultRoles;
import com.epam.pipeline.security.acl.AclPermission;
import com.epam.pipeline.test.acl.AbstractAclTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.acls.domain.AclImpl;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.Arrays;
import java.util.Collections;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.*;
import static com.epam.pipeline.test.creator.folder.FolderCreatorUtils.getFolder;
import static org.junit.Assert.assertEquals;

public class GrantPermissionManagerTest extends AbstractAclTest {

    private static final String GROUP_1_AUTHORITY = "GROUP_1";
    private static final String GROUP_2_AUTHORITY = "GROUP_2";
    private static final String ROLE_USER = "ROLE_USER";
    private static final int SIMPLE_MASK_WRITE = new AclPermission(AclPermission.WRITE.getMask()).getSimpleMask();
    private static final int SIMPLE_MASK_READ = new AclPermission(AclPermission.READ.getMask()).getSimpleMask();

    private final Folder folder = getFolder(ID, null, ANOTHER_SIMPLE_USER);
    private final Folder anotherFolder = getFolder(ID_2, ID, ANOTHER_SIMPLE_USER);

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
                                DefaultRoles.ROLE_PIPELINE_MANAGER.getName()))
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
}
