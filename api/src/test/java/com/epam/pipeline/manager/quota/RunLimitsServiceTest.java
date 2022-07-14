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
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.user.RoleManager;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.Optional;

public class RunLimitsServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long GROUP_ID = 2L;
    private static final String USER_NAME = "TEST_USER";
    private static final String GROUP_NAME = "TEST_GROUP";
    private static final String LAUNCH_MAX_RUNS_USER_LIMIT = "launch.max.runs.user";
    private static final String LAUNCH_MAX_RUNS_GROUP_LIMIT = "launch.max.runs.group";

    private final PipelineRunManager runManager = Mockito.mock(PipelineRunManager.class);
    private final RoleManager roleManager = Mockito.mock(RoleManager.class);
    private final ContextualPreferenceManager contextualPreferenceManager =
        Mockito.mock(ContextualPreferenceManager.class);
    private final MessageHelper messageHelper = Mockito.mock(MessageHelper.class);
    private final AuthManager authManager = Mockito.mock(AuthManager.class);
    private final PreferenceManager preferenceManager = Mockito.mock(PreferenceManager.class);

    private final RunLimitsService runLimitsService =
        new RunLimitsService(runManager, roleManager, contextualPreferenceManager, messageHelper, authManager,
                             preferenceManager);

    @Before
    public void init() {
        doReturn(false).when(authManager).isAdmin();
        doReturn(getUser()).when(authManager).getCurrentUser();
        doReturn(1).when(runManager).countPipelineRuns(Mockito.any());
        doReturn(Optional.empty())
            .when(contextualPreferenceManager)
            .find(Mockito.anyString(), Mockito.any(ContextualPreferenceExternalResource.class));
        doReturn(Optional.empty())
            .when(contextualPreferenceManager)
            .find(Mockito.anyString(), Mockito.any(ContextualPreferenceExternalResource.class));
        doReturn(Optional.empty()).when(preferenceManager).findPreference(Mockito.any());
    }

    @Test
    public void shouldSkipPreferenceChecksIfAdmin() {
        doReturn(true).when(authManager).isAdmin();
        runLimitsService.checkRunLaunchLimits(1);
        verify(authManager, Mockito.never()).getCurrentUser();
        verify(roleManager, Mockito.never()).loadAllRoles(Mockito.anyBoolean());
    }

    @Test
    public void shouldProcessSuccessfullyIfNoLimitsSpecified() {
        runLimitsService.checkRunLaunchLimits(1);
    }

    @Test
    public void shouldFailIfUserLimitExceeds() {
        mockLimitPreference(LAUNCH_MAX_RUNS_USER_LIMIT, 1, ContextualPreferenceLevel.USER, USER_ID);
        assertThrows(LaunchQuotaExceededException.class, () -> runLimitsService.checkRunLaunchLimits(1));
        verify(roleManager, Mockito.never()).loadAllRoles(Mockito.anyBoolean());
    }

    @Test
    public void shouldFailIfGroupLimitExceeds() {
        mockRoleLoading();
        mockLimitPreferencesByKey(LAUNCH_MAX_RUNS_GROUP_LIMIT,
                                  1, ContextualPreferenceLevel.ROLE, GROUP_ID);
        assertThrows(LaunchQuotaExceededException.class, () -> runLimitsService.checkRunLaunchLimits(1));
        verify(roleManager).loadAllRoles(Mockito.anyBoolean());
    }

    @Test
    public void shouldReturnEmptyLimitsForAdmin() {
        doReturn(true).when(authManager).isAdmin();
        Assertions.assertThat(runLimitsService.getCurrentUserLaunchLimits(true)).hasSize(0);
    }

    @Test
    public void shouldReturnConfiguredLimitsForUser() {
        doReturn(Optional.empty()).when(preferenceManager).findPreference(SystemPreferences.CLUSTER_MAX_SIZE);
        Assertions.assertThat(runLimitsService.getCurrentUserLaunchLimits(true)).hasSize(0);
        mockLimitPreference(LAUNCH_MAX_RUNS_USER_LIMIT, 5, ContextualPreferenceLevel.USER, USER_ID);
        Assertions.assertThat(runLimitsService.getCurrentUserLaunchLimits(true))
            .hasSize(1)
            .containsEntry("<user-contextual-limit>", 5);
        mockRoleLoading();
        mockLimitPreferencesByKey(LAUNCH_MAX_RUNS_GROUP_LIMIT, 2,
                                  ContextualPreferenceLevel.ROLE, GROUP_ID);
        Assertions.assertThat(runLimitsService.getCurrentUserLaunchLimits(true))
            .hasSize(2)
            .containsEntry("<user-contextual-limit>", 5)
            .containsEntry(GROUP_NAME, 2);
        Assertions.assertThat(runLimitsService.getCurrentUserLaunchLimits(false))
            .hasSize(1)
            .containsEntry("<user-contextual-limit>", 5);
    }

    private PipelineUser getUser() {
        final PipelineUser user = new PipelineUser();
        user.setId(USER_ID);
        user.setUserName(USER_NAME);
        user.setGroups(Collections.singletonList(GROUP_NAME));
        return user;
    }

    private void mockLimitPreference(final String pref, final Integer value,
                                     final ContextualPreferenceLevel resourceLevel, final Long resourceId) {
        final ContextualPreferenceExternalResource preferenceResource =
            new ContextualPreferenceExternalResource(resourceLevel, resourceId.toString());
        final ContextualPreference limitPreference =
            new ContextualPreference(pref, value.toString(), preferenceResource);
        doReturn(Optional.of(limitPreference)).when(contextualPreferenceManager)
            .find(pref, preferenceResource);
    }

    private void mockLimitPreferencesByKey(final String pref, final Integer value,
                                           final ContextualPreferenceLevel resourceLevel, final Long resourceId) {
        final ContextualPreferenceExternalResource preferenceResource =
            new ContextualPreferenceExternalResource(resourceLevel, resourceId.toString());
        final ContextualPreference limitPreference =
            new ContextualPreference(pref, value.toString(), preferenceResource);
        doReturn(Collections.singletonList(limitPreference)).when(contextualPreferenceManager).load(pref);
    }

    private void mockRoleLoading() {
        final ExtendedRole extendedRole = new ExtendedRole();
        extendedRole.setId(GROUP_ID);
        extendedRole.setName(Role.ROLE_PREFIX + GROUP_NAME);
        extendedRole.setUsers(Collections.singletonList(getUser()));
        doReturn(Collections.singletonList(extendedRole)).when(roleManager).loadAllRoles(true);
    }
}
