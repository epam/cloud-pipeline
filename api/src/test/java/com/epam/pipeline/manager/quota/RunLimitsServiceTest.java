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

package com.epam.pipeline.manager.quota;

import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.contextual.ContextualPreference;
import com.epam.pipeline.entity.contextual.ContextualPreferenceExternalResource;
import com.epam.pipeline.entity.contextual.ContextualPreferenceLevel;
import com.epam.pipeline.entity.user.ExtendedRole;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.exception.quota.LaunchQuotaExceededException;
import com.epam.pipeline.manager.contextual.ContextualPreferenceManager;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.preference.AbstractSystemPreference;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.user.RoleManager;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Collections;

public class RunLimitsServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long GROUP_ID = 2L;
    private static final String USER_NAME = "TEST_USER";
    private static final String GROUP_NAME = "TEST_GROUP";

    private final PipelineRunManager runManager = Mockito.mock(PipelineRunManager.class);
    private final RoleManager roleManager = Mockito.mock(RoleManager.class);
    private final ContextualPreferenceManager preferenceManager = Mockito.mock(ContextualPreferenceManager.class);
    private final MessageHelper messageHelper = Mockito.mock(MessageHelper.class);
    private final AuthManager authManager = Mockito.mock(AuthManager.class);

    private final RunLimitsService runLimitsService =
        new RunLimitsService(runManager, roleManager, preferenceManager, messageHelper, authManager);

    @Before
    public void init() {
        doReturn(false).when(authManager).isAdmin();
        doReturn(getUser()).when(authManager).getCurrentUser();
        doReturn(1).when(runManager).countPipelineRuns(Mockito.any());
    }

    @Test
    public void shouldSkipPreferenceChecksIfAdmin() {
        doReturn(true).when(authManager).isAdmin();
        runLimitsService.checkRunLaunchLimits(1);
        verify(authManager, Mockito.never()).getCurrentUser();
        verify(preferenceManager, Mockito.never()).loadAll();
        verify(roleManager, Mockito.never()).loadAllRoles(Mockito.anyBoolean());
    }

    @Test
    public void shouldProcessSuccessfullyIfNoLimitsSpecified() {
        doReturn(Collections.emptyList()).when(preferenceManager).loadAll();
        runLimitsService.checkRunLaunchLimits(1);
    }

    @Test
    public void shouldFailIfUserLimitExceeds() {
        mockLimitPreference(SystemPreferences.LAUNCH_MAX_RUNS_USER_LIMIT, 1, ContextualPreferenceLevel.USER, USER_ID);
        assertThrows(LaunchQuotaExceededException.class, () -> runLimitsService.checkRunLaunchLimits(1));
        verify(roleManager, Mockito.never()).loadAllRoles(Mockito.anyBoolean());
    }

    @Test
    public void shouldFailIfGroupLimitExceeds() {
        mockRoleLoading();
        mockLimitPreference(SystemPreferences.LAUNCH_MAX_RUNS_GROUP_LIMIT, 1, ContextualPreferenceLevel.ROLE, GROUP_ID);
        assertThrows(LaunchQuotaExceededException.class, () -> runLimitsService.checkRunLaunchLimits(1));
        verify(roleManager).loadAllRoles(Mockito.anyBoolean());
    }

    private PipelineUser getUser() {
        final PipelineUser user = new PipelineUser();
        user.setId(USER_ID);
        user.setUserName(USER_NAME);
        user.setGroups(Collections.singletonList(GROUP_NAME));
        return user;
    }

    private void mockLimitPreference(final AbstractSystemPreference.IntPreference pref, final Integer value,
                                     final ContextualPreferenceLevel resourceLevel, final Long resourceId) {
        final ContextualPreferenceExternalResource preferenceResource =
            new ContextualPreferenceExternalResource(resourceLevel, resourceId.toString());
        final ContextualPreference limitPreference =
            new ContextualPreference(pref.getKey(), value.toString(), preferenceResource);
        doReturn(Collections.singletonList(limitPreference)).when(preferenceManager).loadAll();
    }

    private void mockRoleLoading() {
        final ExtendedRole extendedRole = new ExtendedRole();
        extendedRole.setId(GROUP_ID);
        extendedRole.setName(Role.ROLE_PREFIX + GROUP_NAME);
        extendedRole.setUsers(Collections.singletonList(getUser()));
        doReturn(Collections.singletonList(extendedRole)).when(roleManager).loadAllRoles(true);
    }
}
