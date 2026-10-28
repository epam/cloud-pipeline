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

package com.epam.pipeline.controller.async;

import com.epam.pipeline.acl.async.AsyncTaskApiService;
import com.epam.pipeline.controller.PagedResult;
import com.epam.pipeline.controller.vo.async.AsyncTaskFilterVO;
import com.epam.pipeline.controller.vo.async.AsyncTaskSortField;
import com.epam.pipeline.controller.vo.async.AsyncTaskStatusVO;
import com.epam.pipeline.dto.async.AsyncTask;
import com.epam.pipeline.dto.async.AsyncTaskConsumeResult;
import com.epam.pipeline.dto.async.AsyncTaskStatus;
import com.epam.pipeline.dto.async.AsyncTaskType;
import com.epam.pipeline.dto.async.DownloadArchiveConsumable;
import com.epam.pipeline.dto.async.DownloadArchiveResult;
import com.epam.pipeline.test.web.AbstractControllerTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Every endpoint of this controller, through the real servlet stack: the route and verb it answers on,
 * the path variable, request parameter or body it binds, what it hands to the ACL service, and the
 * JSON it answers with. A controller signature states none of that, and the DTOs behind these
 * endpoints are shaped by Jackson annotations rather than by their fields.
 */
@WebMvcTest(controllers = AsyncTaskController.class)
public class AsyncTaskControllerTest extends AbstractControllerTest {

    private static final String TASK_ID = "4f1a2c90-0000-4000-8000-000000000001";
    private static final String BASE_URL = SERVLET_PATH + "/async-task";
    private static final String FILTER_URL = BASE_URL + "/filter";
    private static final String NEXT_URL = BASE_URL + "/next";
    private static final String TASK_URL = BASE_URL + "/" + TASK_ID;
    private static final String CONSUME_URL = TASK_URL + "/consume";
    private static final String CANCEL_URL = TASK_URL + "/cancel";
    private static final String STATUS_URL = TASK_URL + "/status";

    private static final String TYPE_PARAM = "type";
    private static final String ARCHIVE_URL = "https://bucket.s3.amazonaws.com/archives/selection.zip";
    private static final String ARCHIVE_PATH = "archives/selection.zip";
    private static final String ERROR_MESSAGE = "disk is full";
    private static final String OWNER = "USER";
    private static final int TOTAL_COUNT = 50;
    private static final int PAGE_SIZE = 20;
    private static final int SKIPPED_FILES = 3;
    private static final long STORAGE_ID = 9L;
    private static final long ARCHIVE_SIZE_BYTES = 12345L;

    @Autowired
    private AsyncTaskApiService mockAsyncTaskApiService;

    // ------------------------------------------------------------------ POST /filter

    @Test
    public void shouldFailFilterForUnauthorizedUser() {
        performUnauthorizedRequest(post(FILTER_URL));
    }

    @Test
    @WithMockUser
    public void shouldAnswerFilterWithTheTasksAndTheirTotalCount() throws Exception {
        // Both field names are asserted literally, which comparing the whole answer against a
        // serialized expectation cannot do: the envelope is load-bearing, because pipe-common's
        // `filter_async_tasks` reads `elements` out of it and would break on a bare array. One task
        // behind a count of fifty, because the count belongs to the match and not to the page.
        final PagedResult<List<AsyncTask>> expected =
                new PagedResult<>(Collections.singletonList(task(AsyncTaskStatus.RUNNING)), TOTAL_COUNT);
        doReturn(expected).when(mockAsyncTaskApiService).loadByFilter(any());
        final String content = getObjectMapper().writeValueAsString(filter());

        final MvcResult result = performRequest(post(FILTER_URL).content(content));

        assertAnswer(result, expected);
        final JsonNode payload = payloadOf(result);
        assertEquals(TOTAL_COUNT, payload.get("totalCount").asInt());
        assertEquals(TASK_ID, payload.get("elements").get(0).get("id").asText());
    }

