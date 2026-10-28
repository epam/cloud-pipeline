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

package com.epam.pipeline.repository.async;

import com.epam.pipeline.dto.async.AsyncTaskStatus;
import com.epam.pipeline.dto.async.AsyncTaskType;
import com.epam.pipeline.dto.async.DownloadArchiveParams;
import com.epam.pipeline.dto.async.DownloadArchiveResult;
import com.epam.pipeline.entity.async.AsyncTaskEntity;
import com.epam.pipeline.test.repository.AbstractJpaTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The statements of this repository are written by hand, and three of them are native, so nothing but
 * a database answers whether they do what they say: the claim takes the oldest row and only that one,
 * a move applies only while the task is still where the caller left it, and the payload columns are
 * {@code jsonb} written through a cast. Everywhere else these are mocked away.
 *
 * <p>One claim of the same queries is not covered here: that two callers at once get two different
 * tasks, which is what {@code SKIP LOCKED} is for. It takes a second connection holding a lock while
 * this one asks, and this harness gives a single transaction.</p>
 */
@Transactional
public class AsyncTaskRepositoryTest extends AbstractJpaTest {

    private static final String TYPE = AsyncTaskType.DOWNLOAD_ARCHIVE.name();
    private static final String FOREIGN_TYPE = "SOMETHING_ELSE";
    private static final String OWNER = "USER";
    private static final String ANOTHER_OWNER = "ANOTHER_USER";
    private static final String ERROR = "disk is full";
    private static final String PATH = "data/";
    private static final String ARCHIVE_NAME = "selection.zip";
    private static final Long STORAGE_ID = 1L;
    private static final long ACTIVE_TASKS_OF_OWNER = 4;
    private static final LocalDateTime LONG_AGO = LocalDateTime.of(2026, 10, 9, 9, 0, 0);
    private static final LocalDateTime EARLIER = LocalDateTime.of(2026, 10, 9, 10, 0, 0);
    private static final LocalDateTime LATER = LocalDateTime.of(2026, 10, 9, 11, 0, 0);
    private static final LocalDateTime STARTED_AT = LocalDateTime.of(2026, 10, 9, 12, 0, 0);
    private static final LocalDateTime FINISHED_AT = LocalDateTime.of(2026, 10, 9, 13, 0, 0);
    private static final LocalDateTime EXPIRED_AT = LocalDateTime.of(2026, 10, 16, 13, 0, 0);

    /**
     * What an executor reports, spelled out rather than serialized here: the executor is a separate
     * service, so this - and not whatever a mapper of this process produces - is the wire format.
     */
    private static final String RESULT_JSON = "{\"type\":\"DOWNLOAD_ARCHIVE\",\"storageId\":9,"
            + "\"path\":\"archives/%s.zip\",\"archiveSizeBytes\":1024,\"skippedFiles\":0}";
    private static final Long ARCHIVE_STORAGE_ID = 9L;
    private static final Long ARCHIVE_SIZE_BYTES = 1024L;

    @Autowired
    private AsyncTaskRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    /**
     * The claim and the count read the table rather than a row of it, so a row left behind by anything
     * else would answer in their place. Nothing else writes this table today, and this keeps it that
     * way: the test's own transaction is rolled back, so the emptying never leaves the test.
     */
    @BeforeEach
    public void emptyTheQueue() {
        repository.deleteAll();
    }

    @Test
    public void shouldAssignAnIdAndKeepTheTaskAsItWasSaved() {
        final String id = save(AsyncTaskStatus.PENDING, OWNER, EARLIER).getId();

        final AsyncTaskEntity saved = reload(id);

        assertDoesNotThrow(() -> UUID.fromString(saved.getId()));
        assertEquals(AsyncTaskType.DOWNLOAD_ARCHIVE, saved.getType());
        assertEquals(OWNER, saved.getOwner());
        assertEquals(AsyncTaskStatus.PENDING, saved.getStatus());
        assertEquals(EARLIER, saved.getCreated());
        assertEquals(EARLIER, saved.getStatusChanged());
        assertNull(saved.getStarted());
        assertNull(saved.getFinished());
        final DownloadArchiveParams params = assertInstanceOf(DownloadArchiveParams.class, saved.getParams());
        assertEquals(STORAGE_ID, params.getStorageId());
        assertEquals(List.of(PATH), params.getPaths());
        assertEquals(ARCHIVE_NAME, params.getArchiveName());
    }

    @Test
    public void shouldLockTheOldestPendingTaskOfTheType() {
        // Saved newest first, so a query that dropped the ordering would hand out the newer task and
        // leave the older one waiting behind it.
        save(AsyncTaskStatus.PENDING, OWNER, LATER);
        final String oldestPending = save(AsyncTaskStatus.PENDING, OWNER, EARLIER).getId();
        save(AsyncTaskStatus.SUCCESS, OWNER, LONG_AGO);
        entityManager.flush();

        assertEquals(oldestPending, repository.lockNextPending(TYPE).orElse(null));
    }

