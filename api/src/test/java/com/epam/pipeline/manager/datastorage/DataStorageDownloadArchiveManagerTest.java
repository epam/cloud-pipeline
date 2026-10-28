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

package com.epam.pipeline.manager.datastorage;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.controller.vo.DownloadArchiveVO;
import com.epam.pipeline.dto.async.AsyncTask;
import com.epam.pipeline.dto.async.AsyncTaskStatus;
import com.epam.pipeline.dto.async.AsyncTaskType;
import com.epam.pipeline.dto.async.DownloadArchiveConsumable;
import com.epam.pipeline.dto.async.DownloadArchiveParams;
import com.epam.pipeline.dto.async.DownloadArchiveResult;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.ContentDisposition;
import com.epam.pipeline.entity.datastorage.DataStorageDownloadFileUrl;
import com.epam.pipeline.entity.datastorage.DownloadArchiveConfig;
import com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage;
import com.epam.pipeline.manager.async.AsyncTaskManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.List;

import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class DataStorageDownloadArchiveManagerTest {

    private static final Long STORAGE_ID = 1L;
    private static final String OWNER = "USER";
    private static final String TASK_ID = "4f1a2c90-0000-4000-8000-000000000001";
    private static final int TASK_LIMIT = 3;
    private static final Long ARCHIVE_STORAGE_ID = 9L;
    private static final Long PREVIOUS_STORAGE_ID = 7L;
    private static final Long ARCHIVE_SIZE_BYTES = 1024L;

    private final DataStorageManager dataStorageManager = Mockito.mock(DataStorageManager.class);
    private final AsyncTaskManager asyncTaskManager = Mockito.mock(AsyncTaskManager.class);
    private final PreferenceManager preferenceManager = Mockito.mock(PreferenceManager.class);
    private final StorageProviderManager storageProviderManager =
            Mockito.mock(StorageProviderManager.class);
    private final MessageHelper messageHelper = Mockito.mock(MessageHelper.class);

    private DataStorageDownloadArchiveManager manager;

    @BeforeEach
    public void setUp() {
        manager = new DataStorageDownloadArchiveManager(dataStorageManager, asyncTaskManager,
                preferenceManager, storageProviderManager, messageHelper);
        Mockito.when(preferenceManager.getPreference(
                SystemPreferences.DATA_STORAGE_DOWNLOAD_ARCHIVE_CONFIG)).thenReturn(config());
    }

    @Test
    public void shouldPassRequestedPathsIntoTheTask() {
        manager.createTask(STORAGE_ID, request("selection.zip"), OWNER);

        final ArgumentCaptor<DownloadArchiveParams> params =
                ArgumentCaptor.forClass(DownloadArchiveParams.class);
        Mockito.verify(asyncTaskManager).create(Mockito.eq(AsyncTaskType.DOWNLOAD_ARCHIVE),
                Mockito.eq(OWNER), params.capture());
        assertEquals(STORAGE_ID, params.getValue().getStorageId());
        assertEquals(List.of("data/"), params.getValue().getPaths());
        assertEquals("selection.zip", params.getValue().getArchiveName());
    }

    @Test
    public void shouldCheckStorageExists() {
        manager.createTask(STORAGE_ID, request(null), OWNER);

        Mockito.verify(dataStorageManager).load(STORAGE_ID);
    }

    @Test
    public void shouldCheckReadAccessToEveryRequestedPath() {
        final DownloadArchiveVO request = DownloadArchiveVO.builder()
                .paths(List.of("data/", "notes.txt"))
                .build();

        manager.createTask(STORAGE_ID, request, OWNER);

        Mockito.verify(dataStorageManager).checkReadPermissionsOnPaths(Mockito.any(), Mockito.eq(
                List.of("data/", "notes.txt")));
    }

    @Test
    public void shouldNotCreateTaskWhenPathIsNotReadable() {
        Mockito.doThrow(new IllegalArgumentException("forbidden"))
                .when(dataStorageManager).checkReadPermissionsOnPaths(Mockito.any(), Mockito.any());

        assertThrows(() -> manager.createTask(STORAGE_ID, request(null), OWNER));
        Mockito.verify(asyncTaskManager, Mockito.never())
                .create(Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    public void shouldFallBackToDefaultArchiveName() {
        manager.createTask(STORAGE_ID, request(null), OWNER);

        final ArgumentCaptor<DownloadArchiveParams> params =
                ArgumentCaptor.forClass(DownloadArchiveParams.class);
        Mockito.verify(asyncTaskManager).create(Mockito.any(), Mockito.any(), params.capture());
        assertEquals("archive.zip", params.getValue().getArchiveName());
    }

    @Test
    public void shouldRejectArchiveNameThatCouldEscapeThePrefix() {
        assertThrows(() -> manager.createTask(STORAGE_ID, request("../../etc/passwd"), OWNER));
        assertThrows(() -> manager.createTask(STORAGE_ID, request("a/b.zip"), OWNER));
        assertThrows(() -> manager.createTask(STORAGE_ID, request("name\";x.zip"), OWNER));
    }

    @Test
    public void shouldRejectRequestWithoutPaths() {
        assertThrows(() -> manager.createTask(STORAGE_ID,
                DownloadArchiveVO.builder().paths(Collections.emptyList()).build(), OWNER));
    }

    @Test
    public void shouldRejectRequestOverTheUserQuota() {
        Mockito.when(asyncTaskManager.countActive(OWNER)).thenReturn((long) TASK_LIMIT);

        assertThrows(() -> manager.createTask(STORAGE_ID, request(null), OWNER));
        Mockito.verify(asyncTaskManager, Mockito.never())
                .create(Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    public void shouldAllowRequestUnderTheUserQuota() {
        Mockito.when(asyncTaskManager.countActive(OWNER)).thenReturn((long) TASK_LIMIT - 1);

        manager.createTask(STORAGE_ID, request(null), OWNER);

        Mockito.verify(asyncTaskManager).create(Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    public void shouldDeriveArchiveKeyUnderTheConfiguredPrefix() {
        assertEquals("team/" + TASK_ID + ".zip", manager.archivePath(
                task(AsyncTaskStatus.SUCCESS), configWithPrefix("team")));
        assertEquals("team/" + TASK_ID + ".zip", manager.archivePath(
                task(AsyncTaskStatus.SUCCESS), configWithPrefix("/team/")));
        // A lone slash is how the root of the bucket is asked for.
        assertEquals(TASK_ID + ".zip", manager.archivePath(
                task(AsyncTaskStatus.SUCCESS), configWithPrefix("/")));
        // Blank keeps the default, which is what an installation that never set the field runs on.
        assertEquals("archives/" + TASK_ID + ".zip", manager.archivePath(
                task(AsyncTaskStatus.SUCCESS), configWithPrefix("")));
    }

    @Test
    public void shouldPreferTheLocationRecordedInTheResult() {
        // Why the result carries a location at all. The destination has been changed since this archive
        // was written, so deriving would name an object that was never there - and the user would get a
        // link that signs cleanly and answers 404.
        final AsyncTask task = task(AsyncTaskStatus.SUCCESS);
        task.setResult(DownloadArchiveResult.builder()
                .storageId(PREVIOUS_STORAGE_ID)
                .path("previous-prefix/" + TASK_ID + ".zip")
                .archiveSizeBytes(ARCHIVE_SIZE_BYTES)
                .build());

        assertEquals(PREVIOUS_STORAGE_ID, manager.archiveStorageId(task, config()));
        assertEquals("previous-prefix/" + TASK_ID + ".zip", manager.archivePath(task, config()));
    }

    @Test
    public void shouldFallBackToTheConfiguredLocationWhenTheResultRecordedNone() {
        // A worker that uploaded and then died recorded nothing, so the derived key is the only guess.
        final AsyncTask task = task(AsyncTaskStatus.SUCCESS);
        task.setResult(DownloadArchiveResult.builder()
                .archiveSizeBytes(ARCHIVE_SIZE_BYTES)
                .build());

        assertEquals(ARCHIVE_STORAGE_ID, manager.archiveStorageId(task, config()));
        assertEquals("archives/" + TASK_ID + ".zip", manager.archivePath(task, config()));
    }

    @Test
    public void shouldRefuseARecordedKeyThatDoesNotBelongToTheTask() {
        // The recorded key is signed as it stands. It is storage-relative, so a wrong one can only
        // name something else in the archive storage - which is another user's archive, and the
        // platform would sign it and hand the link to this task's owner.
        final AsyncTask task = task(AsyncTaskStatus.SUCCESS);
        task.setResult(DownloadArchiveResult.builder()
                .storageId(ARCHIVE_STORAGE_ID)
                .path("archives/someone-elses-task.zip")
                .build());

        assertThrows(() -> manager.archivePath(task, config()));
    }

    @Test
    public void shouldAcceptARecordedKeyAtTheRootOfItsStorage() {
        final AsyncTask task = task(AsyncTaskStatus.SUCCESS);
        task.setResult(DownloadArchiveResult.builder()
                .storageId(ARCHIVE_STORAGE_ID)
                .path(TASK_ID + ".zip")
                .build());

        assertEquals(TASK_ID + ".zip", manager.archivePath(task, config()));
    }

    @Test
    public void shouldOfferTheArchiveNameTheUserAskedFor() {
        // The object key carries the task id, so without this the browser would save a uuid for a file
        // the user named themselves.
        final AsyncTask task = task(AsyncTaskStatus.SUCCESS);
        task.setParams(DownloadArchiveParams.builder()
                .storageId(STORAGE_ID)
                .archiveName("selection.zip")
                .build());

        assertEquals("selection.zip", manager.offeredArchiveName(task));
    }

    @Test
    public void shouldOfferTheDefaultNameWhenTheTaskNamesNone() {
        assertEquals("archive.zip", manager.offeredArchiveName(task(AsyncTaskStatus.SUCCESS)));

        final AsyncTask blank = task(AsyncTaskStatus.SUCCESS);
        blank.setParams(DownloadArchiveParams.builder().storageId(STORAGE_ID).build());

        assertEquals("archive.zip", manager.offeredArchiveName(blank));
    }

    @Test
    public void shouldOfferANameEndingInTheArchiveExtension() {
        // The name is checked for characters that are safe in a header, not for an extension, so a user
        // may legitimately have asked for "report" - and a zip saved under that name opens in nothing.
        assertEquals("report.zip", manager.offeredArchiveName(named("report")));
        // Already there, in either case, and it is not doubled.
        assertEquals("report.zip", manager.offeredArchiveName(named("report.zip")));
        assertEquals("report.ZIP", manager.offeredArchiveName(named("report.ZIP")));
    }

    private AsyncTask named(final String archiveName) {
        final AsyncTask task = task(AsyncTaskStatus.SUCCESS);
        task.setParams(DownloadArchiveParams.builder()
                .storageId(STORAGE_ID)
                .archiveName(archiveName)
                .build());
        return task;
    }

    @Test
    public void shouldHandOverASignedLinkBuiltFromTheTask() {
        // Untestable until the destination became a platform storage: signing used to build its own S3
        // helper in place, so there was no seam to assert the signed request through.
        final AbstractDataStorage storage = new S3bucketDataStorage();
        final DataStorageDownloadFileUrl signed = new DataStorageDownloadFileUrl();
        signed.setUrl("https://signed");
        Mockito.when(dataStorageManager.load(ARCHIVE_STORAGE_ID)).thenReturn(storage);
        Mockito.when(storageProviderManager.generateDownloadURL(Mockito.any(), Mockito.anyString(),
                Mockito.any(), Mockito.any(), Mockito.anyString())).thenReturn(signed);

        final DownloadArchiveConsumable consumable = manager.consume(named("selection.zip"));

        assertEquals("https://signed", consumable.getUrl());
        assertEquals(AsyncTaskType.DOWNLOAD_ARCHIVE, consumable.getType());
        // The three things the caller cannot see otherwise: the key, that it is a download, and the
        // name the browser will save it under.
        Mockito.verify(storageProviderManager).generateDownloadURL(Mockito.eq(storage),
                Mockito.eq("archives/" + TASK_ID + ".zip"), Mockito.isNull(),
                Mockito.eq(ContentDisposition.ATTACHMENT), Mockito.eq("selection.zip"));
    }

    @Test
    public void shouldRefuseToHandOverAnArchiveThatIsNotReady() {
        assertThrows(() -> manager.consume(task(AsyncTaskStatus.RUNNING)));
        assertThrows(() -> manager.consume(task(AsyncTaskStatus.FAILED)));
        assertThrows(() -> manager.consume(task(AsyncTaskStatus.EXPIRED)));
    }

    @Test
    public void shouldRefuseToHandOverAnArchiveWhenNoStorageIsConfigured() {
        Mockito.when(preferenceManager.getPreference(
                SystemPreferences.DATA_STORAGE_DOWNLOAD_ARCHIVE_CONFIG))
                .thenReturn(DownloadArchiveConfig.builder()
                        .maxUserTasks(TASK_LIMIT)
                        .build());

        assertThrows(() -> manager.consume(task(AsyncTaskStatus.SUCCESS)));
        Mockito.verify(dataStorageManager, Mockito.never()).load(Mockito.anyLong());
    }

    private DownloadArchiveConfig config() {
        return DownloadArchiveConfig.builder()
                .destinationStorageId(ARCHIVE_STORAGE_ID)
                .maxUserTasks(TASK_LIMIT)
                .build();
    }

    private DownloadArchiveConfig configWithPrefix(final String prefix) {
        return DownloadArchiveConfig.builder()
                .destinationStorageId(ARCHIVE_STORAGE_ID)
                .destinationPrefix(prefix)
                .maxUserTasks(TASK_LIMIT)
                .build();
    }

    private DownloadArchiveVO request(final String archiveName) {
        return DownloadArchiveVO.builder()
                .paths(List.of("data/"))
                .archiveName(archiveName)
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
