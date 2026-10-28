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

import com.epam.pipeline.controller.PagedResult;
import com.epam.pipeline.controller.vo.async.AsyncTaskFilterVO;
import com.epam.pipeline.controller.vo.async.AsyncTaskStatusVO;
import com.epam.pipeline.dto.async.AsyncTask;
import com.epam.pipeline.dto.async.AsyncTaskConsumeResult;
import com.epam.pipeline.dto.async.AsyncTaskStatus;
import com.epam.pipeline.dto.async.AsyncTaskType;
import com.epam.pipeline.dto.async.DownloadArchiveConsumable;
import com.epam.pipeline.manager.async.AsyncTaskManager;
import com.epam.pipeline.manager.datastorage.DataStorageDownloadArchiveManager;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.test.acl.AbstractAclTest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.Collections;
import java.util.List;

import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Runs through the real security layer, so the {@code @PreAuthorize} annotations are exercised rather
 * than assumed. Two rules are worth that cost: only an administrator claims a task or reports its
 * status, and everything else - listing, reading, cancelling, taking the archive - reaches a task of
 * another user for an administrator and for a storage administrator, and nobody else.
 */
public class AsyncTaskApiServiceTest extends AbstractAclTest {

    private static final String TASK_ID = "4f1a2c90-0000-4000-8000-000000000001";
    private static final String STORAGE_ADMIN_ROLE = "STORAGE_ADMIN";
    private static final String ARCHIVE_URL = "https://bucket.s3.amazonaws.com/archives/selection.zip";
    private static final int TOTAL_COUNT = 50;

    @Autowired
    private AsyncTaskApiService asyncTaskApiService;

    @Autowired
    private AsyncTaskManager mockAsyncTaskManager;

    @Autowired
    private DataStorageDownloadArchiveManager mockDownloadArchiveManager;

    @Autowired
    private AuthManager mockAuthManager;

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLetAdminReportStatus() {
        asyncTaskApiService.updateStatus(TASK_ID, statusReport());

        verify(mockAsyncTaskManager).updateStatus(TASK_ID, AsyncTaskStatus.STOPPING, null, null);
    }

