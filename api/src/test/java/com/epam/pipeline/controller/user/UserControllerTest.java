/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.acl.user.UserApiService;
import com.epam.pipeline.controller.vo.PipelineUserExportVO;
import com.epam.pipeline.controller.vo.PipelineUserVO;
import com.epam.pipeline.controller.vo.RouteType;
import com.epam.pipeline.entity.info.UserInfo;
import com.epam.pipeline.entity.security.JwtRawToken;
import com.epam.pipeline.entity.user.CustomControl;
import com.epam.pipeline.entity.user.GroupStatus;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.test.creator.CommonCreatorConstants;
import com.epam.pipeline.test.creator.security.SecurityCreatorUtils;
import com.epam.pipeline.test.creator.user.UserCreatorUtils;
import com.epam.pipeline.test.web.AbstractControllerTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.OBJECT_TYPE;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_ARRAY;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_LONG_LIST;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING_LIST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;


public class UserControllerTest extends AbstractControllerTest {

    private static final String USER_TOKEN_URL = SERVLET_PATH + "/user/token";
    private static final String WHOAMI_URL = SERVLET_PATH + "/whoami";
    private static final String USER_URL = SERVLET_PATH + "/user";
    private static final String USER_FIND_URL = USER_URL + "/find";
    private static final String USER_MEMBER_URL = USER_URL + "/isMember";
    private static final String USER_EXPORT_URL = USER_URL + "/export";
    private static final String USER_ID_URL = USER_URL + "/%d";
    private static final String USER_ID_BLOCK_URL = USER_ID_URL + "/block";
    private static final String USER_ID_UPDATE_URL = USER_ID_URL + "/update";
    private static final String USER_CONTROLS_URL = USER_URL + "/controls";
    private static final String USERS_URL = SERVLET_PATH + "/users";
    private static final String USERS_INFO_URL = USERS_URL + "/info";
    private static final String GROUP_URL = SERVLET_PATH + "/group";
    private static final String GROUP_FIND_URL = GROUP_URL + "/find";
    private static final String GROUP_NAME_URL = GROUP_URL + "/%s";
    private static final String GROUP_NAME_BLOCK_URL = GROUP_NAME_URL + "/block";
    private static final String GROUPS_BLOCK_URL = SERVLET_PATH + "/groups/block";
    private static final String ROUTE_URL = SERVLET_PATH + "/route";
    private static final String LAUNCH_LIMITS_URL = SERVLET_PATH + "/user/launchLimits";

    private static final String EXPIRATION = "expiration";
    private static final String NAME = "name";
    private static final String PREFIX = "prefix";
    private static final String BLOCK_STATUS = "blockStatus";
    private static final String ROLE_IDS = "roleIds";
    private static final String USER_NAME = "userName";
    private static final String GROUP = "group";
    private static final String GROUP_NAME = "groupName";
    private static final String URL = "url";
    private static final String TYPE = "type";
    private final String redirectCookie =
            "\"<html><body><script>window.location.href = \\\"TEST\\\"</script></body></html>\"";
    private final String redirectForm =
            "\"<html>\\n"
            + "<body>\\n"
            + "<form id=\\\"form\\\" method=\\\"post\\\" action=\\\"TEST\\\">\\n"
            + " <input type=\\\"hidden\\\" name=\\\"bearer\\\" value=\\\"TEST\\\" />\\n"
            + "</form>\\n"
            + "<script>\\n" + "document.getElementById('form').submit()\\n"
            + "</script>\\n"
            + "</body>\\n"
            + "</html>\"";
    private static final String TEXT_HTML_UTF8_CONTENT_TYPE = "text/html;charset=UTF-8";
    private static final String FILE_NAME = "users.csv";
    private static final String ROLE_ANONYMOUS_USER = "ANONYMOUS_USER";

    private final JwtRawToken token = SecurityCreatorUtils.getJwtRawToken();
    private final PipelineUser pipelineUser = UserCreatorUtils.getPipelineUser();
    private final PipelineUserVO pipelineUserVO = UserCreatorUtils.getPipelineUserVO();
    private final UserInfo userInfo = UserCreatorUtils.getUserInfo(UserCreatorUtils.getPipelineUser());
    private final CustomControl customControl = UserCreatorUtils.getCustomControl();
    private final PipelineUserExportVO userExportVO = UserCreatorUtils.getPipelineUserExportVO();
    private final GroupStatus groupStatus = UserCreatorUtils.getGroupStatus();