    @Test
    @WithMockUser
    public void shouldPassEveryFieldOfTheFilterOn() throws Exception {
        // The body is the whole question this endpoint is asked, and a field that does not bind
        // arrives as null - which is not an error, just an answer to something else. `sortBy` and
        // `ascending` are the newest two and had nothing holding them.
        doReturn(new PagedResult<>(Collections.emptyList(), 0))
                .when(mockAsyncTaskApiService).loadByFilter(any());
        final String content = getObjectMapper().writeValueAsString(filter());

        performRequest(post(FILTER_URL).content(content));

        final AsyncTaskFilterVO received = capturedFilter();
        assertEquals(AsyncTaskType.DOWNLOAD_ARCHIVE, received.getType());
        assertEquals(Collections.singletonList(AsyncTaskStatus.RUNNING), received.getStatuses());
        assertEquals(1, received.getPage().intValue());
        assertEquals(PAGE_SIZE, received.getPageSize().intValue());
        assertEquals(AsyncTaskSortField.STATUS_CHANGED, received.getSortBy());
        assertTrue(received.isAscending());
    }

    @Test
    @WithMockUser
    public void shouldAcceptFilterWithoutABody() throws Exception {
        // The body is optional, and an absent one means "everything I may see". The ACL layer is what
        // turns the null into an empty filter, so the controller has to pass it on rather than refuse.
        doReturn(new PagedResult<>(Collections.emptyList(), 0))
                .when(mockAsyncTaskApiService).loadByFilter(any());

        performRequest(post(FILTER_URL));

        verify(mockAsyncTaskApiService).loadByFilter(null);
    }

    // ------------------------------------------------------------------ GET /{id}

    @Test
    public void shouldFailLoadForUnauthorizedUser() {
        performUnauthorizedRequest(get(TASK_URL));
    }

    @Test
    @WithMockUser
    public void shouldLoadTaskById() throws Exception {
        final AsyncTask expected = task(AsyncTaskStatus.SUCCESS);
        doReturn(expected).when(mockAsyncTaskApiService).load(TASK_ID);

        final MvcResult result = performRequest(get(TASK_URL));

        verify(mockAsyncTaskApiService).load(TASK_ID);
        assertAnswer(result, expected);
    }

    // ------------------------------------------------------------------ GET /{id}/consume

    @Test
    public void shouldFailConsumeForUnauthorizedUser() {
        performUnauthorizedRequest(get(CONSUME_URL));
    }

    @Test
    @WithMockUser
    public void shouldAnswerConsumeWithTheArtifactAndItsType() throws Exception {
        // The artifact is polymorphic and its type is written beside it rather than inside it - an
        // external property - so the type the bean carries and the type id collapse into the single
        // `type` key, and `result` holds only the url. A client picking the payload apart reads that
        // sibling key to know what it got, so both are asserted by name.
        final AsyncTaskConsumeResult expected =
                AsyncTaskConsumeResult.of(DownloadArchiveConsumable.builder().url(ARCHIVE_URL).build());
        doReturn(expected).when(mockAsyncTaskApiService).consume(TASK_ID);

        final MvcResult result = performRequest(get(CONSUME_URL));

        verify(mockAsyncTaskApiService).consume(TASK_ID);
        assertAnswer(result, expected);
        final JsonNode payload = payloadOf(result);
        assertEquals(AsyncTaskType.DOWNLOAD_ARCHIVE.name(), payload.get(TYPE_PARAM).asText());
        assertEquals(ARCHIVE_URL, payload.get("result").get("url").asText());
    }

    // ------------------------------------------------------------------ POST /{id}/cancel

    @Test
    public void shouldFailCancelForUnauthorizedUser() {
        performUnauthorizedRequest(post(CANCEL_URL));
    }

