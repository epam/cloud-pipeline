/*
 * Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.acl.user;

import com.epam.pipeline.controller.vo.user.RoleVO;
import com.epam.pipeline.entity.user.ExtendedRole;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.manager.user.RoleManager;
import com.epam.pipeline.security.acl.AclPermission;
import com.epam.pipeline.test.acl.AbstractAclTest;
import com.epam.pipeline.test.creator.user.UserCreatorUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.List;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID_2;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.READ_PERMISSION;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_LONG_LIST;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

public class RoleApiServiceTest extends AbstractAclTest {

    private final RoleVO roleVO = UserCreatorUtils.getRoleVO();
    private final ExtendedRole extendedRole = getExtendedRole();
    private final Role role = UserCreatorUtils.getRole("role", ID, ANOTHER_SIMPLE_USER);
    private final Role anotherRole = UserCreatorUtils.getRole("anotherRole", ID_2, ANOTHER_SIMPLE_USER);
    private final List<Role> roleList = mutableListOf(role, anotherRole);

    @Autowired
    private RoleApiService roleApiService;

    @Autowired
    private RoleManager mockRoleManager;

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadRolesWithUsersForAdmin() {
        doReturn(roleList).when(mockRoleManager).loadAllRoles(true);

        assertThat(roleApiService.loadRolesWithUsers()).isEqualTo(roleList);
    }

    @Test
    @WithMockUser(roles = USER_READER_ROLE)
    public void shouldLoadRolesWithUsersForUserReader() {
        doReturn(roleList).when(mockRoleManager).loadAllRoles(true);

        assertThat(roleApiService.loadRolesWithUsers()).isEqualTo(roleList);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldLoadRolesWhichPermissionIsGranted() {
        initAclEntity(role, AclPermission.READ);
        initAclEntity(anotherRole);
        doReturn(roleList).when(mockRoleManager).loadAllRoles(false);

        final List<Role> roles = roleApiService.loadRoles();
        assertThat(roles).hasSize(1).contains(role);
        assertThat(roles.get(0).getMask()).isEqualTo(READ_PERMISSION);
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadRoleForAdmin() {
        doReturn(extendedRole).when(mockRoleManager).loadRoleWithUsers(ID);

        assertThat(roleApiService.loadRole(ID)).isEqualTo(extendedRole);
    }

    @Test
    @WithMockUser(roles = USER_READER_ROLE)
    public void shouldLoadRoleForUserReader() {
        doReturn(extendedRole).when(mockRoleManager).loadRoleWithUsers(ID);

        assertThat(roleApiService.loadRole(ID)).isEqualTo(extendedRole);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldLoadRoleWhenPermissionIsGranted() {
        initAclEntity(extendedRole, AclPermission.READ);
        doReturn(extendedRole).when(mockRoleManager).loadRoleWithUsers(ID);

        final Role role = roleApiService.loadRole(ID);
        assertThat(role.getMask()).isEqualTo(READ_PERMISSION);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyLoadRoleWhenPermissionIsNotGranted() {
        initAclEntity(extendedRole);
        doReturn(extendedRole).when(mockRoleManager).loadRoleWithUsers(ID);

        assertThrows(AccessDeniedException.class, () -> roleApiService.loadRole(ID));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldCreateRoleForAdmin() {
        doReturn(role).when(mockRoleManager).create(TEST_STRING, false, true, ID);

        assertThat(roleApiService.createRole(TEST_STRING, true, ID)).isEqualTo(role);
    }

    @Test
    @WithMockUser
    public void shouldDenyCreateRoleForNotAdmin() {
        doReturn(role).when(mockRoleManager).create(TEST_STRING, false, true, ID);

        assertThrows(AccessDeniedException.class, () -> roleApiService.createRole(TEST_STRING, true, ID));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldUpdateRoleForAdmin() {
        doReturn(role).when(mockRoleManager).update(ID, roleVO);

        assertThat(roleApiService.updateRole(ID, roleVO)).isEqualTo(role);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldUpdateRoleWhenPermissionIsGranted() {
        initAclEntity(role, AclPermission.WRITE);
        doReturn(role).when(mockRoleManager).update(ID, roleVO);

        assertThat(roleApiService.updateRole(ID, roleVO)).isEqualTo(role);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyUpdateRoleWhenPermissionIsNotGranted() {
        initAclEntity(role, AclPermission.READ);
        doReturn(role).when(mockRoleManager).update(ID, roleVO);

        assertThrows(AccessDeniedException.class, () -> roleApiService.updateRole(ID, roleVO));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldDeleteRoleForAdmin() {
        doReturn(role).when(mockRoleManager).delete(ID);

        assertThat(roleApiService.deleteRole(ID)).isEqualTo(role);
    }

    @Test
    @WithMockUser
    public void shouldDenyDeleteRoleForNotAdmin() {
        doReturn(role).when(mockRoleManager).delete(ID);

        assertThrows(AccessDeniedException.class, () -> roleApiService.deleteRole(ID));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldAssignRoleForAdmin() {
        doReturn(extendedRole).when(mockRoleManager).assignRole(ID, TEST_LONG_LIST);

        assertThat(roleApiService.assignRole(ID, TEST_LONG_LIST)).isEqualTo(extendedRole);
    }

    @Test
    @WithMockUser
    public void shouldDenyAssignRoleForNotAdmin() {
        doReturn(extendedRole).when(mockRoleManager).assignRole(ID, TEST_LONG_LIST);

        assertThrows(AccessDeniedException.class, () -> roleApiService.assignRole(ID, TEST_LONG_LIST));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldRemoveRoleForAdmin() {
        doReturn(extendedRole).when(mockRoleManager).removeRole(ID, TEST_LONG_LIST);

        assertThat(roleApiService.removeRole(ID, TEST_LONG_LIST)).isEqualTo(extendedRole);
    }

    @Test
    @WithMockUser
    public void shouldDenyRemoveRoleForNotAdmin() {
        doReturn(extendedRole).when(mockRoleManager).removeRole(ID, TEST_LONG_LIST);

        assertThrows(AccessDeniedException.class, () -> roleApiService.removeRole(ID, TEST_LONG_LIST));
    }

    private static ExtendedRole getExtendedRole() {
        final ExtendedRole extendedRole = new ExtendedRole();
        extendedRole.setName("role");
        extendedRole.setId(ID);
        extendedRole.setOwner(ANOTHER_SIMPLE_USER);
        return extendedRole;
    }
}
