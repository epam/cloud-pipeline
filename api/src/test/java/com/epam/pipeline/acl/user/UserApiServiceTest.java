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

package com.epam.pipeline.acl.user;

import com.epam.pipeline.controller.vo.PipelineUserExportVO;
import com.epam.pipeline.controller.vo.PipelineUserVO;
import com.epam.pipeline.entity.info.UserInfo;
import com.epam.pipeline.entity.security.JwtRawToken;
import com.epam.pipeline.entity.user.CustomControl;
import com.epam.pipeline.entity.user.GroupStatus;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.PipelineUserEvent;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.manager.user.UsersFileImportManager;
import com.epam.pipeline.test.acl.AbstractAclTest;
import com.epam.pipeline.test.creator.pipeline.PipelineCreatorUtils;
import com.epam.pipeline.test.creator.security.SecurityCreatorUtils;
import com.epam.pipeline.test.creator.user.UserCreatorUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.Collections;
import java.util.List;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_ARRAY;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_LONG_LIST;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING_LIST;
import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

public class UserApiServiceTest extends AbstractAclTest {

    private final PipelineUser pipelineUser = UserCreatorUtils.getPipelineUser();
    private final PipelineUserVO pipelineUserVO = PipelineCreatorUtils.getPipelineUserVO();
    private final GroupStatus groupStatus = UserCreatorUtils.getGroupStatus();
    private final UserInfo userInfo = UserCreatorUtils.getUserInfo(pipelineUser);
    private final CustomControl customControl = UserCreatorUtils.getCustomControl();
    private final PipelineUserExportVO userExportVO = UserCreatorUtils.getPipelineUserExportVO();
    private final JwtRawToken token = SecurityCreatorUtils.getJwtRawToken();

    private final List<GroupStatus> groupStatusList = Collections.singletonList(groupStatus);
    private final List<PipelineUser> pipelineUserList = Collections.singletonList(pipelineUser);
    private final List<UserInfo> userInfoList = Collections.singletonList(userInfo);
    private final List<CustomControl> customControlList = Collections.singletonList(customControl);

    @Autowired
    private UserApiService userApiService;

    @Autowired
    private UserManager mockUserManager;

    @Autowired
    private UsersFileImportManager mockUsersFileImportManager;

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldCreateUserForAdmin() {
        doReturn(pipelineUser).when(mockUserManager).createUser(pipelineUserVO);

        assertThat(userApiService.createUser(pipelineUserVO)).isEqualTo(pipelineUser);
    }

