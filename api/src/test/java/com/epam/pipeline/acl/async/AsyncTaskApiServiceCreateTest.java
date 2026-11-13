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

package com.epam.pipeline.acl.async;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dto.async.AsyncTask;
import com.epam.pipeline.dto.async.UnpackArchiveParams;
import com.epam.pipeline.entity.async.AsyncTasksConfig;
import com.epam.pipeline.manager.async.AsyncTaskManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.security.CheckPermissionHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What {@code createAsyncTask} owes every task type, regardless of which one it is: the caller's login
 * as the owner, the shared quota, and the request handed on as it arrived - judging the payload and
 * preparing it belongs to the processor of its type, which {@code AsyncTaskManager} dispatches to.
 *
 * <p>Deliberately a plain unit test rather than an ACL one - the security layer is exercised by
 * {@link AsyncTaskApiServiceTest}, and the dispatch by
 * {@code com.epam.pipeline.manager.async.AsyncTaskManagerTest}.</p>
 */
public class AsyncTaskApiServiceCreateTest {

    private static final String OWNER = "USER";
    private static final String TASK_ID = "4f1a2c90-0000-4000-8000-000000000001";
    private static final int TASK_LIMIT = 3;
    private static final Long STORAGE_ID = 1L;

    private final AsyncTaskManager asyncTaskManager = Mockito.mock(AsyncTaskManager.class);
    private final PreferenceManager preferenceManager = Mockito.mock(PreferenceManager.class);
    private final AuthManager authManager = Mockito.mock(AuthManager.class);
    private final MessageHelper messageHelper = Mockito.mock(MessageHelper.class);

    private AsyncTaskApiService service;

    @BeforeEach
    public void setUp() {
        service = new AsyncTaskApiService(asyncTaskManager,
                preferenceManager,
                authManager,
                Mockito.mock(CheckPermissionHelper.class),
                messageHelper);
        Mockito.when(authManager.getAuthorizedUser()).thenReturn(OWNER);
        Mockito.when(preferenceManager.getPreference(SystemPreferences.SYSTEM_ASYNC_TASKS_CONFIG))
                .thenReturn(AsyncTasksConfig.builder().maxUserTasks(TASK_LIMIT).build());
        Mockito.when(asyncTaskManager.create(Mockito.any(), Mockito.any()))
                .thenReturn(AsyncTask.builder().id(TASK_ID).build());
    }

    @Test
    public void shouldQueueTheRequestUnderTheCallersLogin() {
        // The owner comes from the authenticated caller and from nowhere else: a request body naming
        // another user must not become that user's task.
        final UnpackArchiveParams params = unpackParams();

        service.createAsyncTask(params);

        Mockito.verify(asyncTaskManager).create(OWNER, params);
    }

    @Test
    public void shouldEnforceTheQuotaItselfRatherThanLeavingItToTheProcessor() {
        // One budget across every type, and enforced here so that a new processor cannot exempt its
        // own type by forgetting to check it. It is judged before the payload reaches its processor,
        // so a caller at their limit hears about the quota rather than about the request.
        Mockito.when(asyncTaskManager.countActive(OWNER)).thenReturn((long) TASK_LIMIT);

        assertThrows(() -> service.createAsyncTask(unpackParams()));

        Mockito.verify(asyncTaskManager, Mockito.never()).create(Mockito.any(), Mockito.any());
    }

    @Test
    public void shouldAllowARequestUnderTheQuota() {
        Mockito.when(asyncTaskManager.countActive(OWNER)).thenReturn((long) TASK_LIMIT - 1);

        assertEquals(TASK_ID, service.createAsyncTask(unpackParams()).getId());
    }

    private UnpackArchiveParams unpackParams() {
        return UnpackArchiveParams.builder()
                .sourceStorageId(STORAGE_ID)
                .sourcePath("in/data.zip")
                .destinationStorageId(STORAGE_ID)
                .destinationPath("out")
                .build();
    }
}
