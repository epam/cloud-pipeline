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
import com.epam.pipeline.dto.async.AsyncTaskType;
import com.epam.pipeline.dto.async.UnpackArchiveParams;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import com.epam.pipeline.manager.security.storage.StoragePermissionManager;
import com.epam.pipeline.security.acl.AclPermission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class UnpackArchiveTaskProcessorTest {

    private static final Long SOURCE_ID = 1L;
    private static final Long DESTINATION_ID = 2L;
    private static final String ARCHIVE_PATH = "incoming/data.zip";
    private static final String DESTINATION_PATH = "extracted/data";
    private static final String OWNER = "USER";

    private final DataStorageManager dataStorageManager = Mockito.mock(DataStorageManager.class);
    private final StoragePermissionManager storagePermissionManager =
            Mockito.mock(StoragePermissionManager.class);
    private final MessageHelper messageHelper = Mockito.mock(MessageHelper.class);

    private final AsyncTaskParamsValidator validator =
            new AsyncTaskParamsValidator(storagePermissionManager, messageHelper);
    private final UnpackArchiveTaskProcessor processor =
            new UnpackArchiveTaskProcessor(dataStorageManager, validator, messageHelper);

    @BeforeEach
    public void setUp() {
        Mockito.when(storagePermissionManager.hasStoragePermission(Mockito.any(), Mockito.any()))
                .thenReturn(true);
        Mockito.when(dataStorageManager.load(SOURCE_ID)).thenReturn(storage(SOURCE_ID));
        Mockito.when(dataStorageManager.load(DESTINATION_ID)).thenReturn(storage(DESTINATION_ID));
    }

    @Test
    public void shouldAnswerForTheUnpackType() {
        assertEquals(AsyncTaskType.UNPACK_ARCHIVE, processor.getType());
    }

    @Test
    public void shouldReturnTheDestinationAsAKeyPrefix() {
        final UnpackArchiveParams prepared = (UnpackArchiveParams) processor.prepare(request().build());

        assertEquals(DESTINATION_PATH + "/", prepared.getDestinationPath());
        assertEquals(ARCHIVE_PATH, prepared.getSourcePath());
    }

    @Test
    public void shouldReturnAnEmptyPrefixForTheRoot() {
        final UnpackArchiveParams prepared =
                (UnpackArchiveParams) processor.prepare(request().destinationPath("").build());

        assertEquals("", prepared.getDestinationPath());
    }

    @Test
    public void shouldRefuseASlashAsTheDestinationPath() {
        assertThrows(() -> processor.prepare(request().destinationPath("/").build()));
    }

    @Test
    public void shouldStillRefuseAnAbsentDestinationPath() {
        assertThrows(() -> processor.prepare(request().destinationPath(null).build()));
    }

    @Test
    public void shouldCarryTheFlagsThroughUnchanged() {
        final UnpackArchiveParams prepared = (UnpackArchiveParams) processor.prepare(
                request().deleteSourceOnSuccess(true).overwrite(true).build());

        assertEquals(true, prepared.isDeleteSourceOnSuccess());
        assertEquals(true, prepared.isOverwrite());
    }

    @Test
    public void shouldRefuseARequestMissingEitherEnd() {
        assertThrows(() -> processor.prepare(request().sourceStorageId(null).build()));
        assertThrows(() -> processor.prepare(request().destinationStorageId(null).build()));
        assertThrows(() -> processor.prepare(request().sourcePath(null).build()));
        assertThrows(() -> processor.prepare(request().sourcePath("  ").build()));

        Mockito.verify(dataStorageManager, Mockito.never()).load(Mockito.anyLong());
    }

    @Test
    public void shouldRequireReadOnTheSourceStorage() {
        Mockito.when(storagePermissionManager.hasStoragePermission(SOURCE_ID, AclPermission.READ_NAME))
                .thenReturn(false);

        assertThrows(() -> processor.prepare(request().build()));
    }

    @Test
    public void shouldRequireWriteOnTheDestinationStorage() {
        Mockito.when(storagePermissionManager
                .hasStoragePermission(DESTINATION_ID, AclPermission.WRITE_NAME)).thenReturn(false);

        assertThrows(() -> processor.prepare(request().build()));
    }

    @Test
    public void shouldRequireWriteOnTheSourceStorageToDeleteTheArchive() {
        Mockito.when(storagePermissionManager.hasStoragePermission(SOURCE_ID, AclPermission.WRITE_NAME))
                .thenReturn(false);

        processor.prepare(request().build());

        assertThrows(() -> processor.prepare(request().deleteSourceOnSuccess(true).build()));
    }

    @Test
    public void shouldCheckReadOnTheArchivePathAndWriteOnTheDestinationFolder() {
        processor.prepare(request().build());

        Mockito.verify(dataStorageManager).checkReadPermissionsOnPaths(
                storageWithId(SOURCE_ID), Mockito.eq(List.of(ARCHIVE_PATH)));
        Mockito.verify(dataStorageManager).checkWritePermissionsOnFolder(
                storageWithId(DESTINATION_ID), Mockito.eq(DESTINATION_PATH + "/"));
        Mockito.verify(dataStorageManager, Mockito.never()).checkWritePermissionsOnFile(
                Mockito.any(), Mockito.any());
    }

    @Test
    public void shouldCheckWriteOnTheArchivePathOnlyWhenItIsToBeDeleted() {
        processor.prepare(request().deleteSourceOnSuccess(true).build());

        Mockito.verify(dataStorageManager).checkWritePermissionsOnFile(
                storageWithId(SOURCE_ID), Mockito.eq(ARCHIVE_PATH));
    }

    @Test
    public void shouldAllowTheSourceStorageToBeTheDestination() {
        final UnpackArchiveParams prepared = (UnpackArchiveParams) processor.prepare(
                request().destinationStorageId(SOURCE_ID).build());

        assertEquals(SOURCE_ID, prepared.getSourceStorageId());
        assertEquals(SOURCE_ID, prepared.getDestinationStorageId());
    }

    @Test
    public void shouldRefuseToHandOverAnythingToConsume() {
        assertThrows(UnsupportedOperationException.class, () -> processor.consume(AsyncTask.builder()
                .id("4f1a2c90-0000-4000-8000-000000000001")
                .type(AsyncTaskType.UNPACK_ARCHIVE)
                .owner(OWNER)
                .build()));
    }

    private UnpackArchiveParams.UnpackArchiveParamsBuilder request() {
        return UnpackArchiveParams.builder()
                .sourceStorageId(SOURCE_ID)
                .sourcePath(ARCHIVE_PATH)
                .destinationStorageId(DESTINATION_ID)
                .destinationPath(DESTINATION_PATH);
    }

    private AbstractDataStorage storage(final Long id) {
        final S3bucketDataStorage storage = new S3bucketDataStorage(id, "storage-" + id, "bucket" + id);
        storage.setOwner(OWNER);
        return storage;
    }

    private AbstractDataStorage storageWithId(final Long id) {
        return Mockito.argThat(storage -> storage != null && id.equals(storage.getId()));
    }
}