    @Test
    @WithMockUser
    public void shouldDenyCreateUserForNotAdmin() {
        doReturn(pipelineUser).when(mockUserManager).createUser(pipelineUserVO);

        assertThrows(AccessDeniedException.class, () -> userApiService.createUser(pipelineUserVO));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldUpdateUserForAdmin() {
        doReturn(pipelineUser).when(mockUserManager).updateUser(ID, pipelineUserVO);

        assertThat(userApiService.updateUser(ID, pipelineUserVO)).isEqualTo(pipelineUser);
    }

    @Test
    @WithMockUser
    public void shouldDenyUpdateUserForNotAdmin() {
        doReturn(pipelineUser).when(mockUserManager).updateUser(ID, pipelineUserVO);

        assertThrows(AccessDeniedException.class, () -> userApiService.updateUser(ID, pipelineUserVO));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldUpdateUserBlockingStatusForAdmin() {
        doReturn(pipelineUser).when(mockUserManager).updateUserBlockingStatus(ID, true);

        assertThat(userApiService.updateUserBlockingStatus(ID, true)).isEqualTo(pipelineUser);
    }

    @Test
    @WithMockUser
    public void shouldDenyUpdateUserBlockingStatusForNotAdmin() {
        doReturn(pipelineUser).when(mockUserManager).updateUserBlockingStatus(ID, true);

        assertThrows(AccessDeniedException.class, () -> userApiService.updateUserBlockingStatus(ID, true));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldUpsertGroupBlockingStatusForAdmin() {
        doReturn(groupStatus).when(mockUserManager).upsertGroupBlockingStatus(TEST_STRING, true);

        assertThat(userApiService.upsertGroupBlockingStatus(TEST_STRING, true)).isEqualTo(groupStatus);
    }

    @Test
    @WithMockUser
    public void shouldDenyUpsertGroupBlockingStatusForNotAdmin() {
        doReturn(groupStatus).when(mockUserManager).upsertGroupBlockingStatus(TEST_STRING, true);

        assertThrows(AccessDeniedException.class, () -> userApiService.upsertGroupBlockingStatus(TEST_STRING, true));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldDeleteGroupBlockingStatusForAdmin() {
        doReturn(groupStatus).when(mockUserManager).deleteGroupBlockingStatus(TEST_STRING);

        assertThat(userApiService.deleteGroupBlockingStatus(TEST_STRING)).isEqualTo(groupStatus);
    }

    @Test
    @WithMockUser
    public void shouldDenyDeleteGroupBlockingStatusForNotAdmin() {
        doReturn(groupStatus).when(mockUserManager).deleteGroupBlockingStatus(TEST_STRING);

        assertThrows(AccessDeniedException.class, () -> userApiService.deleteGroupBlockingStatus(TEST_STRING));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadAllGroupsBlockingStatusesForAdmin() {
        doReturn(groupStatusList).when(mockUserManager).loadAllGroupsBlockingStatuses();

        assertThat(userApiService.loadAllGroupsBlockingStatuses()).isEqualTo(groupStatusList);
    }

    @Test
    @WithMockUser(roles = USER_READER_ROLE)
    public void shouldLoadAllGroupsBlockingStatusesForUserReader() {
        doReturn(groupStatusList).when(mockUserManager).loadAllGroupsBlockingStatuses();

        assertThat(userApiService.loadAllGroupsBlockingStatuses()).isEqualTo(groupStatusList);
    }

    @Test
    @WithMockUser
    public void shouldDenyLoadAllGroupsBlockingStatusesForNotUserReader() {
        doReturn(groupStatusList).when(mockUserManager).loadAllGroupsBlockingStatuses();

        assertThrows(AccessDeniedException.class, () -> userApiService.loadAllGroupsBlockingStatuses());
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadUserForAdmin() {
        doReturn(pipelineUser).when(mockUserManager).loadUserById(ID);

        assertThat(userApiService.loadUser(ID)).isEqualTo(pipelineUser);
    }

    @Test
    @WithMockUser(roles = USER_READER_ROLE)
    public void shouldLoadUserForUserReader() {
        doReturn(pipelineUser).when(mockUserManager).loadUserById(ID);

        assertThat(userApiService.loadUser(ID)).isEqualTo(pipelineUser);
    }

    @Test
    @WithMockUser
    public void shouldDenyLoadUserForNotUserReader() {
        doReturn(pipelineUser).when(mockUserManager).loadUserById(ID);

        assertThrows(AccessDeniedException.class, () -> userApiService.loadUser(ID));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadUserByNameForAdmin() {
        doReturn(pipelineUser).when(mockUserManager).loadUserByName(TEST_STRING);

        assertThat(userApiService.loadUserByName(TEST_STRING)).isEqualTo(pipelineUser);
    }

    @Test
    @WithMockUser(roles = USER_READER_ROLE)
    public void shouldLoadUserByNameForUserReader() {
        doReturn(pipelineUser).when(mockUserManager).loadUserByName(TEST_STRING);

        assertThat(userApiService.loadUserByName(TEST_STRING)).isEqualTo(pipelineUser);
    }

    @Test
    @WithMockUser
    public void shouldDenyLoadUserByNameForNotUserReader() {
        doReturn(pipelineUser).when(mockUserManager).loadUserByName(TEST_STRING);

        assertThrows(AccessDeniedException.class, () -> userApiService.loadUserByName(TEST_STRING));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldDeleteUserForAdmin() {
        doReturn(pipelineUser).when(mockUserManager).deleteUser(ID);

        userApiService.deleteUser(ID);

        verify(mockUserManager).deleteUser(ID);
    }

    @Test
    @WithMockUser
    public void shouldDenyDeleteUserForNotAdmin() {
        doReturn(pipelineUser).when(mockUserManager).deleteUser(ID);

        assertThrows(AccessDeniedException.class, () -> userApiService.deleteUser(ID));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldUpdateUserRolesForAdmin() {
        doReturn(pipelineUser).when(mockUserManager).updateUser(ID, TEST_LONG_LIST);

        assertThat(userApiService.updateUserRoles(ID, TEST_LONG_LIST)).isEqualTo(pipelineUser);
    }

    @Test
    @WithMockUser
    public void shouldDenyUpdateUserRolesForAdmin() {
        doReturn(pipelineUser).when(mockUserManager).updateUser(ID, TEST_LONG_LIST);

        assertThrows(AccessDeniedException.class, () -> userApiService.updateUserRoles(ID, TEST_LONG_LIST));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadUsersForAdmin() {
        doReturn(pipelineUserList).when(mockUserManager).loadAllUsers();

        assertThat(userApiService.loadUsers()).isEqualTo(pipelineUserList);
    }

    @Test
    @WithMockUser(roles = USER_READER_ROLE)
    public void shouldLoadUsersForUserReader() {
        doReturn(pipelineUserList).when(mockUserManager).loadAllUsers();

        assertThat(userApiService.loadUsers()).isEqualTo(pipelineUserList);
    }

    @Test
    @WithMockUser
    public void shouldDenyLoadUsersForNotUserReader() {
        doReturn(pipelineUserList).when(mockUserManager).loadAllUsers();

        assertThrows(AccessDeniedException.class, () -> userApiService.loadUsers());
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadUsersInfoForAdmin() {
        doReturn(userInfoList).when(mockUserManager).loadUsersInfo();

        assertThat(userApiService.loadUsersInfo()).isEqualTo(userInfoList);
    }

    @Test
    @WithMockUser
    public void shouldLoadUsersInfoForUserRole() {
        doReturn(userInfoList).when(mockUserManager).loadUsersInfo();

        assertThat(userApiService.loadUsersInfo()).isEqualTo(userInfoList);
    }

    @Test
    @WithMockUser(roles = USER_READER_ROLE)
    public void shouldLoadUsersInfoForUserReader() {
        doReturn(userInfoList).when(mockUserManager).loadUsersInfo();

        assertThat(userApiService.loadUsersInfo()).isEqualTo(userInfoList);
    }

    @Test
    @WithMockUser(roles = SIMPLE_USER_ROLE)
    public void shouldDenyLoadUsersInfoForNotUserReader() {
        doReturn(userInfoList).when(mockUserManager).loadUsersInfo();

        assertThrows(AccessDeniedException.class, () -> userApiService.loadUsersInfo());
    }

    @Test
    public void shouldGetCurrentUser() {
        doReturn(pipelineUser).when(mockUserManager).getCurrentUser();

        assertThat(userApiService.getCurrentUser()).isEqualTo(pipelineUser);
    }

    @Test
    public void shouldLoadUsersByGroup() {
        doReturn(pipelineUserList).when(mockUserManager).loadUsersByGroup(TEST_STRING);

        assertThat(userApiService.loadUsersByGroup(TEST_STRING)).isEqualTo(pipelineUserList);
    }

    @Test
    public void shouldCheckUsersByGroup() {
        doReturn(true).when(mockUserManager).checkUserByGroup(TEST_STRING, TEST_STRING);

        assertThat(userApiService.checkUserByGroup(TEST_STRING, TEST_STRING)).isEqualTo(true);
    }

    @Test
    public void shouldFindUserGroups() {
        doReturn(TEST_STRING_LIST).when(mockUserManager).findGroups(TEST_STRING);

        assertThat(userApiService.findGroups(TEST_STRING)).isEqualTo(TEST_STRING_LIST);
    }

    @Test
    public void shouldGetUserControls() {
        doReturn(customControlList).when(mockUserManager).getUserControls();

        assertThat(userApiService.getUserControls()).isEqualTo(customControlList);
    }

    @Test
    public void shouldFindUsers() {
        doReturn(pipelineUserList).when(mockUserManager).findUsers(TEST_STRING);

        assertThat(userApiService.findUsers(TEST_STRING)).isEqualTo(pipelineUserList);
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldExportUsersForAdmin() {
        doReturn(TEST_ARRAY).when(mockUserManager).exportUsers(userExportVO);

        assertThat(userApiService.exportUsers(userExportVO)).isEqualTo(TEST_ARRAY);
    }

    @Test
    @WithMockUser(roles = USER_READER_ROLE)
    public void shouldExportUsersForUserReader() {
        doReturn(TEST_ARRAY).when(mockUserManager).exportUsers(userExportVO);

        assertThat(userApiService.exportUsers(userExportVO)).isEqualTo(TEST_ARRAY);
    }

    @Test
    @WithMockUser
    public void shouldDenyExportUsersForNotUserReader() {
        doReturn(TEST_ARRAY).when(mockUserManager).exportUsers(userExportVO);

        assertThrows(AccessDeniedException.class, () -> userApiService.exportUsers(userExportVO));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldGetTokenForAdmin() {
        doReturn(token).when(mockUserManager).issueToken(TEST_STRING, ID);

        assertThat(userApiService.issueToken(TEST_STRING, ID)).isEqualTo(token);
    }

    @Test
    @WithMockUser
    public void shouldDenyGetTokenForNotAdmin() {
        doReturn(token).when(mockUserManager).issueToken(TEST_STRING, ID);

        assertThrows(AccessDeniedException.class, () -> userApiService.issueToken(TEST_STRING, ID));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldImportUsersForAdmin() {
        final List<PipelineUserEvent> usersEvents = Collections.singletonList(UserCreatorUtils
                .getPipelineUserEvent(SIMPLE_USER));
        doReturn(usersEvents).when(mockUsersFileImportManager)
                .importUsersFromFile(true, true, TEST_STRING_LIST, null);

        assertThat(userApiService.importUsersFromCsv(true, true, TEST_STRING_LIST, null))
                .isEqualTo(usersEvents);
    }

    @Test
    @WithMockUser
    public void shouldDenyImportUsersForNotAdmin() {
        final List<PipelineUserEvent> usersEvents = Collections.singletonList(UserCreatorUtils
                .getPipelineUserEvent(SIMPLE_USER));
        doReturn(usersEvents).when(mockUsersFileImportManager)
                .importUsersFromFile(true, true, TEST_STRING_LIST, null);

        assertThrows(AccessDeniedException.class, () -> userApiService
                .importUsersFromCsv(true, true, TEST_STRING_LIST, null));
    }
}