    @Test
    @WithMockUser
    public void shouldCancelTask() throws Exception {
        // A running task is only asked to stop here and confirms the cancellation itself later, so
        // the answer is the task as it now stands rather than an empty one.
        final AsyncTask expected = task(AsyncTaskStatus.CANCEL_REQUESTED);
        doReturn(expected).when(mockAsyncTaskApiService).cancel(TASK_ID);

        final MvcResult result = performRequest(post(CANCEL_URL));

        verify(mockAsyncTaskApiService).cancel(TASK_ID);
        assertAnswer(result, expected);
    }

    // ------------------------------------------------------------------ POST /next

    @Test
    public void shouldFailNextForUnauthorizedUser() {
        performUnauthorizedRequest(post(NEXT_URL).param(TYPE_PARAM, AsyncTaskType.DOWNLOAD_ARCHIVE.name()));
    }

    @Test
    @WithMockUser
    public void shouldHandOutTheNextTaskOfTheTypeAsked() throws Exception {
        final AsyncTask expected = task(AsyncTaskStatus.RUNNING);
        doReturn(Optional.of(expected)).when(mockAsyncTaskApiService)
                .claimNext(AsyncTaskType.DOWNLOAD_ARCHIVE);

        final MvcResult result = performRequest(
                post(NEXT_URL).param(TYPE_PARAM, AsyncTaskType.DOWNLOAD_ARCHIVE.name()));

        verify(mockAsyncTaskApiService).claimNext(AsyncTaskType.DOWNLOAD_ARCHIVE);
        assertAnswer(result, expected);
    }

    @Test
    @WithMockUser
    public void shouldAnswerNextWithoutAPayloadWhenTheQueueIsEmpty() throws Exception {
        // An empty queue is the ordinary answer rather than an error: a worker polls this endpoint in
        // a loop, and must get a success with nothing in it instead of something to handle.
        doReturn(Optional.empty()).when(mockAsyncTaskApiService).claimNext(AsyncTaskType.DOWNLOAD_ARCHIVE);

        final MvcResult result = performRequest(
                post(NEXT_URL).param(TYPE_PARAM, AsyncTaskType.DOWNLOAD_ARCHIVE.name()));

        verify(mockAsyncTaskApiService).claimNext(AsyncTaskType.DOWNLOAD_ARCHIVE);
        assertAnswer(result, null);
    }

    // ------------------------------------------------------------------ POST /{id}/status

    @Test
    public void shouldFailStatusReportForUnauthorizedUser() {
        performUnauthorizedRequest(post(STATUS_URL));
    }

    @Test
    @WithMockUser
    public void shouldReportStatusChange() throws Exception {
        final AsyncTask expected = task(AsyncTaskStatus.STOPPING);
        doReturn(expected).when(mockAsyncTaskApiService).updateStatus(any(), any());
        final String content = getObjectMapper().writeValueAsString(
                AsyncTaskStatusVO.builder().status(AsyncTaskStatus.STOPPING).build());

        final MvcResult result = performRequest(post(STATUS_URL).content(content));

        assertEquals(AsyncTaskStatus.STOPPING, capturedReport().getStatus());
        assertAnswer(result, expected);
    }

