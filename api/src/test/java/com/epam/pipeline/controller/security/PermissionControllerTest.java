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

package com.epam.pipeline.controller.security;

import com.epam.pipeline.controller.vo.EntityPermissionVO;
import com.epam.pipeline.controller.vo.PermissionGrantVO;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.security.acl.AclSecuredEntry;
import com.epam.pipeline.manager.security.AclPermissionApiService;
import com.epam.pipeline.test.web.AbstractControllerTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static com.epam.pipeline.test.creator.security.PermissionCreatorUtils.ACL_SECURED_ENTRY_TYPE;
import static com.epam.pipeline.test.creator.security.PermissionCreatorUtils.ENTITY_WITH_PERMISSION_VO_TYPE;
import static com.epam.pipeline.test.creator.security.PermissionCreatorUtils.getAclSecuredEntry;
import static com.epam.pipeline.test.creator.security.PermissionCreatorUtils.getEntityPermissionVO;
import static com.epam.pipeline.test.creator.security.PermissionCreatorUtils.getPermissionGrantVO;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

public class PermissionControllerTest extends AbstractControllerTest {

    private static final String GRANT_URL = SERVLET_PATH + "/grant";
    private static final String ALL_PERMISSIONS_URL = GRANT_URL + "/all";
    private static final String OWNER_URL = GRANT_URL + "/owner";
    private static final String PERMISSIONS_URL = SERVLET_PATH + "/permissions";
    private static final String ID_AS_STRING = String.valueOf(ID);
    private static final String ID_PARAM = "id";
    private static final String ACL_CLASS_PARAM = "aclClass";
    private static final AclClass DATA_STORAGE = AclClass.DATA_STORAGE;
    private static final String DATA_STORAGE_STRING = AclClass.DATA_STORAGE.name();
    private static final String USER_PARAM = "user";
    private static final String USER_NAME_PARAM = "userName";
    private static final String PRINCIPAL_PARAM = "isPrincipal";
    private static final String TRUE_AS_STRING = "true";
    private final AclSecuredEntry aclSecuredEntry = getAclSecuredEntry();
    private final PermissionGrantVO permissionGrantVO = getPermissionGrantVO();
    private final EntityPermissionVO entityPermissionVO = getEntityPermissionVO();

    @Autowired
    private AclPermissionApiService mockPermissionApiService;

    @Test
    public void shouldFailGrantPermissionForUnauthorizedUser() {
        performUnauthorizedRequest(post(GRANT_URL));
    }

    @Test
    @WithMockUser
    public void shouldGrantPermissions() throws Exception {
        final String content = getObjectMapper().writeValueAsString(permissionGrantVO);
        doReturn(aclSecuredEntry).when(mockPermissionApiService).setPermissions(permissionGrantVO);

        final MvcResult mvcResult = performRequest(post(GRANT_URL).content(content));

        verify(mockPermissionApiService).setPermissions(permissionGrantVO);
        assertResponse(mvcResult, aclSecuredEntry, ACL_SECURED_ENTRY_TYPE);
    }

    @Test
    public void shouldFailDeletePermissionsForUserForUnauthorizedUser() {
        performUnauthorizedRequest(delete(GRANT_URL));
    }

    @Test
    @WithMockUser
    public void shouldDeletePermissionsForUser() {
        doReturn(aclSecuredEntry).when(mockPermissionApiService)
                .deletePermissions(ID, DATA_STORAGE, TEST_STRING, true);

        final MvcResult mvcResult = performRequest(delete(GRANT_URL)
                .params(multiValueMapOf(ID_PARAM, ID_AS_STRING,
                                        ACL_CLASS_PARAM, DATA_STORAGE_STRING,
                                        USER_PARAM, TEST_STRING,
                                        PRINCIPAL_PARAM, TRUE_AS_STRING)));

        verify(mockPermissionApiService).deletePermissions(ID, DATA_STORAGE, TEST_STRING, true);
        assertResponse(mvcResult, aclSecuredEntry, ACL_SECURED_ENTRY_TYPE);
    }

    @Test
    public void shouldFailDeleteAllPermissionsForUnauthorizedUser() {
        performUnauthorizedRequest(delete(ALL_PERMISSIONS_URL));
    }

    @Test
    @WithMockUser
    public void shouldDeleteAllPermissions() {
        doReturn(aclSecuredEntry).when(mockPermissionApiService).deleteAllPermissions(ID, DATA_STORAGE);

        final MvcResult mvcResult = performRequest(delete(ALL_PERMISSIONS_URL)
                .params(multiValueMapOf(ID_PARAM, ID_AS_STRING,
                                        ACL_CLASS_PARAM, DATA_STORAGE_STRING)));

        verify(mockPermissionApiService).deleteAllPermissions(ID, DATA_STORAGE);
        assertResponse(mvcResult, aclSecuredEntry, ACL_SECURED_ENTRY_TYPE);
    }

    @Test
    public void shouldFailGetPipelinePermissionsForUnauthorizedUser() {
        performUnauthorizedRequest(get(GRANT_URL));
    }

    @Test
    @WithMockUser
    public void shouldGetPipelinePermissions() {
        doReturn(aclSecuredEntry).when(mockPermissionApiService).getPermissions(ID, DATA_STORAGE);

        final MvcResult mvcResult = performRequest(get(GRANT_URL)
                .params(multiValueMapOf(ID_PARAM, ID_AS_STRING,
                                        ACL_CLASS_PARAM, DATA_STORAGE_STRING)));

        verify(mockPermissionApiService).getPermissions(ID, DATA_STORAGE);
        assertResponse(mvcResult, aclSecuredEntry, ACL_SECURED_ENTRY_TYPE);
    }

    @Test
    public void shouldFailChangeOwnerForUnauthorizedUser() {
        performUnauthorizedRequest(post(OWNER_URL));
    }

    @Test
    @WithMockUser
    public void shouldChangeOwner() {
        doReturn(aclSecuredEntry).when(mockPermissionApiService).changeOwner(ID, DATA_STORAGE, TEST_STRING);

        final MvcResult mvcResult = performRequest(post(OWNER_URL)
                .params(multiValueMapOf(ID_PARAM, ID_AS_STRING,
                                        ACL_CLASS_PARAM, DATA_STORAGE_STRING,
                                        USER_NAME_PARAM, TEST_STRING)));

        verify(mockPermissionApiService).changeOwner(ID, DATA_STORAGE, TEST_STRING);
        assertResponse(mvcResult, aclSecuredEntry, ACL_SECURED_ENTRY_TYPE);
    }

    @Test
    public void shouldFailLoadEntityPermissionsForUnauthorizedUser() {
        performUnauthorizedRequest(get(PERMISSIONS_URL));
    }

    @Test
    @WithMockUser
    public void shouldLoadEntityPermissions() {
        doReturn(entityPermissionVO).when(mockPermissionApiService).loadEntityPermission(ID, DATA_STORAGE);

        final MvcResult mvcResult = performRequest(get(PERMISSIONS_URL)
                .params(multiValueMapOf(ID_PARAM, ID_AS_STRING,
                                        ACL_CLASS_PARAM, DATA_STORAGE_STRING)));

        verify(mockPermissionApiService).loadEntityPermission(ID, DATA_STORAGE);
        assertResponse(mvcResult, entityPermissionVO, ENTITY_WITH_PERMISSION_VO_TYPE);
    }
}
