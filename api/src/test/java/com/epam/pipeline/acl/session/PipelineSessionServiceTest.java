/*
 * Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.acl.session;

import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.SidImpl;
import com.epam.pipeline.test.acl.AbstractAclTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class PipelineSessionServiceTest extends AbstractAclTest {

    private static final String USERNAME = "TEST_USER";
    private static final String ANOTHER_USERNAME = "ANOTHER_USER";
    private static final String GROUP_NAME = "TEST_GROUP";
    private static final String SESSION_ID = "session-id-1";
    private static final String ANOTHER_SESSION_ID = "session-id-2";

    @Autowired
    private PipelineSessionService pipelineSessionService;

    @Autowired
    private JdbcIndexedSessionRepository mockJdbcIndexedSessionRepository;

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldInvalidateSessionForPrincipal_whenAdmin() {
        final PipelineUser user = userWithName(USERNAME);
        doReturn(user).when(mockUserManager).loadUserByName(eq(USERNAME));
        doReturn(Collections.singletonMap(SESSION_ID, null)).when(mockJdbcIndexedSessionRepository)
                .findByPrincipalName(USERNAME);

        pipelineSessionService.invalidateSession(principalSid(USERNAME));

        verify(mockJdbcIndexedSessionRepository).deleteById(SESSION_ID);
    }

    @Test
    @WithMockUser(roles = USER_ADMIN_ROLE)
    public void shouldInvalidateSessionForPrincipal_whenUserAdmin() {
        final PipelineUser user = userWithName(USERNAME);
        doReturn(user).when(mockUserManager).loadUserByName(eq(USERNAME));
        doReturn(Collections.singletonMap(SESSION_ID, null)).when(mockJdbcIndexedSessionRepository)
                .findByPrincipalName(USERNAME);

        pipelineSessionService.invalidateSession(principalSid(USERNAME));

        verify(mockJdbcIndexedSessionRepository).deleteById(SESSION_ID);
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldInvalidateSessionForGroup_whenAdmin() {
        final PipelineUser user = userWithName(USERNAME);
        doReturn(List.of(user)).when(mockUserManager).loadUsersByGroupOrRole(GROUP_NAME);
        doReturn(Collections.singletonMap(SESSION_ID, null)).when(mockJdbcIndexedSessionRepository)
                .findByPrincipalName(USERNAME);

        pipelineSessionService.invalidateSession(groupSid(GROUP_NAME));

        verify(mockJdbcIndexedSessionRepository).deleteById(SESSION_ID);
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldInvalidateSessionForAllGroupMembers_whenAdmin() {
        doReturn(List.of(userWithName(USERNAME), userWithName(ANOTHER_USERNAME)))
                .when(mockUserManager).loadUsersByGroupOrRole(GROUP_NAME);
        doReturn(Collections.singletonMap(SESSION_ID, null)).when(mockJdbcIndexedSessionRepository)
                .findByPrincipalName(USERNAME);
        doReturn(Collections.singletonMap(ANOTHER_SESSION_ID, null)).when(mockJdbcIndexedSessionRepository)
                .findByPrincipalName(ANOTHER_USERNAME);

        pipelineSessionService.invalidateSession(groupSid(GROUP_NAME));

        verify(mockJdbcIndexedSessionRepository).deleteById(SESSION_ID);
        verify(mockJdbcIndexedSessionRepository).deleteById(ANOTHER_SESSION_ID);
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldFailInvalidatingSession_whenUserNotFound() {
        doReturn(null).when(mockUserManager).loadUserByName(any());

        assertThrows(IllegalArgumentException.class,
                () -> pipelineSessionService.invalidateSession(principalSid(USERNAME)));

        verify(mockJdbcIndexedSessionRepository, never()).deleteById(any());
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldFailInvalidatingSession_whenSidIsNull() {
        assertThrows(IllegalArgumentException.class,
                () -> pipelineSessionService.invalidateSession(null));

        verify(mockJdbcIndexedSessionRepository, never()).deleteById(any());
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldFailInvalidatingSession_whenSidNameIsBlank() {
        assertThrows(IllegalArgumentException.class,
                () -> pipelineSessionService.invalidateSession(principalSid("  ")));

        verify(mockJdbcIndexedSessionRepository, never()).deleteById(any());
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldFailInvalidatingSession_whenNotAuthorized() {
        assertThrows(AccessDeniedException.class,
                () -> pipelineSessionService.invalidateSession(principalSid(USERNAME)));
    }

    private static SidImpl principalSid(final String name) {
        final SidImpl sid = new SidImpl();
        sid.setName(name);
        sid.setPrincipal(true);
        return sid;
    }

    private static SidImpl groupSid(final String name) {
        final SidImpl sid = new SidImpl();
        sid.setName(name);
        sid.setPrincipal(false);
        return sid;
    }

    private static PipelineUser userWithName(final String name) {
        final PipelineUser user = new PipelineUser();
        user.setUserName(name);
        return user;
    }

}
