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
import com.epam.pipeline.dto.async.AsyncTask;
import com.epam.pipeline.dto.async.AsyncTaskStatus;
import com.epam.pipeline.dto.async.AsyncTaskType;
import com.epam.pipeline.dto.async.DownloadArchiveConsumable;
import com.epam.pipeline.dto.async.DownloadArchiveParams;
import com.epam.pipeline.dto.async.DownloadArchiveResult;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.ArchiveTasksConfig;
import com.epam.pipeline.entity.datastorage.ContentDisposition;
import com.epam.pipeline.entity.datastorage.DataStorageDownloadFileUrl;
import com.epam.pipeline.entity.datastorage.DownloadArchiveConfig;
import com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import com.epam.pipeline.manager.datastorage.StorageProviderManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.security.storage.StoragePermissionManager;
import com.epam.pipeline.security.acl.AclPermission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class DownloadArchiveTaskProcessorTest {

    private static final Long STORAGE_ID = 1L;
    private static final Long ARCHIVE_STORAGE_ID = 9L;
    private static final Long RECORDED_STORAGE_ID = 7L;
    private static final String TASK_ID = "4f1a2c90-0000-4000-8000-000000000001";
    private static final String OWNER = "USER";
    private static final String URL = "https://bucket.s3.amazonaws.com/archives/selection.zip";

    private final DataStorageManager dataStorageManager = Mockito.mock(DataStorageManager.class);
    private final StoragePermissionManager storagePermissionManager =
            Mockito.mock(StoragePermissionManager.class);
    private final MessageHelper messageHelper = Mockito.mock(MessageHelper.class);
    private final PreferenceManager preferenceManager = Mockito.mock(PreferenceManager.class);
    private final StorageProviderManager storageProviderManager = Mockito.mock(StorageProviderManager.class);

    private final AsyncTaskParamsValidator validator =
            new AsyncTaskParamsValidator(storagePermissionManager, messageHelper);
    private final DownloadArchiveTaskProcessor processor = new DownloadArchiveTaskProcessor(
            dataStorageManager, validator, messageHelper, preferenceManager, storageProviderManager);

    @BeforeEach
    public void setUp() {
        Mockito.when(storagePermissionManager.hasStoragePermission(Mockito.any(), Mockito.any()))
                .thenReturn(true);
        Mockito.when(dataStorageManager.load(Mockito.anyLong())).thenReturn(storage(STORAGE_ID));
        Mockito.when(preferenceManager.getPreference(SystemPreferences.DATA_STORAGE_ARCHIVE_CONFIG))
                .thenReturn(config());
    }

    @Test
    public void shouldAnswerForTheDownloadType() {
        assertEquals(AsyncTaskType.DOWNLOAD_ARCHIVE, processor.getType());
    }

    @Test
    public void shouldPassTheSelectionThroughUnchanged() {
        final DownloadArchiveParams prepared = (DownloadArchiveParams) processor.prepare(request("sel.zip"));

        assertEquals(STORAGE_ID, prepared.getStorageId());
        assertEquals(List.of("data/"), prepared.getPaths());
        assertEquals("sel.zip", prepared.getArchiveName());
    }

    @Test
    public void shouldDefaultTheArchiveNameWhenNoneWasAsked() {
        final DownloadArchiveParams prepared = (DownloadArchiveParams) processor.prepare(request(null));

        assertEquals("archive.zip", prepared.getArchiveName());
    }

    @Test
    public void shouldRefuseAnArchiveNameWithUnsafeCharacters() {
        assertThrows(() -> processor.prepare(request("../evil.zip")));
        assertThrows(() -> processor.prepare(request("a/b.zip")));
    }

    @Test
    public void shouldRefuseARequestWithoutAStorageOrPaths() {
        assertThrows(() -> processor.prepare(DownloadArchiveParams.builder().paths(List.of("data/")).build()));
        assertThrows(() -> processor.prepare(DownloadArchiveParams.builder().storageId(STORAGE_ID).build()));
        assertThrows(() -> processor.prepare(DownloadArchiveParams.builder()
                .storageId(STORAGE_ID)
                .paths(List.of())
                .build()));
    }

    @Test
    public void shouldRequireReadOnTheStorage() {
        Mockito.when(storagePermissionManager.hasStoragePermission(STORAGE_ID, AclPermission.READ_NAME))
                .thenReturn(false);

        assertThrows(() -> processor.prepare(request("sel.zip")));
    }

    @Test
    public void shouldCheckReadOnTheSelectedPaths() {
        processor.prepare(request("sel.zip"));

        Mockito.verify(dataStorageManager).checkReadPermissionsOnPaths(Mockito.any(),
                Mockito.eq(List.of("data/")));
    }

    @Test
    public void shouldHandOverASignedUrlOnceTheArchiveIsReady() {
        Mockito.when(storageProviderManager.generateDownloadURL(Mockito.any(), Mockito.any(),
                Mockito.isNull(), Mockito.eq(ContentDisposition.ATTACHMENT), Mockito.any()))
                .thenReturn(signedUrl());

        final DownloadArchiveConsumable consumable =
                (DownloadArchiveConsumable) processor.consume(task(AsyncTaskStatus.SUCCESS));

        assertEquals(URL, consumable.getUrl());
    }

    @Test
    public void shouldRefuseToHandOverAnArchiveThatIsNotReady() {
        for (final AsyncTaskStatus status : List.of(AsyncTaskStatus.PENDING, AsyncTaskStatus.RUNNING,
                AsyncTaskStatus.FAILED, AsyncTaskStatus.EXPIRED)) {
            assertThrows(() -> processor.consume(task(status)));
        }
    }

    @Test
    public void shouldRefuseToHandOverWhenNoArchiveStorageIsConfigured() {
        Mockito.when(preferenceManager.getPreference(SystemPreferences.DATA_STORAGE_ARCHIVE_CONFIG))
                .thenReturn(ArchiveTasksConfig.builder().build());

        assertThrows(() -> processor.consume(task(AsyncTaskStatus.SUCCESS)));
    }

    @Test
    public void shouldDeriveTheArchiveKeyUnderTheConfiguredPrefix() {
        Mockito.when(storageProviderManager.generateDownloadURL(Mockito.any(), Mockito.any(),
                Mockito.isNull(), Mockito.any(), Mockito.any())).thenReturn(signedUrl());

        processor.consume(task(AsyncTaskStatus.SUCCESS));

        Mockito.verify(storageProviderManager).generateDownloadURL(Mockito.any(),
                Mockito.eq("archives/" + TASK_ID + ".zip"), Mockito.isNull(), Mockito.any(),
                Mockito.eq("archive.zip"));
    }

    @Test
    public void shouldOfferTheRequestedNameWithTheArchiveExtension() {
        // What the browser saves the download as. The name a caller asked for is kept, and the
        // extension added where it is missing, so the file still arrives as a zip.
        final AsyncTask task = task(AsyncTaskStatus.SUCCESS);
        task.setParams(DownloadArchiveParams.builder()
                .storageId(STORAGE_ID)
                .paths(List.of("data/"))
                .archiveName("selection")
                .build());
        Mockito.when(storageProviderManager.generateDownloadURL(Mockito.any(), Mockito.any(),
                Mockito.isNull(), Mockito.any(), Mockito.any())).thenReturn(signedUrl());

        processor.consume(task);

        Mockito.verify(storageProviderManager).generateDownloadURL(Mockito.any(), Mockito.any(),
                Mockito.isNull(), Mockito.any(), Mockito.eq("selection.zip"));
    }

    @Test
    public void shouldTakeTheArchiveFromTheStorageTheTaskRecorded() {
        // Where the archive actually is: the executor reports the storage it wrote to, and the
        // configured destination is only the fallback for a task that reported none. Reading the
        // configured one regardless would sign a link to a key that is not there.
        final AsyncTask task = task(AsyncTaskStatus.SUCCESS);
        task.setResult(DownloadArchiveResult.builder()
                .storageId(RECORDED_STORAGE_ID)
                .path("archives/" + TASK_ID + ".zip")
                .build());
        Mockito.when(storageProviderManager.generateDownloadURL(Mockito.any(), Mockito.any(),
                Mockito.isNull(), Mockito.any(), Mockito.any())).thenReturn(signedUrl());

        processor.consume(task);

        Mockito.verify(dataStorageManager).load(RECORDED_STORAGE_ID);
        Mockito.verify(dataStorageManager, Mockito.never()).load(ARCHIVE_STORAGE_ID);
    }

    @Test
    public void shouldRefuseARecordedKeyThatDoesNotBelongToTheTask() {
        final AsyncTask task = task(AsyncTaskStatus.SUCCESS);
        task.setResult(DownloadArchiveResult.builder()
                .storageId(ARCHIVE_STORAGE_ID)
                .path("archives/someone-elses-task.zip")
                .build());

        assertThrows(() -> processor.consume(task));
    }

    private DownloadArchiveParams request(final String archiveName) {
        return DownloadArchiveParams.builder()
                .storageId(STORAGE_ID)
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

    private ArchiveTasksConfig config() {
        return ArchiveTasksConfig.builder()
                .tasks(ArchiveTasksConfig.Tasks.builder()
                        .download(DownloadArchiveConfig.builder()
                                .destinationStorageId(ARCHIVE_STORAGE_ID)
                                .build())
                        .build())
                .build();
    }

    private DataStorageDownloadFileUrl signedUrl() {
        final DataStorageDownloadFileUrl url = new DataStorageDownloadFileUrl();
        url.setUrl(URL);
        return url;
    }

    private AbstractDataStorage storage(final Long id) {
        final S3bucketDataStorage storage = new S3bucketDataStorage(id, "storage-" + id, "bucket" + id);
        storage.setOwner(OWNER);
        return storage;
    }
}
