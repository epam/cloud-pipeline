/*
 * Copyright 2026 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.execution;

import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.preference.Preference;
import com.epam.pipeline.entity.security.JwtRawToken;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.security.UserContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("PMD.UnusedPrivateField")
public class PipelineLauncherApiTokenTest {

    private static final Long RUN_ID = 1L;
    private static final Long OWNER_ID = 2L;
    private static final Long ADMIN_ID = 3L;
    private static final String OWNER = "OWNER";
    private static final String OWNER_LOWER_CASE = "owner";
    private static final String ADMIN = "ADMIN";
    private static final String OWNER_TOKEN = "owner-token";
    private static final String CALLER_TOKEN = "caller-token";
    private static final String API_HOST = "https://api/";

    @Mock
    private AuthManager authManager;
    @Mock
    private UserManager userManager;
    @Mock
    private PreferenceManager preferenceManager;

    @InjectMocks
    private PipelineLauncher pipelineLauncher;

    private final PipelineUser ownerUser = PipelineUser.builder().id(OWNER_ID).userName(OWNER).build();
    private final UserContext ownerContext = new UserContext(ownerUser);

    @BeforeEach
    public void setUp() {
        doReturn(new Preference()).when(preferenceManager).getSystemPreference(any());
        doReturn(ownerUser).when(userManager).loadByNameOrId(OWNER);
        lenient().doReturn(new JwtRawToken(OWNER_TOKEN)).when(authManager)
                .issueToken(eq(ownerContext), isNull(Long.class));
    }

    @Test
    public void shouldIssueApiTokenForRunOwnerWhenCallerIsAnotherUser() {
        doReturn(new UserContext(ADMIN_ID, ADMIN)).when(authManager).getUserContext();

        final Map<SystemParams, String> params = pipelineLauncher.matchCommonParams(getRun(), API_HOST, null);

        assertEquals(OWNER_TOKEN, params.get(SystemParams.API_TOKEN));
        verify(authManager, never()).issueToken(eq(new UserContext(ADMIN_ID, ADMIN)), any());
    }

    @Test
    public void shouldIssueApiTokenForRunOwnerWhenThereIsNoCaller() {
        final Map<SystemParams, String> params = pipelineLauncher.matchCommonParams(getRun(), API_HOST, null);

        assertEquals(OWNER_TOKEN, params.get(SystemParams.API_TOKEN));
    }

    @Test
    public void shouldIssueApiTokenForCallerContextWhenCallerIsRunOwner() {
        final UserContext callerContext = new UserContext(OWNER_ID, OWNER_LOWER_CASE);
        callerContext.setExternal(true);
        doReturn(callerContext).when(authManager).getUserContext();
        doReturn(new JwtRawToken(CALLER_TOKEN)).when(authManager).issueToken(eq(callerContext), isNull(Long.class));

        final Map<SystemParams, String> params = pipelineLauncher.matchCommonParams(getRun(), API_HOST, null);

        assertEquals(CALLER_TOKEN, params.get(SystemParams.API_TOKEN));
    }

    private PipelineRun getRun() {
        final PipelineRun run = new PipelineRun();
        run.setId(RUN_ID);
        run.setOwner(OWNER);
        run.setStartDate(new Date());
        return run;
    }
}
