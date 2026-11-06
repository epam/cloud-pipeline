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

package com.epam.pipeline.manager.async;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.controller.PagedResult;
import com.epam.pipeline.controller.vo.async.AsyncTaskFilterVO;
import com.epam.pipeline.controller.vo.async.AsyncTaskSortField;
import com.epam.pipeline.dto.async.AsyncTask;
import com.epam.pipeline.dto.async.AsyncTaskStatus;
import com.epam.pipeline.dto.async.DownloadArchiveParams;
import com.epam.pipeline.dto.async.DownloadArchiveResult;
import com.epam.pipeline.dto.async.AsyncTaskType;
import com.epam.pipeline.entity.async.AsyncTaskEntity;
import com.epam.pipeline.mapper.async.AsyncTaskMapper;
import com.epam.pipeline.repository.async.AsyncTaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AsyncTaskManagerTest {

    private static final String ID = "4f1a2c90-0000-4000-8000-000000000001";
    private static final String OWNER = "USER";
    private static final String ERROR = "disk is full";
    private static final AsyncTaskType TYPE = AsyncTaskType.DOWNLOAD_ARCHIVE;
    private static final long STORAGE_ID = 1L;
    private static final long ARCHIVE_SIZE_BYTES = 42L;
    private static final int TOTAL_MATCHES = 50;

    private final AsyncTaskRepository repository = Mockito.mock(AsyncTaskRepository.class);
    private final AsyncTaskMapper mapper = Mappers.getMapper(AsyncTaskMapper.class);
    private final MessageHelper messageHelper = Mockito.mock(MessageHelper.class);

    private AsyncTaskManager manager;

    @BeforeEach
    public void setUp() {
        manager = new AsyncTaskManager(repository, mapper, messageHelper, new ObjectMapper());
    }

    @Test
    public void shouldCreateTaskPendingAndOwnedByRequester() {
        Mockito.when(repository.save(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));

        final AsyncTask created = manager.create(TYPE, OWNER, params());

        assertEquals(AsyncTaskStatus.PENDING, created.getStatus());
        assertEquals(OWNER, created.getOwner());
        assertEquals(TYPE, created.getType());
        assertNotNull(created.getCreated());
        assertNotNull(created.getStatusChanged());
    }

    @Test
    public void shouldListWithoutPagingWhenPageSizeIsMissingOrZero() {
        Mockito.when(repository.findAll(Mockito.<Specification<AsyncTaskEntity>>any(),
                Mockito.any(Sort.class))).thenReturn(Collections.emptyList());

        manager.loadByFilter(AsyncTaskFilterVO.builder().build());
        manager.loadByFilter(AsyncTaskFilterVO.builder().pageSize(0).build());

        Mockito.verify(repository, Mockito.times(2)).findAll(
                Mockito.<Specification<AsyncTaskEntity>>any(), Mockito.any(Sort.class));
        Mockito.verify(repository, Mockito.never()).findAll(
                Mockito.<Specification<AsyncTaskEntity>>any(), Mockito.any(Pageable.class));
    }

    @Test
    public void shouldSortByNewestCreatedByDefault() {
        // The order this endpoint has always answered in. A filter that says nothing about sorting -
        // which is every filter the storage async task service sends - must keep getting it.
        stubUnpaged();

        manager.loadByFilter(AsyncTaskFilterVO.builder().build());

        assertEquals(Sort.by(Sort.Direction.DESC, "created"), capturedSort());
    }

    @Test
    public void shouldSortFromTheOldestWhenAscendingIsAsked() {
        stubUnpaged();

        manager.loadByFilter(AsyncTaskFilterVO.builder().ascending(true).build());

        assertEquals(Sort.by(Sort.Direction.ASC, "created"), capturedSort());
    }

    @Test
    public void shouldSortByTheFieldTheFilterNames() {
        // The field name, never the column name: `Sort` resolves the path through the JPA metamodel,
        // so `status_changed` would fail at runtime where `statusChanged` works.
        stubUnpaged();

        manager.loadByFilter(AsyncTaskFilterVO.builder()
                .sortBy(AsyncTaskSortField.STATUS_CHANGED)
                .build());

        assertEquals(Sort.by(Sort.Direction.DESC, "statusChanged"), capturedSort());
    }

    @Test
    public void shouldCarryTheSortIntoThePageItAsksFor() {
        // The paged branch takes the same sort through a Pageable, and it is a separate call on the
        // repository - so sorting could work unpaged and be dropped for a paged request.
        Mockito.when(repository.findAll(Mockito.<Specification<AsyncTaskEntity>>any(),
                Mockito.any(Pageable.class))).thenReturn(Page.empty());

        manager.loadByFilter(AsyncTaskFilterVO.builder()
                .pageSize(10)
                .sortBy(AsyncTaskSortField.FINISHED)
                .ascending(true)
                .build());

        final ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        Mockito.verify(repository).findAll(Mockito.<Specification<AsyncTaskEntity>>any(),
                captor.capture());
        assertEquals(Sort.by(Sort.Direction.ASC, "finished"), captor.getValue().getSort());
    }

    @Test
    public void shouldCountEveryTaskTheFilterMatchesRatherThanThePageItAnswersWith() {
        // Why the answer carries a count at all: a caller asking for one page of fifty matches gets
        // the page, and has no other way of telling that there are forty-nine more.
        Mockito.when(repository.findAll(Mockito.<Specification<AsyncTaskEntity>>any(),
                Mockito.any(Pageable.class))).thenReturn(new PageImpl<>(
                        Collections.singletonList(task(AsyncTaskStatus.RUNNING)),
                        PageRequest.of(0, 1), TOTAL_MATCHES));

        final PagedResult<List<AsyncTask>> listed = manager.loadByFilter(
                AsyncTaskFilterVO.builder().pageSize(1).build());

        assertEquals(1, listed.getElements().size());
        assertEquals(TOTAL_MATCHES, listed.getTotalCount());
    }

    @Test
    public void shouldCountWhatItAnsweredWithWhenNothingIsPaged() {
        Mockito.when(repository.findAll(Mockito.<Specification<AsyncTaskEntity>>any(),
                Mockito.any(Sort.class))).thenReturn(List.of(task(AsyncTaskStatus.RUNNING),
                        task(AsyncTaskStatus.SUCCESS)));

        final PagedResult<List<AsyncTask>> listed = manager.loadByFilter(AsyncTaskFilterVO.builder().build());

        assertEquals(2, listed.getElements().size());
        assertEquals(2, listed.getTotalCount());
    }

    @Test
    public void shouldClaimPendingTask() {
        Mockito.when(repository.lockNextPending(TYPE.name())).thenReturn(Optional.of(ID));
        Mockito.when(repository.moveStatus(Mockito.eq(ID), Mockito.eq(AsyncTaskStatus.PENDING.name()),
                Mockito.eq(AsyncTaskStatus.RUNNING.name()), Mockito.isNull(), Mockito.isNull(), Mockito.any()))
                .thenReturn(1);
        Mockito.when(repository.findById(ID)).thenReturn(Optional.of(task(AsyncTaskStatus.RUNNING)));

        final Optional<AsyncTask> claimed = manager.claimNext(TYPE);

        assertTrue(claimed.isPresent());
        assertEquals(AsyncTaskStatus.RUNNING, claimed.get().getStatus());
    }

    @Test
    public void shouldNotClaimTaskTakenByAnotherWorker() {
        Mockito.when(repository.lockNextPending(TYPE.name())).thenReturn(Optional.of(ID));
        Mockito.when(repository.moveStatus(Mockito.eq(ID), Mockito.eq(AsyncTaskStatus.PENDING.name()),
                Mockito.eq(AsyncTaskStatus.RUNNING.name()), Mockito.isNull(), Mockito.isNull(), Mockito.any()))
                .thenReturn(0);

        assertFalse(manager.claimNext(TYPE).isPresent());
        Mockito.verify(repository, Mockito.never()).findById(Mockito.anyString());
    }

    @Test
    public void shouldReturnEmptyWhenQueueHoldsNothingOfType() {
        Mockito.when(repository.lockNextPending(TYPE.name())).thenReturn(Optional.empty());

        assertFalse(manager.claimNext(TYPE).isPresent());
        Mockito.verify(repository, Mockito.never())
                .moveStatus(Mockito.anyString(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
                        Mockito.any());
    }

    @Test
    public void shouldApplyAllowedStatusChange() {
        mockTask(AsyncTaskStatus.RUNNING);
        Mockito.when(repository.moveStatus(Mockito.eq(ID), Mockito.eq(AsyncTaskStatus.RUNNING.name()),
                Mockito.eq(AsyncTaskStatus.SUCCESS.name()), Mockito.anyString(), Mockito.isNull(),
                Mockito.any())).thenReturn(1);

        manager.updateStatus(ID, AsyncTaskStatus.SUCCESS, result(), null);

        Mockito.verify(repository).moveStatus(Mockito.eq(ID), Mockito.eq(AsyncTaskStatus.RUNNING.name()),
                Mockito.eq(AsyncTaskStatus.SUCCESS.name()), Mockito.anyString(), Mockito.isNull(), Mockito.any());
    }

    @Test
    public void shouldRefuseToReportStatusesThatHaveTheirOwnOperation() {
        mockTask(AsyncTaskStatus.PENDING);

        assertThrows(() -> manager.updateStatus(ID, AsyncTaskStatus.RUNNING, null, null));
        assertThrows(() -> manager.updateStatus(ID, AsyncTaskStatus.PENDING, null, null));
        assertThrows(() -> manager.updateStatus(ID, AsyncTaskStatus.CANCEL_REQUESTED, null, null));
        Mockito.verify(repository, Mockito.never())
                .moveStatus(Mockito.anyString(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
                        Mockito.any());
    }

    @Test
    public void shouldRequireResultOnSuccess() {
        mockTask(AsyncTaskStatus.RUNNING);

        assertThrows(() -> manager.updateStatus(ID, AsyncTaskStatus.SUCCESS, null, null));
        Mockito.verify(repository, Mockito.never())
                .moveStatus(Mockito.anyString(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
                        Mockito.any());
    }

    @Test
    public void shouldRequireErrorMessageOnFailure() {
        mockTask(AsyncTaskStatus.RUNNING);

        assertThrows(() -> manager.updateStatus(ID, AsyncTaskStatus.FAILED, null, null));
    }

    @Test
    public void shouldRejectResultOnStatusThatProducesNothing() {
        mockTask(AsyncTaskStatus.RUNNING);

        assertThrows(() -> manager.updateStatus(ID, AsyncTaskStatus.STOPPING, result(), null));
    }

    @Test
    public void shouldRejectErrorMessageOnSuccess() {
        mockTask(AsyncTaskStatus.RUNNING);

        assertThrows(() -> manager.updateStatus(ID, AsyncTaskStatus.SUCCESS, result(), ERROR));
    }

    @Test
    public void shouldRejectResultOnFailure() {
        mockTask(AsyncTaskStatus.RUNNING);

        assertThrows(() -> manager.updateStatus(ID, AsyncTaskStatus.FAILED, result(), ERROR));
    }

    @Test
    public void shouldAcceptStoppingWithoutPayload() {
        mockTask(AsyncTaskStatus.RUNNING);
        Mockito.when(repository.moveStatus(Mockito.eq(ID), Mockito.eq(AsyncTaskStatus.RUNNING.name()),
                Mockito.eq(AsyncTaskStatus.STOPPING.name()), Mockito.isNull(), Mockito.isNull(),
                Mockito.any())).thenReturn(1);

        manager.updateStatus(ID, AsyncTaskStatus.STOPPING, null, null);

        Mockito.verify(repository).moveStatus(Mockito.eq(ID), Mockito.any(), Mockito.any(),
                Mockito.isNull(), Mockito.isNull(), Mockito.any());
    }

    @Test
    public void shouldRejectStatusChangeOutsideTheMatrix() {
        mockTask(AsyncTaskStatus.FAILED);

        assertThrows(() -> manager.updateStatus(ID, AsyncTaskStatus.SUCCESS, result(), null));
        Mockito.verify(repository, Mockito.never())
                .moveStatus(Mockito.anyString(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
                        Mockito.any());
    }

    @Test
    public void shouldAcceptRepeatedReportOfTheSameStatus() {
        mockTask(AsyncTaskStatus.SUCCESS);

        // a worker that lost the response to its first report retries with the same body
        final AsyncTask reported = manager.updateStatus(ID, AsyncTaskStatus.SUCCESS, result(), null);

        assertEquals(AsyncTaskStatus.SUCCESS, reported.getStatus());
        Mockito.verify(repository, Mockito.never())
                .moveStatus(Mockito.anyString(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
                        Mockito.any());
    }

    @Test
    public void shouldFailStatusChangeWhenTaskMovedMeanwhile() {
        mockTask(AsyncTaskStatus.RUNNING);
        Mockito.when(repository.moveStatus(Mockito.eq(ID), Mockito.eq(AsyncTaskStatus.RUNNING.name()),
                Mockito.eq(AsyncTaskStatus.FAILED.name()), Mockito.isNull(), Mockito.eq(ERROR), Mockito.any()))
                .thenReturn(0);

        assertThrows(() -> manager.updateStatus(ID, AsyncTaskStatus.FAILED, null, ERROR));
    }

    @Test
    public void shouldCancelTaskNobodyHasTaken() {
        Mockito.when(repository.cancelTask(Mockito.eq(ID), Mockito.any())).thenReturn(1);
        Mockito.when(repository.findById(ID)).thenReturn(Optional.of(task(AsyncTaskStatus.CANCELLED)));

        assertEquals(AsyncTaskStatus.CANCELLED, manager.cancel(ID).getStatus());
    }

    @Test
    public void shouldAskRunningTaskToStop() {
        Mockito.when(repository.cancelTask(Mockito.eq(ID), Mockito.any())).thenReturn(1);
        Mockito.when(repository.findById(ID)).thenReturn(Optional.of(task(AsyncTaskStatus.CANCEL_REQUESTED)));

        assertEquals(AsyncTaskStatus.CANCEL_REQUESTED, manager.cancel(ID).getStatus());
    }

    @Test
    public void shouldLeaveTaskAlreadyStoppingAsItIs() {
        Mockito.when(repository.cancelTask(Mockito.eq(ID), Mockito.any())).thenReturn(0);
        mockTask(AsyncTaskStatus.STOPPING);

        assertEquals(AsyncTaskStatus.STOPPING, manager.cancel(ID).getStatus());
    }

    @Test
    public void shouldRejectCancelOfFinishedTask() {
        Mockito.when(repository.cancelTask(Mockito.eq(ID), Mockito.any())).thenReturn(0);
        mockTask(AsyncTaskStatus.SUCCESS);

        assertThrows(() -> manager.cancel(ID));
    }

    @Test
    public void shouldFailWhenTaskIsUnknown() {
        Mockito.when(repository.findById(ID)).thenReturn(Optional.empty());

        assertThrows(() -> manager.load(ID));
    }

    private DownloadArchiveParams params() {
        return DownloadArchiveParams.builder()
                .storageId(STORAGE_ID)
                .paths(List.of("data/"))
                .archiveName("selection.zip")
                .build();
    }

    private DownloadArchiveResult result() {
        return DownloadArchiveResult.builder().archiveSizeBytes(ARCHIVE_SIZE_BYTES).build();
    }

    private void mockTask(final AsyncTaskStatus status) {
        Mockito.when(repository.findById(ID)).thenReturn(Optional.of(task(status)));
    }

    private AsyncTaskEntity task(final AsyncTaskStatus status) {
        return AsyncTaskEntity.builder()
                .id(ID)
                .type(TYPE)
                .owner(OWNER)
                .status(status)
                .build();
    }

    private void stubUnpaged() {
        Mockito.when(repository.findAll(Mockito.<Specification<AsyncTaskEntity>>any(),
                Mockito.any(Sort.class))).thenReturn(Collections.emptyList());
    }

    private Sort capturedSort() {
        final ArgumentCaptor<Sort> captor = ArgumentCaptor.forClass(Sort.class);
        Mockito.verify(repository).findAll(Mockito.<Specification<AsyncTaskEntity>>any(),
                captor.capture());
        return captor.getValue();
    }
}