    @Test
    @WithMockUser
    public void shouldRefuseStatusReportFromRegularUser() {
        assertThrows(AccessDeniedException.class,
            () -> asyncTaskApiService.updateStatus(TASK_ID, statusReport()));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLetAdminClaimTask() {
        asyncTaskApiService.claimNext(AsyncTaskType.DOWNLOAD_ARCHIVE);

        verify(mockAsyncTaskManager).claimNext(AsyncTaskType.DOWNLOAD_ARCHIVE);
    }

    @Test
    @WithMockUser
    public void shouldRefuseClaimFromRegularUser() {
        assertThrows(AccessDeniedException.class,
            () -> asyncTaskApiService.claimNext(AsyncTaskType.DOWNLOAD_ARCHIVE));
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldLetOwnerCancelOwnTask() {
        mockUser(SIMPLE_USER);
        mockTaskOwnedBy(SIMPLE_USER);

        asyncTaskApiService.cancel(TASK_ID);

        verify(mockAsyncTaskManager).cancel(TASK_ID);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldNotLetOneUserCancelAnotherUsersTask() {
        mockUser(SIMPLE_USER);
        mockTaskOwnedBy(ANOTHER_SIMPLE_USER);

        assertThrows(IllegalArgumentException.class, () -> asyncTaskApiService.cancel(TASK_ID));
        verify(mockAsyncTaskManager, never()).cancel(TASK_ID);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldNotLetOneUserSeeAnotherUsersTask() {
        // The whole owner boundary rests on these refusals now, so they name the exception: an untyped
        // assertion would also pass if the call were refused by `@PreAuthorize` instead.
        mockUser(SIMPLE_USER);
        mockTaskOwnedBy(ANOTHER_SIMPLE_USER);

        assertThrows(IllegalArgumentException.class, () -> asyncTaskApiService.load(TASK_ID));
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldNotLetOneUserDownloadAnotherUsersArchive() {
        mockUser(SIMPLE_USER);
        mockTaskOwnedBy(ANOTHER_SIMPLE_USER);

        assertThrows(IllegalArgumentException.class, () -> asyncTaskApiService.consume(TASK_ID));
        verify(mockDownloadArchiveManager, never()).consume(any());
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLetAdminSeeAnotherUsersTask() {
        asAdmin();
        mockTaskOwnedBy(ANOTHER_SIMPLE_USER);

        assertEquals(TASK_ID, asyncTaskApiService.load(TASK_ID).getId());
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLetAdminCancelAnyTask() {
        asAdmin();
        mockTaskOwnedBy(ANOTHER_SIMPLE_USER);

        asyncTaskApiService.cancel(TASK_ID);

        verify(mockAsyncTaskManager).cancel(TASK_ID);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldOverwriteOwnerOfRegularUsersFilter() {
        mockUser(SIMPLE_USER);

        asyncTaskApiService.loadByFilter(AsyncTaskFilterVO.builder()
                .owner(ANOTHER_SIMPLE_USER)
                .build());

        assertEquals(SIMPLE_USER, capturedFilter().getOwner());
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLeaveOwnerOfAdminsFilterAlone() {
        asAdmin();

        asyncTaskApiService.loadByFilter(AsyncTaskFilterVO.builder()
                .owner(ANOTHER_SIMPLE_USER)
                .build());

        assertEquals(ANOTHER_SIMPLE_USER, capturedFilter().getOwner());
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLetAdminListEveryOwner() {
        asAdmin();
        // Stubbed so that the assertion can fail: a filter that got restricted would carry this
        // login, while an unstubbed mock would hand the restriction a null and look like no
        // restriction at all.
        mockUser(SIMPLE_USER);

        asyncTaskApiService.loadByFilter(AsyncTaskFilterVO.builder().build());

        assertNull(capturedFilter().getOwner());
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldHandTheCountThroughUntouched() {
        // This layer rewrites the filter on the way in, and must leave the answer alone on the way
        // out. The count stubbed here says nothing about the tasks in the answer - which is what a
        // page past the end of the match looks like - so a count recomputed from the list, rather
        // than carried through, would come out as none.
        mockUser(SIMPLE_USER);
        doReturn(new PagedResult<>(Collections.emptyList(), TOTAL_COUNT))
                .when(mockAsyncTaskManager).loadByFilter(any());

        final PagedResult<List<AsyncTask>> listed =
                asyncTaskApiService.loadByFilter(AsyncTaskFilterVO.builder().build());

        assertEquals(TOTAL_COUNT, listed.getTotalCount());
    }

    // ------------------------------------------------------------------ the storage administrator
    //
    // The role is declared on `@WithMockUser` and read out of the security context, as the service
    // reads it in production. `mockSecurityContext()` is what joins the two: this layer reaches the
    // context through `AuthManager`, which is a mock here, so the mock has to hand the authentication
    // over. `GENERAL_USER_ROLE` is in every one of these annotations because every registered user
    // holds it, and without it `@PreAuthorize` refuses the call before the other role matters.

    @Test
    @WithMockUser(username = SIMPLE_USER, roles = {GENERAL_USER_ROLE, STORAGE_ADMIN_ROLE})
    public void shouldLetStorageAdminListEveryOwner() {
        // These tasks are work against the data storages a storage administrator answers for, so the
        // queue is theirs to see whole: the owner is left as it came, which means every user.
        mockSecurityContext();
        mockUser(SIMPLE_USER);
        doReturn(new PagedResult<>(Collections.emptyList(), TOTAL_COUNT))
                .when(mockAsyncTaskManager).loadByFilter(any());

        asyncTaskApiService.loadByFilter(AsyncTaskFilterVO.builder().build());

        assertNull(capturedFilter().getOwner());
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, roles = {GENERAL_USER_ROLE, STORAGE_ADMIN_ROLE})
    public void shouldLetStorageAdminCancelAnotherUsersTask() {
        mockSecurityContext();
        mockUser(SIMPLE_USER);
        mockTaskOwnedBy(ANOTHER_SIMPLE_USER);

        asyncTaskApiService.cancel(TASK_ID);

        verify(mockAsyncTaskManager).cancel(TASK_ID);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, roles = {GENERAL_USER_ROLE, STORAGE_ADMIN_ROLE})
    public void shouldLetStorageAdminSeeAnotherUsersTask() {
        mockSecurityContext();
        mockUser(SIMPLE_USER);
        mockTaskOwnedBy(ANOTHER_SIMPLE_USER);

        assertEquals(TASK_ID, asyncTaskApiService.load(TASK_ID).getId());
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, roles = {GENERAL_USER_ROLE, STORAGE_ADMIN_ROLE})
    public void shouldLetStorageAdminDownloadAnotherUsersArchive() {
        mockSecurityContext();
        mockUser(SIMPLE_USER);
        mockTaskOwnedBy(ANOTHER_SIMPLE_USER);
        doReturn(DownloadArchiveConsumable.builder().url(ARCHIVE_URL).build())
                .when(mockDownloadArchiveManager).consume(any());

        final AsyncTaskConsumeResult consumed = asyncTaskApiService.consume(TASK_ID);

        assertEquals(AsyncTaskType.DOWNLOAD_ARCHIVE, consumed.getType());
        verify(mockDownloadArchiveManager).consume(any());
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, roles = {GENERAL_USER_ROLE, STORAGE_ADMIN_ROLE})
    public void shouldStillRefuseExecutorEndpointsToStorageAdmin() {
        // `/next` and `/{id}/status` belong to the executor service and stay ADMIN_ONLY, so this role
        // never reaches them - and here the refusal comes from the annotation rather than from the body.
        mockSecurityContext();

        assertThrows(AccessDeniedException.class,
            () -> asyncTaskApiService.claimNext(AsyncTaskType.DOWNLOAD_ARCHIVE));
        assertThrows(AccessDeniedException.class,
            () -> asyncTaskApiService.updateStatus(TASK_ID, statusReport()));
    }

    /**
     * {@code @WithMockUser} fills the security context, which is what {@code @PreAuthorize} reads, but
     * {@code AuthManager} is a mock in this context and the service asks it who the caller is.
     */
    private void asAdmin() {
        doReturn(true).when(mockAuthManager).isAdmin();
    }

    private AsyncTaskStatusVO statusReport() {
        return AsyncTaskStatusVO.builder().status(AsyncTaskStatus.STOPPING).build();
    }

    private void mockTaskOwnedBy(final String owner) {
        doReturn(AsyncTask.builder()
                .id(TASK_ID)
                .type(AsyncTaskType.DOWNLOAD_ARCHIVE)
                .owner(owner)
                .status(AsyncTaskStatus.SUCCESS)
                .build()).when(mockAsyncTaskManager).load(TASK_ID);
    }

    private AsyncTaskFilterVO capturedFilter() {
        final ArgumentCaptor<AsyncTaskFilterVO> captor = ArgumentCaptor.forClass(AsyncTaskFilterVO.class);
        verify(mockAsyncTaskManager).loadByFilter(captor.capture());
        return captor.getValue();
    }
}
