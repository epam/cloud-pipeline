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
import com.epam.pipeline.dto.async.AsyncTaskConsumable;
import com.epam.pipeline.dto.async.AsyncTaskParams;
import com.epam.pipeline.dto.async.AsyncTaskType;
import com.epam.pipeline.dto.async.DownloadArchiveParams;
import com.epam.pipeline.dto.async.UnpackArchiveParams;
import com.epam.pipeline.entity.datastorage.ArchiveTasksConfig;
import com.epam.pipeline.manager.async.AsyncTaskManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.security.CheckPermissionHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;

import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What {@code createAsyncTask} owes every task type, regardless of which one it is: a payload at all,
 * a registered processor, the shared quota, and queueing the processor's <b>return value</b>.
 *
 * <p>Deliberately a plain unit test rather than an ACL one - the security layer is exercised by
 * {@link AsyncTaskApiServiceTest}, and what is asserted here is the contract between the service and
 * a processor, with a stub processor standing in for the real ones.</p>
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

    /**
     * Returns a payload that differs from the one it was given, which is the whole point of
     * {@code prepare} returning a value.
     */
    private final StubProcessor unpackProcessor = new StubProcessor(AsyncTaskType.UNPACK_ARCHIVE);

    private AsyncTaskApiService service;

    @BeforeEach
    public void setUp() {
        service = new AsyncTaskApiService(asyncTaskManager,
                List.<AsyncTaskProcessor>of(unpackProcessor),
                preferenceManager,
                authManager,
                Mockito.mock(CheckPermissionHelper.class),
                messageHelper);
        Mockito.when(authManager.getAuthorizedUser()).thenReturn(OWNER);
        Mockito.when(preferenceManager.getPreference(SystemPreferences.DATA_STORAGE_ARCHIVE_CONFIG))
                .thenReturn(ArchiveTasksConfig.builder().maxUserTasks(TASK_LIMIT).build());
        Mockito.when(asyncTaskManager.create(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(AsyncTask.builder().id(TASK_ID).build());
    }

    @Test
    public void shouldQueueWhatTheProcessorReturnedRatherThanWhatArrived() {
        // The bug this signature exists to prevent. A type may derive a value from the request - here
        // a destination folder that becomes a key prefix - and the derived form is both what the
        // executor must receive and what the permission checks were made against. Queueing the
        // original would persist `out`, and the executor would glue an entry name onto it.
        service.createAsyncTask(unpackParams("out"));

        final ArgumentCaptor<AsyncTaskParams> queued = ArgumentCaptor.forClass(AsyncTaskParams.class);
        Mockito.verify(asyncTaskManager).create(Mockito.eq(AsyncTaskType.UNPACK_ARCHIVE),
                Mockito.eq(OWNER), queued.capture());
        assertEquals("out/", ((UnpackArchiveParams) queued.getValue()).getDestinationPath());
    }

    @Test
    public void shouldRefuseAnAbsentPayload() {
        // `params.getType()` would otherwise raise, so an empty body answered with a 500 rather than
        // with a message.
        assertThrows(() -> service.createAsyncTask(null));

        Mockito.verify(asyncTaskManager, Mockito.never()).create(Mockito.any(), Mockito.any(),
                Mockito.any());
    }

    @Test
    public void shouldRefuseATypeWithNoRegisteredProcessor() {
        // Only the unpack processor is registered here, so the download type has none.
        assertThrows(() -> service.createAsyncTask(DownloadArchiveParams.builder()
                .storageId(STORAGE_ID)
                .build()));

        Mockito.verify(asyncTaskManager, Mockito.never()).create(Mockito.any(), Mockito.any(),
                Mockito.any());
    }

    @Test
    public void shouldEnforceTheQuotaItselfRatherThanLeavingItToTheProcessor() {
        // One budget across every type, and enforced here so that a new processor cannot exempt its
        // own type by forgetting to check it. The stub processor checks nothing at all.
        Mockito.when(asyncTaskManager.countActive(OWNER)).thenReturn((long) TASK_LIMIT);

        assertThrows(() -> service.createAsyncTask(unpackParams("out")));

        Mockito.verify(asyncTaskManager, Mockito.never()).create(Mockito.any(), Mockito.any(),
                Mockito.any());
    }

    @Test
    public void shouldCheckTheQuotaAfterTheProcessorHasRun() {
        // The order the endpoint this replaced had: everything about the payload and the caller's
        // permissions is judged first, and the quota last. So a caller at their limit still hears
        // about a malformed request or a missing permission rather than about the quota.
        Mockito.when(asyncTaskManager.countActive(OWNER)).thenReturn((long) TASK_LIMIT);

        assertThrows(() -> service.createAsyncTask(unpackParams("out")));

        assertEquals(1, unpackProcessor.prepareCalls);
        Mockito.verify(asyncTaskManager, Mockito.never()).create(Mockito.any(), Mockito.any(),
                Mockito.any());
    }

    @Test
    public void shouldAllowARequestUnderTheQuota() {
        Mockito.when(asyncTaskManager.countActive(OWNER)).thenReturn((long) TASK_LIMIT - 1);

        assertEquals(TASK_ID, service.createAsyncTask(unpackParams("out")).getId());
    }

    private UnpackArchiveParams unpackParams(final String destinationPath) {
        return UnpackArchiveParams.builder()
                .sourceStorageId(STORAGE_ID)
                .sourcePath("in/data.zip")
                .destinationStorageId(STORAGE_ID)
                .destinationPath(destinationPath)
                .build();
    }

    /**
     * Stands in for a real processor: derives a trailing slash and counts its calls. It validates
     * nothing, which is what makes the quota assertions above meaningful.
     */
    private static final class StubProcessor implements AsyncTaskProcessor {

        private final AsyncTaskType type;
        private int prepareCalls;

        private StubProcessor(final AsyncTaskType type) {
            this.type = type;
        }

        @Override
        public AsyncTaskType getType() {
            return type;
        }

        @Override
        public AsyncTaskParams prepare(final AsyncTaskParams params) {
            prepareCalls++;
            final UnpackArchiveParams unpackParams = (UnpackArchiveParams) params;
            return unpackParams.toBuilder()
                    .destinationPath(unpackParams.getDestinationPath() + "/")
                    .build();
        }

        @Override
        public AsyncTaskConsumable consume(final AsyncTask task) {
            throw new UnsupportedOperationException();
        }
    }
}