    @Test
    @WithMockUser
    public void shouldCarryTheResultOfASuccessReportThrough() throws Exception {
        // The report's payload is polymorphic: its own `type` selects the shape of the rest, and a
        // body whose type is missing or unknown is refused outright rather than arriving as a null
        // result. What is pinned here is that the right shape arrives whole, every field of it -
        // these are what the task is recorded as having produced.
        doReturn(task(AsyncTaskStatus.SUCCESS)).when(mockAsyncTaskApiService).updateStatus(any(), any());
        final String content = getObjectMapper().writeValueAsString(AsyncTaskStatusVO.builder()
                .status(AsyncTaskStatus.SUCCESS)
                .result(DownloadArchiveResult.builder()
                        .storageId(STORAGE_ID)
                        .path(ARCHIVE_PATH)
                        .archiveSizeBytes(ARCHIVE_SIZE_BYTES)
                        .skippedFiles(SKIPPED_FILES)
                        .build())
                .build());

        performRequest(post(STATUS_URL).content(content));

        final AsyncTaskStatusVO reported = capturedReport();
        assertEquals(AsyncTaskStatus.SUCCESS, reported.getStatus());
        assertEquals(AsyncTaskType.DOWNLOAD_ARCHIVE, reported.getResult().getType());
        final DownloadArchiveResult archive = (DownloadArchiveResult) reported.getResult();
        assertEquals(STORAGE_ID, archive.getStorageId().longValue());
        assertEquals(ARCHIVE_PATH, archive.getPath());
        assertEquals(ARCHIVE_SIZE_BYTES, archive.getArchiveSizeBytes().longValue());
        assertEquals(SKIPPED_FILES, archive.getSkippedFiles().intValue());
    }

    @Test
    @WithMockUser
    public void shouldCarryTheErrorMessageOfAFailureReportThrough() throws Exception {
        doReturn(task(AsyncTaskStatus.FAILED)).when(mockAsyncTaskApiService).updateStatus(any(), any());
        final String content = getObjectMapper().writeValueAsString(AsyncTaskStatusVO.builder()
                .status(AsyncTaskStatus.FAILED)
                .errorMessage(ERROR_MESSAGE)
                .build());

        performRequest(post(STATUS_URL).content(content));

        final AsyncTaskStatusVO reported = capturedReport();
        assertEquals(AsyncTaskStatus.FAILED, reported.getStatus());
        assertEquals(ERROR_MESSAGE, reported.getErrorMessage());
    }

    // ------------------------------------------------------------------ helpers

    /**
     * The whole answer, compared as text. This is what {@code assertResponse} does first, without
     * what it does second: comparing the parsed payload goes through {@code equals}, and none of the
     * DTOs behind this controller has value equality. Comparing the whole document still catches a
     * field appearing or going missing, which naming fields one by one does not.
     */
    private void assertAnswer(final MvcResult result, final Object payload) throws Exception {
        assertThat(result.getResponse().getContentAsString()).isEqualToIgnoringWhitespace(
                getObjectMapper().writeValueAsString(buildExpectedResult(payload)));
    }

    private JsonNode payloadOf(final MvcResult result) throws Exception {
        return getObjectMapper().readTree(result.getResponse().getContentAsString()).get("payload");
    }

    /**
     * The filter and the report as the controller received them. Captured rather than matched with
     * {@code eq}, because neither VO has value equality.
     */
    private AsyncTaskFilterVO capturedFilter() {
        final ArgumentCaptor<AsyncTaskFilterVO> captor = ArgumentCaptor.forClass(AsyncTaskFilterVO.class);
        verify(mockAsyncTaskApiService).loadByFilter(captor.capture());
        return captor.getValue();
    }

    private AsyncTaskStatusVO capturedReport() {
        final ArgumentCaptor<AsyncTaskStatusVO> captor = ArgumentCaptor.forClass(AsyncTaskStatusVO.class);
        verify(mockAsyncTaskApiService).updateStatus(eq(TASK_ID), captor.capture());
        return captor.getValue();
    }

    private AsyncTaskFilterVO filter() {
        return AsyncTaskFilterVO.builder()
                .type(AsyncTaskType.DOWNLOAD_ARCHIVE)
                .statuses(Collections.singletonList(AsyncTaskStatus.RUNNING))
                .page(1)
                .pageSize(PAGE_SIZE)
                .sortBy(AsyncTaskSortField.STATUS_CHANGED)
                .ascending(true)
                .build();
    }

    private AsyncTask task(final AsyncTaskStatus status) {
        return AsyncTask.builder()
                .id(TASK_ID)
                .type(AsyncTaskType.DOWNLOAD_ARCHIVE)
                .owner(OWNER)
                .status(status)
                .build();
    }
}