    @Test
    public void shouldLockNothingWhenNoTaskIsPending() {
        save(AsyncTaskStatus.RUNNING, OWNER, EARLIER);
        save(AsyncTaskStatus.SUCCESS, OWNER, LATER);
        entityManager.flush();

        assertFalse(repository.lockNextPending(TYPE).isPresent());
    }

    @Test
    public void shouldLockNothingOfAnotherType() {
        // The queue is per type, and a worker asks for its own. Only one type exists today, so the type
        // that has to be skipped is named as the literal the query would be given.
        save(AsyncTaskStatus.PENDING, OWNER, EARLIER);
        entityManager.flush();

        assertFalse(repository.lockNextPending(FOREIGN_TYPE).isPresent());
    }

    @Test
    public void shouldMoveTheTaskOnlyWhileItIsStillWhereTheCallerLeftIt() {
        final String id = save(AsyncTaskStatus.PENDING, OWNER, EARLIER).getId();

        assertEquals(1, moveStatus(id, AsyncTaskStatus.PENDING, AsyncTaskStatus.RUNNING, null, null, STARTED_AT));
        // The same claim once more: whoever sends it is working from a status the task has left, and
        // changing nothing is how that is reported back.
        assertEquals(0, moveStatus(id, AsyncTaskStatus.PENDING, AsyncTaskStatus.RUNNING, null, null, STARTED_AT));
        assertEquals(AsyncTaskStatus.RUNNING, reload(id).getStatus());
    }

    @Test
    public void shouldStampStartedWhenTheTaskStartsRunning() {
        final String id = save(AsyncTaskStatus.PENDING, OWNER, EARLIER).getId();

        moveStatus(id, AsyncTaskStatus.PENDING, AsyncTaskStatus.RUNNING, null, null, STARTED_AT);

        final AsyncTaskEntity running = reload(id);
        assertEquals(STARTED_AT, running.getStatusChanged());
        assertEquals(STARTED_AT, running.getStarted());
        assertNull(running.getFinished());
    }

    @Test
    public void shouldStampFinishedAndStoreTheResultOnSuccess() {
        final String id = running(EARLIER);

        moveStatus(id, AsyncTaskStatus.RUNNING, AsyncTaskStatus.SUCCESS, resultJson(id), null, FINISHED_AT);

        final AsyncTaskEntity succeeded = reload(id);
        assertEquals(FINISHED_AT, succeeded.getStatusChanged());
        assertEquals(FINISHED_AT, succeeded.getFinished());
        // The moment the work began is not what the report is about, and it survives the report.
        assertEquals(STARTED_AT, succeeded.getStarted());
        final DownloadArchiveResult result = assertInstanceOf(DownloadArchiveResult.class, succeeded.getResult());
        assertEquals(ARCHIVE_STORAGE_ID, result.getStorageId());
        assertEquals("archives/" + id + ".zip", result.getPath());
        assertEquals(ARCHIVE_SIZE_BYTES, result.getArchiveSizeBytes());
    }

    @Test
    public void shouldKeepTheRecordedResultWhenALaterReportCarriesNone() {
        // The cleaner reports expiry with no payload, and the archive's location has to survive it: an
        // expired task is still asked where its archive was.
        final String id = running(EARLIER);
        moveStatus(id, AsyncTaskStatus.RUNNING, AsyncTaskStatus.SUCCESS, resultJson(id), null, FINISHED_AT);

        moveStatus(id, AsyncTaskStatus.SUCCESS, AsyncTaskStatus.EXPIRED, null, null, EXPIRED_AT);

        final AsyncTaskEntity expired = reload(id);
        assertEquals(AsyncTaskStatus.EXPIRED, expired.getStatus());
        assertEquals(EXPIRED_AT, expired.getStatusChanged());
        assertEquals("archives/" + id + ".zip",
                assertInstanceOf(DownloadArchiveResult.class, expired.getResult()).getPath());
    }

    @Test
    public void shouldStoreTheErrorMessageOnFailure() {
        final String id = running(EARLIER);

        moveStatus(id, AsyncTaskStatus.RUNNING, AsyncTaskStatus.FAILED, null, ERROR, FINISHED_AT);

        final AsyncTaskEntity failed = reload(id);
        assertEquals(AsyncTaskStatus.FAILED, failed.getStatus());
        assertEquals(ERROR, failed.getErrorMessage());
        assertNull(failed.getResult());
        // Only success is a finish here. A failed task is dated by status_changed, which is what the
        // filter's finishedBefore cannot be used to sweep.
        assertNull(failed.getFinished());
    }