    private final List<PipelineUser> pipelineUserList = Collections.singletonList(pipelineUser);
    private final List<UserInfo> userInfoList = Collections.singletonList(userInfo);
    private final List<CustomControl> customControlList = Collections.singletonList(customControl);
    private final List<GroupStatus> groupStatusList = Collections.singletonList(groupStatus);
    private final Map<String, Integer> launchLimits = Collections.singletonMap(GROUP_NAME, 1);

    @Autowired
    private UserApiService mockUserApiService;

    @Autowired
    private AuthManager mockAuthManager;

    @Test
    @WithMockUser
    public void shouldGenerateToken() {
        doReturn(token).when(mockUserApiService).issueToken(TEST_STRING, ID);

        final MvcResult mvcResult = performRequest(get(USER_TOKEN_URL)
                .params(multiValueMapOf(EXPIRATION, ID,
                                        NAME, TEST_STRING)));

        verify(mockUserApiService).issueToken(TEST_STRING, ID);
        assertResponse(mvcResult, token, SecurityCreatorUtils.JWT_RAW_TOKEN_TYPE);
    }

    @Test
    @WithMockUser
    public void shouldGenerateTokenForCurrentUser() {
        doReturn(token).when(mockAuthManager).issueTokenForCurrentUser(ID);

        final MvcResult mvcResult = performRequest(get(USER_TOKEN_URL)
                .params(multiValueMapOf(EXPIRATION, ID,
                                        null, null)));

        verify(mockAuthManager).issueTokenForCurrentUser(ID);
        assertResponse(mvcResult, token, SecurityCreatorUtils.JWT_RAW_TOKEN_TYPE);
    }

    @Test
    public void shouldFailGenerateTokenForUnauthorizedUser() {
        performUnauthorizedRequest(get(USER_TOKEN_URL));
    }

