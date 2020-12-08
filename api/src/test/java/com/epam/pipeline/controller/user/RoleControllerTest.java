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

package com.epam.pipeline.controller.user;

import com.epam.pipeline.acl.user.RoleApiService;
import com.epam.pipeline.controller.vo.user.RoleVO;
import com.epam.pipeline.entity.user.ExtendedRole;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.test.creator.user.UserCreatorUtils;
import com.epam.pipeline.test.web.AbstractControllerTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Collections;
import java.util.List;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_LONG_LIST;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

public class RoleControllerTest extends AbstractControllerTest {

    private static final String ROLE_URL = SERVLET_PATH + "/role";
    private static final String ROLE_ID_URL = ROLE_URL + "/%d";
    private static final String LOAD_ALL_URL = ROLE_URL + "/loadAll";
    private static final String ASSIGN_URL = ROLE_ID_URL + "/assign";
    private static final String REMOVE_URL = ROLE_ID_URL + "/remove";
    private static final String CREATE_URL = ROLE_URL + "/create";

    private static final String LOAD_USERS = "loadUsers";
    private static final String USER_IDS = "userIds";
    private static final String ROLE_NAME = "roleName";
    private static final String USER_DEFAULT = "userDefault";
    private static final String DEFAULT_STORAGE_ID = "defaultStorageId";

    private final Role role = UserCreatorUtils.getRole();
    private final ExtendedRole extendedRole = UserCreatorUtils.getExtendedRole();
    private final RoleVO roleVO = UserCreatorUtils.getRoleVO();
    private final List<Role> roleList = Collections.singletonList(role);

    @Autowired
    private RoleApiService mockRoleApiService;

    @Test
    @WithMockUser
    public void shouldLoadRolesWithUsers() {
        doReturn(roleList).when(mockRoleApiService).loadRolesWithUsers();

        final MvcResult mvcResult = performRequest(get(LOAD_ALL_URL).params(multiValueMapOf(LOAD_USERS, true)));

        verify(mockRoleApiService).loadRolesWithUsers();
        assertResponse(mvcResult, roleList, UserCreatorUtils.COLLECTION_ROLE_INSTANCE_TYPE);
    }

    @Test
    @WithMockUser
    public void shouldLoadRolesWithoutUsers() {
        doReturn(roleList).when(mockRoleApiService).loadRoles();

        final MvcResult mvcResult = performRequest(get(LOAD_ALL_URL).params(multiValueMapOf(LOAD_USERS, false)));

        verify(mockRoleApiService).loadRoles();
        assertResponse(mvcResult, roleList, UserCreatorUtils.COLLECTION_ROLE_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailLoadRoles() {
        performUnauthorizedRequest(get(LOAD_ALL_URL));
    }

    @Test
    @WithMockUser
    public void shouldAssignRole() {
        doReturn(extendedRole).when(mockRoleApiService).assignRole(ID, TEST_LONG_LIST);

        final MvcResult mvcResult = performRequest(post(String.format(ASSIGN_URL, ID))
                .params(multiValueMapOf(USER_IDS, TEST_LONG_LIST)));

        verify(mockRoleApiService).assignRole(ID, TEST_LONG_LIST);
        assertResponse(mvcResult, extendedRole, UserCreatorUtils.EXTENDED_ROLE_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailAssignRole() {
        performUnauthorizedRequest(post(String.format(ASSIGN_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldRemoveRole() {
        doReturn(extendedRole).when(mockRoleApiService).removeRole(ID, TEST_LONG_LIST);

        final MvcResult mvcResult = performRequest(delete(String.format(REMOVE_URL, ID))
                .params(multiValueMapOf(USER_IDS, TEST_LONG_LIST)));

        verify(mockRoleApiService).removeRole(ID, TEST_LONG_LIST);
        assertResponse(mvcResult, extendedRole, UserCreatorUtils.EXTENDED_ROLE_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailRemoveRole() {
        performUnauthorizedRequest(delete(String.format(REMOVE_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldCreateRole() {
        doReturn(role).when(mockRoleApiService).createRole(TEST_STRING, true, ID);

        final MvcResult mvcResult = performRequest(post(CREATE_URL)
                .params(multiValueMapOf(ROLE_NAME, TEST_STRING,
                                        USER_DEFAULT, true,
                                        DEFAULT_STORAGE_ID, ID)));

        verify(mockRoleApiService).createRole(TEST_STRING, true, ID);
        assertResponse(mvcResult, role, UserCreatorUtils.ROLE_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailCreateRole() {
        performUnauthorizedRequest(post(CREATE_URL));
    }

    @Test
    @WithMockUser
    public void shouldUpdateRole() throws Exception {
        final String content = getObjectMapper().writeValueAsString(roleVO);
        doReturn(role).when(mockRoleApiService).updateRole(ID, roleVO);

        final MvcResult mvcResult = performRequest(put(String.format(ROLE_ID_URL, ID)).content(content));

        verify(mockRoleApiService).updateRole(ID, roleVO);
        assertResponse(mvcResult, role, UserCreatorUtils.ROLE_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailUpdateRole() {
        performUnauthorizedRequest(put(String.format(ROLE_ID_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldGetRole() {
        doReturn(role).when(mockRoleApiService).loadRole(ID);

        final MvcResult mvcResult = performRequest(get(String.format(ROLE_ID_URL, ID)));

        verify(mockRoleApiService).loadRole(ID);
        assertResponse(mvcResult, role, UserCreatorUtils.ROLE_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailGetRole() {
        performUnauthorizedRequest(get(String.format(ROLE_ID_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldDeleteRole() {
        doReturn(role).when(mockRoleApiService).deleteRole(ID);

        final MvcResult mvcResult = performRequest(delete(String.format(ROLE_ID_URL, ID)));

        verify(mockRoleApiService).deleteRole(ID);
        assertResponse(mvcResult, role, UserCreatorUtils.ROLE_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailDeleteRole() {
        performUnauthorizedRequest(delete(String.format(ROLE_ID_URL, ID)));
    }
}