    @Test
    public void shouldKeepTheErrorMessageWhenAReportIsRepeated() {
        // The same guard the result column carries, on the other column: a report that says nothing
        // about the error must not erase what is recorded. Here it is a worker that lost the response
        // to its report and sends it again - which the manager answers without touching the row, so
        // this is the only place the guard itself is exercised.
        final String id = running(EARLIER);
        moveStatus(id, AsyncTaskStatus.RUNNING, AsyncTaskStatus.FAILED, null, ERROR, FINISHED_AT);

        moveStatus(id, AsyncTaskStatus.FAILED, AsyncTaskStatus.FAILED, null, null, EXPIRED_AT);

        assertEquals(ERROR, reload(id).getErrorMessage());
    }

    @Test
    public void shouldCancelATaskNobodyHasTakenAtOnce() {
        final String id = save(AsyncTaskStatus.PENDING, OWNER, EARLIER).getId();

        assertEquals(1, repository.cancelTask(id, STARTED_AT));

        final AsyncTaskEntity cancelled = reload(id);
        assertEquals(AsyncTaskStatus.CANCELLED, cancelled.getStatus());
        assertEquals(STARTED_AT, cancelled.getStatusChanged());
    }

    @Test
    public void shouldAskARunningTaskToStopRatherThanCancelItOutright() {
        final String id = running(EARLIER);

        assertEquals(1, repository.cancelTask(id, FINISHED_AT));

        assertEquals(AsyncTaskStatus.CANCEL_REQUESTED, reload(id).getStatus());
        // Asked a second time - the user pressing cancel twice - the request has nothing left to do:
        // the task has moved past the two statuses a cancellation starts from.
        assertEquals(0, repository.cancelTask(id, EXPIRED_AT));
        assertEquals(FINISHED_AT, reload(id).getStatusChanged());
    }

    @Test
    public void shouldNotCancelATaskThatHasFinished() {
        final String id = save(AsyncTaskStatus.SUCCESS, OWNER, EARLIER).getId();

        assertEquals(0, repository.cancelTask(id, FINISHED_AT));

        final AsyncTaskEntity untouched = reload(id);
        assertEquals(AsyncTaskStatus.SUCCESS, untouched.getStatus());
        assertEquals(EARLIER, untouched.getStatusChanged());
    }

    @Test
    public void shouldCountOnlyTheActiveTasksOfTheOwner() {
        // The quota of one user, so a task of another and a task already over do not count towards it.
        // Every active status is here: one dropped from the set would let a user past their quota.
        save(AsyncTaskStatus.PENDING, OWNER, EARLIER);
        save(AsyncTaskStatus.RUNNING, OWNER, EARLIER);
        save(AsyncTaskStatus.CANCEL_REQUESTED, OWNER, EARLIER);
        save(AsyncTaskStatus.STOPPING, OWNER, EARLIER);
        save(AsyncTaskStatus.SUCCESS, OWNER, EARLIER);
        save(AsyncTaskStatus.PENDING, ANOTHER_OWNER, EARLIER);
        entityManager.flush();

        assertEquals(ACTIVE_TASKS_OF_OWNER,
                repository.countByOwnerAndStatusIn(OWNER, AsyncTaskStatus.activeStatuses()));
    }

    private AsyncTaskEntity save(final AsyncTaskStatus status, final String owner, final LocalDateTime created) {
        return repository.save(AsyncTaskEntity.builder()
                .type(AsyncTaskType.DOWNLOAD_ARCHIVE)
                .owner(owner)
                .status(status)
                .params(DownloadArchiveParams.builder()
                        .storageId(STORAGE_ID)
                        .paths(List.of(PATH))
                        .archiveName(ARCHIVE_NAME)
                        .build())
                .created(created)
                .statusChanged(created)
                .build());
    }

    private String running(final LocalDateTime created) {
        final String id = save(AsyncTaskStatus.PENDING, OWNER, created).getId();
        moveStatus(id, AsyncTaskStatus.PENDING, AsyncTaskStatus.RUNNING, null, null, STARTED_AT);
        return id;
    }

    private int moveStatus(final String id, final AsyncTaskStatus expected, final AsyncTaskStatus target,
                           final String resultJson, final String errorMessage, final LocalDateTime changedAt) {
        return repository.moveStatus(id, expected.name(), target.name(), resultJson, errorMessage, changedAt);
    }

    private String resultJson(final String id) {
        return String.format(RESULT_JSON, id);
    }

    private AsyncTaskEntity reload(final String id) {
        entityManager.flush();
        entityManager.clear();
        return repository.findById(id).orElseThrow();
    }
}