    @Test
    @WithMockUser
    public void shouldGetCurrentUser() {
        doReturn(pipelineUser).when(mockUserApiService).getCurrentUser();

        final MvcResult mvcResult = performRequest(get(WHOAMI_URL));

        verify(mockUserApiService).getCurrentUser();
        assertResponse(mvcResult, pipelineUser, UserCreatorUtils.PIPELINE_USER_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailGetCurrentUserForUnauthorizedUser() {
        performUnauthorizedRequest(get(WHOAMI_URL));
    }

    @Test
    @WithMockUser
    public void shouldRedirectWithCookieRouteType() throws Exception {
        doReturn(token).when(mockAuthManager).issueTokenForCurrentUser(null);

        final MvcResult mvcResult = performRequest(get(ROUTE_URL)
                .params(multiValueMapOf(URL, TEST_STRING,
                                        TYPE, RouteType.COOKIE)),
                                        TEXT_HTML_UTF8_CONTENT_TYPE);

        verify(mockAuthManager).issueTokenForCurrentUser(null);
        assertThat(mvcResult.getResponse().getContentAsString()).contains(redirectCookie);
    }

    @Test
    @WithMockUser
    public void shouldRedirectWithFormRouteType() throws Exception {
        doReturn(token).when(mockAuthManager).issueTokenForCurrentUser(null);

        final MvcResult mvcResult = performRequest(get(ROUTE_URL)
                .params(multiValueMapOf(URL, TEST_STRING,
                                        TYPE, RouteType.FORM)),
                                        TEXT_HTML_UTF8_CONTENT_TYPE);

        verify(mockAuthManager).issueTokenForCurrentUser(null);
        assertThat(mvcResult.getResponse().getContentAsString()).contains(redirectForm);
    }

    @Test
    @WithMockUser(roles = ROLE_ANONYMOUS_USER)
    public void shouldRedirectForAnonymousUser() throws Exception {
        doReturn(token).when(mockAuthManager).issueTokenForCurrentUser(null);

        final MvcResult mvcResultRedirectForm = performRequest(get(ROUTE_URL)
                        .params(multiValueMapOf(URL, TEST_STRING,
                                                TYPE, RouteType.FORM)),
                                                TEXT_HTML_UTF8_CONTENT_TYPE);
        final MvcResult mvcResultRedirectCookie = performRequest(get(ROUTE_URL)
                        .params(multiValueMapOf(URL, TEST_STRING,
                                                TYPE, RouteType.COOKIE)),
                                                TEXT_HTML_UTF8_CONTENT_TYPE);

        verify(mockAuthManager, times(2)).issueTokenForCurrentUser(null);
        assertThat(mvcResultRedirectForm.getResponse().getContentAsString()).contains(redirectForm);
        assertThat(mvcResultRedirectCookie.getResponse().getContentAsString()).contains(redirectCookie);
    }

    @Test
    @WithMockUser
    public void shouldFindUsers() {
        doReturn(pipelineUserList).when(mockUserApiService).findUsers(TEST_STRING);

        final MvcResult mvcResult = performRequest(get(USER_FIND_URL).params(multiValueMapOf(PREFIX, TEST_STRING)));

        verify(mockUserApiService).findUsers(TEST_STRING);
        assertResponse(mvcResult, pipelineUserList, UserCreatorUtils.PIPELINE_USER_LIST_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailFindUsersForUnauthorizedUser() {
        performUnauthorizedRequest(get(USER_FIND_URL));
    }

    @Test
    @WithMockUser
    public void shouldCreateUser() throws Exception {
        final String content = getObjectMapper().writeValueAsString(pipelineUserVO);
        doReturn(pipelineUser).when(mockUserApiService).createUser(pipelineUserVO);

        final MvcResult mvcResult = performRequest(post(USER_URL).content(content));

        verify(mockUserApiService).createUser(pipelineUserVO);
        assertResponse(mvcResult, pipelineUser, UserCreatorUtils.PIPELINE_USER_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailCreateUserForUnauthorizedUser() {
        performUnauthorizedRequest(post(USER_URL));
    }

    @Test
    @WithMockUser
    public void shouldLoadUser() {
        doReturn(pipelineUser).when(mockUserApiService).loadUser(ID, false);

        final MvcResult mvcResult = performRequest(get(String.format(USER_ID_URL, ID)));

        verify(mockUserApiService).loadUser(ID, false);
        assertResponse(mvcResult, pipelineUser, UserCreatorUtils.PIPELINE_USER_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailLoadUserForUnauthorizedUser() {
        performUnauthorizedRequest(get(String.format(USER_ID_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldLoadUserByName() {
        doReturn(pipelineUser).when(mockUserApiService).loadUserByName(TEST_STRING);

        final MvcResult mvcResult = performRequest(get(USER_URL).params(multiValueMapOf(NAME, TEST_STRING)));

        verify(mockUserApiService).loadUserByName(TEST_STRING);
        assertResponse(mvcResult, pipelineUser, UserCreatorUtils.PIPELINE_USER_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailLoadUserByNameForUnauthorizedUser() {
        performUnauthorizedRequest(get(USER_ID_URL));
    }

    @Test
    @WithMockUser
    public void shouldUpdateUser() throws Exception {
        final String content = getObjectMapper().writeValueAsString(pipelineUserVO);
        doReturn(pipelineUser).when(mockUserApiService).updateUser(ID, pipelineUserVO);

        final MvcResult mvcResult = performRequest(put(String.format(USER_ID_URL, ID)).content(content));

        verify(mockUserApiService).updateUser(ID, pipelineUserVO);
        assertResponse(mvcResult, pipelineUser, UserCreatorUtils.PIPELINE_USER_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailUpdateUserForUnauthorizedUser() {
        performUnauthorizedRequest(put(String.format(USER_ID_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldDeleteUser() {
        final MvcResult mvcResult = performRequest(delete(String.format(USER_ID_URL, ID)));

        verify(mockUserApiService).deleteUser(ID);
        assertResponse(mvcResult, null, OBJECT_TYPE);
    }

    @Test
    public void shouldFailDeleteUserForUnauthorizedUser() {
        performUnauthorizedRequest(delete(String.format(USER_ID_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldLoadUsers() {
        doReturn(pipelineUserList).when(mockUserApiService).loadUsers(false);

        final MvcResult mvcResult = performRequest(get(USERS_URL));

        verify(mockUserApiService).loadUsers(false);
        assertResponse(mvcResult, pipelineUserList, UserCreatorUtils.PIPELINE_USER_LIST_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailLoadUsersForUnauthorizedUser() {
        performUnauthorizedRequest(get(USERS_URL));
    }

    @Test
    @WithMockUser
    public void shouldLoadUsersInfo() {
        doReturn(userInfoList).when(mockUserApiService).loadUsersInfo(Collections.emptyList());

        final MvcResult mvcResult = performRequest(get(USERS_INFO_URL));

        verify(mockUserApiService).loadUsersInfo(Collections.emptyList());
        assertResponse(mvcResult, userInfoList, UserCreatorUtils.USER_INFO_LIST_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailLoadUsersInfoForUnauthorizedUser() {
        performUnauthorizedRequest(get(USERS_URL));
    }

    @Test
    @WithMockUser
    public void shouldGetUserControls() {
        doReturn(customControlList).when(mockUserApiService).getUserControls();

        final MvcResult mvcResult = performRequest(get(USER_CONTROLS_URL));

        verify(mockUserApiService).getUserControls();
        assertResponse(mvcResult, customControlList, UserCreatorUtils.CUSTOM_CONTROL_LIST_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailGetUserControlsForUnauthorizedUser() {
        performUnauthorizedRequest(get(USER_CONTROLS_URL));
    }

    @Test
    @WithMockUser
    public void shouldUpdateUserBlockingStatus() {
        doReturn(pipelineUser).when(mockUserApiService).updateUserBlockingStatus(ID, true);

        final MvcResult mvcResult = performRequest(put(String.format(USER_ID_BLOCK_URL, ID))
                .params(multiValueMapOf(BLOCK_STATUS, true)));

        verify(mockUserApiService).updateUserBlockingStatus(ID, true);
        assertResponse(mvcResult, pipelineUser, UserCreatorUtils.PIPELINE_USER_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailUpdateUserBlockingStatusForUnauthorizedUser() {
        performUnauthorizedRequest(put(String.format(USER_ID_BLOCK_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldUpdateUserRoles() {
        doReturn(pipelineUser).when(mockUserApiService).updateUserRoles(ID, TEST_LONG_LIST);

        final MvcResult mvcResult = performRequest(post(String.format(USER_ID_UPDATE_URL, ID))
                .params(multiValueMapOf(ROLE_IDS, TEST_LONG_LIST)));

        verify(mockUserApiService).updateUserRoles(ID, TEST_LONG_LIST);
        assertResponse(mvcResult, pipelineUser, UserCreatorUtils.PIPELINE_USER_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailUpdateUserRolesForUnauthorizedUser() {
        performUnauthorizedRequest(put(String.format(USER_ID_BLOCK_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldCheckUserByGroup() {
        doReturn(true).when(mockUserApiService).checkUserByGroup(TEST_STRING, TEST_STRING);

        final MvcResult mvcResult = performRequest(get(USER_MEMBER_URL)
                .params(multiValueMapOf(USER_NAME, TEST_STRING,
                                        GROUP, TEST_STRING)));

        verify(mockUserApiService).checkUserByGroup(TEST_STRING, TEST_STRING);
        assertResponse(mvcResult, true, CommonCreatorConstants.BOOLEAN_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailCheckUserByGroupForUnauthorizedUser() {
        performUnauthorizedRequest(get(USER_MEMBER_URL));
    }

    @Test
    @WithMockUser
    public void shouldExportUsers() throws Exception {
        final String content = getObjectMapper().writeValueAsString(userExportVO);
        doReturn(TEST_ARRAY).when(mockUserApiService).exportUsers(userExportVO);

        final MvcResult mvcResult = performRequest(post(USER_EXPORT_URL).content(content),
                EXPECTED_CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE);

        verify(mockUserApiService).exportUsers(userExportVO);
        assertFileResponse(mvcResult, FILE_NAME, TEST_ARRAY);
    }

    @Test
    public void shouldFailExportUsersForUnauthorizedUser() {
        performUnauthorizedRequest(post(USER_EXPORT_URL));
    }

    @Test
    @WithMockUser
    public void shouldLoadUsersByGroup() {
        doReturn(pipelineUserList).when(mockUserApiService).loadUsersByGroup(TEST_STRING);

        final MvcResult mvcResult = performRequest(get(GROUP_URL).params(multiValueMapOf(GROUP, TEST_STRING)));

        verify(mockUserApiService).loadUsersByGroup(TEST_STRING);
        assertResponse(mvcResult, pipelineUserList, UserCreatorUtils.PIPELINE_USER_LIST_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailLoadUsersByGroupForUnauthorizedUser() {
        performUnauthorizedRequest(get(GROUP_URL));
    }

    @Test
    @WithMockUser
    public void shouldFindGroups() {
        doReturn(TEST_STRING_LIST).when(mockUserApiService).findGroups(TEST_STRING);

        final MvcResult mvcResult = performRequest(get(GROUP_FIND_URL).params(multiValueMapOf(PREFIX, TEST_STRING)));

        verify(mockUserApiService).findGroups(TEST_STRING);
        assertResponse(mvcResult, TEST_STRING_LIST, CommonCreatorConstants.STRING_LIST_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailFindGroups() {
        performUnauthorizedRequest(get(GROUP_FIND_URL));
    }

    @Test
    @WithMockUser
    public void shouldUpsertGroupBlockingStatus() {
        doReturn(groupStatus).when(mockUserApiService).upsertGroupBlockingStatus(TEST_STRING, true);

        final MvcResult mvcResult = performRequest(post(String.format(GROUP_NAME_BLOCK_URL, TEST_STRING))
                .params(multiValueMapOf(BLOCK_STATUS, true)));
        performRequest(put(String.format(GROUP_NAME_BLOCK_URL, TEST_STRING))
                .params(multiValueMapOf(BLOCK_STATUS, true)));

        verify(mockUserApiService, times(2)).upsertGroupBlockingStatus(TEST_STRING, true);
        assertResponse(mvcResult, groupStatus, UserCreatorUtils.GROUP_STATUS_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailUpsertGroupBlockingStatusForUnauthorizedUser() {
        performUnauthorizedRequest(put(String.format(GROUP_NAME_BLOCK_URL, TEST_STRING)));
    }

    @Test
    @WithMockUser
    public void shouldDeleteGroupBlockingStatus() {
        doReturn(groupStatus).when(mockUserApiService).deleteGroupBlockingStatus(TEST_STRING);

        final MvcResult mvcResult = performRequest(delete(String.format(GROUP_NAME_BLOCK_URL, TEST_STRING))
                .params(multiValueMapOf(GROUP_NAME, TEST_STRING)));

        verify(mockUserApiService).deleteGroupBlockingStatus(TEST_STRING);
        assertResponse(mvcResult, groupStatus, UserCreatorUtils.GROUP_STATUS_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailDeleteGroupBlockingStatusForUnauthorizedUser() {
        performUnauthorizedRequest(delete(String.format(GROUP_NAME_BLOCK_URL, TEST_STRING)));
    }

    @Test
    @WithMockUser
    public void shouldLoadGroupsBlockingStatuses() {
        doReturn(groupStatusList).when(mockUserApiService).loadAllGroupsBlockingStatuses();

        final MvcResult mvcResult = performRequest(get(GROUPS_BLOCK_URL));

        verify(mockUserApiService).loadAllGroupsBlockingStatuses();
        assertResponse(mvcResult, groupStatusList, UserCreatorUtils.GROUP_STATUS_LIST_INSTANCE_TYPE);
    }

    @Test
    @WithMockUser
    public void shouldLoadUserLaunchLimits() {
        doReturn(launchLimits).when(mockUserApiService).getCurrentUserLaunchLimits(false);
        final MvcResult mvcResult = performRequest(get(LAUNCH_LIMITS_URL));
        verify(mockUserApiService).getCurrentUserLaunchLimits(false);
        assertResponse(mvcResult, launchLimits, UserCreatorUtils.LAUNCH_LIMITS_RESPONSE_TYPE);
    }

    @Test
    public void shouldFailLoadGroupsBlockingStatusesForUnauthorizedUser() {
        performUnauthorizedRequest(get(GROUPS_BLOCK_URL));
    }
}
