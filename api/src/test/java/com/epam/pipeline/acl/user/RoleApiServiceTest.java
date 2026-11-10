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
import static com.epam.pipeline.test.creator.CommonCreatorConstants.NO_PERMISSION;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.READ_PERMISSION;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_LONG_LIST;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;

public class RoleApiServiceTest extends AbstractAclTest {

    private static final String GROUP_NAME = "ROLE_GROUP";

    private final RoleVO roleVO = UserCreatorUtils.getRoleVO();
    private final ExtendedRole extendedRole = getExtendedRole();
    private final ExtendedRole adminRole = getAdminRole(1L);
    private final ExtendedRole scopedAdminRole = getScopedAdminRole(10L);
    private final Role role = UserCreatorUtils.getRole("role", ID, ANOTHER_SIMPLE_USER);
    private final Role anotherRole = UserCreatorUtils.getRole("anotherRole", ID_2, ANOTHER_SIMPLE_USER);
    private final List<Role> roleList = mutableListOf(role, anotherRole);
    private final Role group = UserCreatorUtils.getRole(GROUP_NAME, ID, ANOTHER_SIMPLE_USER);
    private final Role predefinedRole = getPredefinedRole(ID_2);

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
    @WithMockUser(roles = USER_ADMIN_ROLE)
    public void shouldLoadRolesWithUsersForUserAdmin() {
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
    public void shouldLoadRolesWithoutUsersAnyWayEvenIfPermissionIsNotGranted() {
        initAclEntity(role, AclPermission.READ);
        initAclEntity(anotherRole);
        doReturn(roleList).when(mockRoleManager).loadAllRoles(false);

        final List<Role> roles = roleApiService.loadRoles();
        assertThat(roles).hasSize(2).contains(role);
        assertThat(roles.get(0).getMask()).isEqualTo(READ_PERMISSION);
        assertThat(roles.get(1).getMask()).isEqualTo(NO_PERMISSION);
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadRoleForAdmin() {
        doReturn(extendedRole).when(mockRoleManager).loadRoleWithUsers(ID);

        assertThat(roleApiService.loadRole(ID)).isEqualTo(extendedRole);
    }

    @Test
    @WithMockUser(roles = USER_ADMIN_ROLE)
    public void shouldLoadRoleForUserAdmin() {
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
        initAclEntity(
                UserCreatorUtils.getRole(extendedRole.getName(), extendedRole.getId(), ANOTHER_SIMPLE_USER),
                AclPermission.READ
        );
        doReturn(extendedRole).when(mockRoleManager).loadRoleWithUsers(ID);

        final Role role = roleApiService.loadRole(ID);
        assertThat(role.getMask()).isEqualTo(READ_PERMISSION);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyLoadRoleWhenPermissionIsNotGranted() {
        initAclEntity(UserCreatorUtils.getRole(extendedRole.getName(), extendedRole.getId(), ANOTHER_SIMPLE_USER));
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
    @WithMockUser(roles = USER_ADMIN_ROLE)
    public void shouldCreateRoleForUserAdmin() {
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
    @WithMockUser(roles = USER_ADMIN_ROLE)
    public void shouldUpdateRoleForUserAdmin() {
        doReturn(role).when(mockRoleManager).update(ID, roleVO);
        doReturn(role).when(mockRoleManager).load(eq(role.getId()));
        initAclEntity(UserCreatorUtils.getRole(role.getName(), role.getId(), ANOTHER_SIMPLE_USER));

        assertThat(roleApiService.updateRole(ID, roleVO)).isEqualTo(role);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldUpdateRoleWhenPermissionIsGranted() {
        final RoleVO groupVO = getRoleVO("group", false, ID_2);
        initAclEntity(group, AclPermission.WRITE);
        doReturn(group).when(mockRoleManager).load(eq(group.getId()));
        doReturn(group).when(mockRoleManager).update(group.getId(), groupVO);

        assertThat(roleApiService.updateRole(ID, groupVO)).isEqualTo(group);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyRenameRoleWhenPermissionIsGranted() {
        final RoleVO groupVO = getRoleVO("renamed", false, null);
        initAclEntity(group, AclPermission.WRITE);
        doReturn(group).when(mockRoleManager).load(eq(group.getId()));
        doReturn(group).when(mockRoleManager).update(group.getId(), groupVO);

        assertThrows(AccessDeniedException.class, () -> roleApiService.updateRole(ID, groupVO));
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyChangeUserDefaultWhenPermissionIsGranted() {
        final RoleVO groupVO = getRoleVO(GROUP_NAME, true, null);
        initAclEntity(group, AclPermission.WRITE);
        doReturn(group).when(mockRoleManager).load(eq(group.getId()));
        doReturn(group).when(mockRoleManager).update(group.getId(), groupVO);

        assertThrows(AccessDeniedException.class, () -> roleApiService.updateRole(ID, groupVO));
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyUpdatePredefinedRoleWhenPermissionIsGranted() {
        final RoleVO predefinedRoleVO = getRoleVO(predefinedRole.getName(), false, ID);
        initAclEntity(predefinedRole, AclPermission.WRITE);
        doReturn(predefinedRole).when(mockRoleManager).load(eq(predefinedRole.getId()));
        doReturn(predefinedRole).when(mockRoleManager).update(predefinedRole.getId(), predefinedRoleVO);

        assertThrows(AccessDeniedException.class,
            () -> roleApiService.updateRole(predefinedRole.getId(), predefinedRoleVO));
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyUpdateRoleWithoutBodyWhenPermissionIsGranted() {
        initAclEntity(group, AclPermission.WRITE);
        doReturn(group).when(mockRoleManager).load(eq(group.getId()));

        assertThrows(AccessDeniedException.class, () -> roleApiService.updateRole(ID, null));
    }

    @Test
    @WithMockUser(roles = USER_ADMIN_ROLE)
    public void shouldDenyUpdateAdminRoleForUserAdmin() {
        final RoleVO adminRoleVO = getRoleVO(adminRole.getName(), false, null);
        doReturn(adminRole).when(mockRoleManager).load(eq(adminRole.getId()));
        doReturn(adminRole).when(mockRoleManager).update(adminRole.getId(), adminRoleVO);
        initAclEntity(UserCreatorUtils.getRole(adminRole.getName(), adminRole.getId(), ANOTHER_SIMPLE_USER));

        assertThrows(AccessDeniedException.class, () -> roleApiService.updateRole(adminRole.getId(), adminRoleVO));
    }

    @Test
    @WithMockUser(roles = USER_ADMIN_ROLE)
    public void shouldRenameRoleForUserAdmin() {
        final RoleVO groupVO = getRoleVO("renamed", true, null);
        doReturn(group).when(mockRoleManager).load(eq(group.getId()));
        doReturn(group).when(mockRoleManager).update(group.getId(), groupVO);

        assertThat(roleApiService.updateRole(ID, groupVO)).isEqualTo(group);
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
    @WithMockUser(roles = USER_ADMIN_ROLE)
    public void shouldDeleteRoleForUserAdmin() {
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
    @WithMockUser(roles = USER_ADMIN_ROLE)
    public void shouldAssignRoleForUserAdmin() {
        doReturn(extendedRole).when(mockRoleManager).load(eq(extendedRole.getId()));
        doReturn(extendedRole).when(mockRoleManager).assignRole(ID, TEST_LONG_LIST);

        assertThat(roleApiService.assignRole(ID, TEST_LONG_LIST)).isEqualTo(extendedRole);
    }

    @Test(expected = AccessDeniedException.class)
    @WithMockUser(roles = USER_ADMIN_ROLE)
    public void shouldNotAssignAdminRoleForUserAdmin() {
        doReturn(adminRole).when(mockRoleManager).load(eq(adminRole.getId()));
        doReturn(adminRole).when(mockRoleManager).assignRole(adminRole.getId(), TEST_LONG_LIST);
        initAclEntity(UserCreatorUtils.getRole(adminRole.getName(), adminRole.getId(), ANOTHER_SIMPLE_USER));

        roleApiService.assignRole(adminRole.getId(), TEST_LONG_LIST);
    }

    @Test(expected = AccessDeniedException.class)
    @WithMockUser(roles = USER_ADMIN_ROLE)
    public void shouldNotAssignScopedAdminRoleForUserAdmin() {
        doReturn(scopedAdminRole).when(mockRoleManager).load(eq(scopedAdminRole.getId()));
        doReturn(scopedAdminRole).when(mockRoleManager).assignRole(scopedAdminRole.getId(), TEST_LONG_LIST);
        initAclEntity(
                UserCreatorUtils.getRole(scopedAdminRole.getName(), scopedAdminRole.getId(), ANOTHER_SIMPLE_USER));

        roleApiService.assignRole(scopedAdminRole.getId(), TEST_LONG_LIST);
    }

    @Test
    @WithMockUser
    public void shouldDenyAssignRoleForNotAdmin() {
        initAclEntity(UserCreatorUtils.getRole(extendedRole.getName(), extendedRole.getId(), ANOTHER_SIMPLE_USER));
        doReturn(extendedRole).when(mockRoleManager).assignRole(ID, TEST_LONG_LIST);

        assertThrows(AccessDeniedException.class, () -> roleApiService.assignRole(ID, TEST_LONG_LIST));
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldAssignRoleWhenPermissionIsGranted() {
        initAclEntity(group, AclPermission.WRITE);
        doReturn(group).when(mockRoleManager).load(eq(group.getId()));
        doReturn(extendedRole).when(mockRoleManager).assignRole(ID, TEST_LONG_LIST);

        assertThat(roleApiService.assignRole(ID, TEST_LONG_LIST)).isEqualTo(extendedRole);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyAssignRoleWhenReadPermissionIsGranted() {
        initAclEntity(group, AclPermission.READ);
        doReturn(group).when(mockRoleManager).load(eq(group.getId()));
        doReturn(extendedRole).when(mockRoleManager).assignRole(ID, TEST_LONG_LIST);

        assertThrows(AccessDeniedException.class, () -> roleApiService.assignRole(ID, TEST_LONG_LIST));
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyAssignPredefinedRoleWhenPermissionIsGranted() {
        initAclEntity(predefinedRole, AclPermission.WRITE);
        doReturn(predefinedRole).when(mockRoleManager).load(eq(predefinedRole.getId()));
        doReturn(extendedRole).when(mockRoleManager).assignRole(predefinedRole.getId(), TEST_LONG_LIST);

        assertThrows(AccessDeniedException.class,
            () -> roleApiService.assignRole(predefinedRole.getId(), TEST_LONG_LIST));
    }

    @Test
    @WithMockUser(roles = USER_ADMIN_ROLE)
    public void shouldAssignPredefinedRoleForUserAdmin() {
        doReturn(predefinedRole).when(mockRoleManager).load(eq(predefinedRole.getId()));
        doReturn(extendedRole).when(mockRoleManager).assignRole(predefinedRole.getId(), TEST_LONG_LIST);

        assertThat(roleApiService.assignRole(predefinedRole.getId(), TEST_LONG_LIST)).isEqualTo(extendedRole);
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldRemoveRoleForAdmin() {
        doReturn(extendedRole).when(mockRoleManager).removeRole(ID, TEST_LONG_LIST);

        assertThat(roleApiService.removeRole(ID, TEST_LONG_LIST)).isEqualTo(extendedRole);
    }

    @Test
    @WithMockUser(roles = USER_ADMIN_ROLE)
    public void shouldRemoveRoleForUserAdmin() {
        doReturn(extendedRole).when(mockRoleManager).removeRole(ID, TEST_LONG_LIST);
        doReturn(extendedRole).when(mockRoleManager).load(eq(extendedRole.getId()));
        initAclEntity(UserCreatorUtils.getRole(extendedRole.getName(), extendedRole.getId(), ANOTHER_SIMPLE_USER));

        assertThat(roleApiService.removeRole(ID, TEST_LONG_LIST)).isEqualTo(extendedRole);
    }

    @Test
    @WithMockUser
    public void shouldDenyRemoveRoleForNotAdmin() {
        initAclEntity(UserCreatorUtils.getRole(extendedRole.getName(), extendedRole.getId(), ANOTHER_SIMPLE_USER));
        doReturn(extendedRole).when(mockRoleManager).removeRole(ID, TEST_LONG_LIST);

        assertThrows(AccessDeniedException.class, () -> roleApiService.removeRole(ID, TEST_LONG_LIST));
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldRemoveRoleWhenPermissionIsGranted() {
        initAclEntity(group, AclPermission.WRITE);
        doReturn(group).when(mockRoleManager).load(eq(group.getId()));
        doReturn(extendedRole).when(mockRoleManager).removeRole(ID, TEST_LONG_LIST);

        assertThat(roleApiService.removeRole(ID, TEST_LONG_LIST)).isEqualTo(extendedRole);
    }

    @Test
    @WithMockUser(roles = USER_ADMIN_ROLE)
    public void shouldDenyRemoveAdminRoleForUserAdmin() {
        doReturn(adminRole).when(mockRoleManager).load(eq(adminRole.getId()));
        doReturn(adminRole).when(mockRoleManager).removeRole(adminRole.getId(), TEST_LONG_LIST);
        initAclEntity(UserCreatorUtils.getRole(adminRole.getName(), adminRole.getId(), ANOTHER_SIMPLE_USER));

        assertThrows(AccessDeniedException.class, () -> roleApiService.removeRole(adminRole.getId(), TEST_LONG_LIST));
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyRemovePredefinedRoleWhenPermissionIsGranted() {
        initAclEntity(predefinedRole, AclPermission.WRITE);
        doReturn(predefinedRole).when(mockRoleManager).load(eq(predefinedRole.getId()));
        doReturn(extendedRole).when(mockRoleManager).removeRole(predefinedRole.getId(), TEST_LONG_LIST);

        assertThrows(AccessDeniedException.class,
            () -> roleApiService.removeRole(predefinedRole.getId(), TEST_LONG_LIST));
    }

    private static RoleVO getRoleVO(final String name, final boolean userDefault, final Long storageId) {
        final RoleVO vo = new RoleVO();
        vo.setName(name);
        vo.setUserDefault(userDefault);
        vo.setDefaultStorageId(storageId);
        return vo;
    }

    private static Role getPredefinedRole(final Long id) {
        final Role predefined = UserCreatorUtils.getRole("ROLE_STORAGE_MANAGER", id, ANOTHER_SIMPLE_USER);
        predefined.setPredefined(true);
        return predefined;
    }

    private static ExtendedRole getExtendedRole() {
        final ExtendedRole extendedRole = new ExtendedRole();
        extendedRole.setName("role");
        extendedRole.setId(ID);
        extendedRole.setOwner(ANOTHER_SIMPLE_USER);
        return extendedRole;
    }

    private static ExtendedRole getAdminRole(long id) {
        final ExtendedRole extendedRole = new ExtendedRole();
        extendedRole.setName("ROLE_ADMIN");
        extendedRole.setId(id);
        extendedRole.setOwner(ANOTHER_SIMPLE_USER);
        return extendedRole;
    }

    private static ExtendedRole getScopedAdminRole(long id) {
        final ExtendedRole extendedRole = new ExtendedRole();
        extendedRole.setName("ROLE_USER_ADMIN");
        extendedRole.setId(id);
        extendedRole.setOwner(ANOTHER_SIMPLE_USER);
        return extendedRole;
    }
}
