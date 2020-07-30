/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.dts.transfer.service;

import com.epam.pipeline.dts.transfer.model.StorageItem;
import com.epam.pipeline.dts.transfer.model.StorageType;
import com.epam.pipeline.dts.transfer.model.TransferTask;
import com.epam.pipeline.dts.transfer.model.google.GoogleCredentials;
import com.epam.pipeline.dts.transfer.model.google.GoogleTransferInfo;
import com.epam.pipeline.dts.transfer.service.impl.GSJavaClientDataUploader;
import com.epam.pipeline.dts.transfer.service.impl.GoogleStorageClient;
import org.apache.commons.io.FilenameUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class GSJavaClientDataUploaderTest extends AbstractTransferTest {

    private static final String PATH_TO_FILE = "/path/to/file";
    private static final String PATH_TO_FOLDER = "/path/to/folder";
    private static final String GS_BUCKET_PATH_TO_FOLDER = "gs://bucket/path/to/folder";
    private static final String GS_BUCKET_PATH_TO_FILE = "gs://bucket/path/to/file";
    private static final String BUCKET = "bucket";
    private static final String PATH_TO_FOLDER_RELATIVE = "path/to/folder";

    private final GoogleStorageClient googleStorageClient = mock(GoogleStorageClient.class);
    private final DataUploader dataUploader = new GSJavaClientDataUploader(googleStorageClient);
    private final Path existingFolderPath = createTempFolder("dts-gs-data-uploader-test-folder");
    private final Path existingFilePath = createTempFile("dts-gs-data-uploader-test-file");

    @AfterEach
    void tearDown() {
        existingFolderPath.toFile().delete();
        existingFilePath.toFile().delete();
    }

    @Test
    void getStorageTypeShouldReturnS3() {
        assertThat(dataUploader.getStorageType(), is(StorageType.GS));
    }

    @Test
    void transferShouldFailIfSourceStorageTypeIsUnsupported() {
        final TransferTask task = taskOf(s3Item(), nonExistingLocalItem());

        assertThrows(RuntimeException.class, () -> dataUploader.transfer(task));
    }

    @Test
    void transferShouldFailIfDestinationStorageTypeIsUnsupported() {
        final TransferTask task = taskOf(nonExistingLocalItem(), s3Item());

        assertThrows(RuntimeException.class, () -> dataUploader.transfer(task));
    }

    @Test
    void transferShouldFailIfLocalSourceFileDoesNotExist() {
        final TransferTask task = taskOf(nonExistingLocalItem(), gsItem());

        assertThrows(RuntimeException.class, () -> dataUploader.transfer(task));
    }

    @Test
    void transferShouldFailIfGsSourcePathDoesNotStartWithGsPrefix() {
        final TransferTask task = taskOf(invalidGsItem(), nonExistingLocalItem());

        assertThrows(RuntimeException.class, () -> dataUploader.transfer(task));
    }

    @Test
    void transferShouldFailIfGsDestinationPathDoesNotStartWithGsPrefix() {
        final TransferTask task = taskOf(nonExistingLocalItem(), invalidGsItem());

        assertThrows(RuntimeException.class, () -> dataUploader.transfer(task));
    }

    @Test
    void transferShouldUploadFolder() {
        final StorageItem source = localFolderItem();
        final StorageItem destination = gsItem();

        dataUploader.transfer(taskOf(source, destination));

        final GoogleTransferInfo transferInfo = GoogleTransferInfo.builder()
            .source(source.getPath())
            .destination(destination.getPath())
            .credentials(GoogleCredentials.from(destination.getCredentials()))
            .build();
        verify(googleStorageClient).uploadDirectory(eq(transferInfo));
    }

    @Test
    void transferShouldUploadFileToFileDestination() {
        final StorageItem source = localFileItem();
        final StorageItem destination = gsItem();

        dataUploader.transfer(taskOf(source, destination));

        final GoogleTransferInfo transferInfo = GoogleTransferInfo.builder()
            .source(source.getPath())
            .destination(destination.getPath())
            .credentials(GoogleCredentials.from(destination.getCredentials()))
            .build();
        verify(googleStorageClient).uploadFile(eq(transferInfo));
    }

    @Test
    void transferShouldUploadFileToFolderDestination() {
        final StorageItem source = localFileItem();
        final StorageItem destination = gsFolderItem();

        dataUploader.transfer(taskOf(source, destination));

        final GoogleTransferInfo transferInfo = GoogleTransferInfo.builder()
            .source(source.getPath())
            .destination(destination.getPath() + FilenameUtils.getName(source.getPath()))
            .credentials(GoogleCredentials.from(destination.getCredentials()))
            .build();
        verify(googleStorageClient).uploadFile(eq(transferInfo));
    }

    @Test
    void transferShouldDownloadFolder() {
        final StorageItem source = gsItem(GS_BUCKET_PATH_TO_FOLDER);
        final StorageItem destination = localItem(PATH_TO_FOLDER);
        when(googleStorageClient.isFile(any())).thenReturn(false);
        when(googleStorageClient.isDirectory(any())).thenReturn(true);

        dataUploader.transfer(taskOf(source, destination));

        final GoogleTransferInfo transferInfo = GoogleTransferInfo.builder()
            .source(PATH_TO_FOLDER_RELATIVE)
            .bucketName(BUCKET)
            .key(PATH_TO_FOLDER_RELATIVE)
            .destination(PATH_TO_FOLDER)
            .credentials(GoogleCredentials.from(source.getCredentials()))
            .build();
        verify(googleStorageClient).downloadDirectory(eq(transferInfo));
    }

    @Test
    void transferShouldDownloadFolderEvenIfDestinationPathHasExtraDelimiterAtTheEnd() {
        final StorageItem source = gsItem(GS_BUCKET_PATH_TO_FOLDER);
        final StorageItem destination = localItem("/path/to/folder/");
        when(googleStorageClient.isFile(any())).thenReturn(false);
        when(googleStorageClient.isDirectory(any())).thenReturn(true);

        dataUploader.transfer(taskOf(source, destination));

        final GoogleTransferInfo transferInfo = GoogleTransferInfo.builder()
            .source(PATH_TO_FOLDER_RELATIVE)
            .bucketName(BUCKET)
            .key(PATH_TO_FOLDER_RELATIVE)
            .destination(PATH_TO_FOLDER)
            .credentials(GoogleCredentials.from(source.getCredentials()))
            .build();
        verify(googleStorageClient).downloadDirectory(eq(transferInfo));
    }

    @Test
    void transferShouldDownloadFile() {
        final StorageItem source = gsItem(GS_BUCKET_PATH_TO_FILE);
        final StorageItem destination = localItem(PATH_TO_FILE);
        when(googleStorageClient.isFile(any())).thenReturn(true);
        when(googleStorageClient.isDirectory(any())).thenReturn(false);

        dataUploader.transfer(taskOf(source, destination));

        final GoogleTransferInfo transferInfo = GoogleTransferInfo.builder()
            .source(GS_BUCKET_PATH_TO_FILE)
            .bucketName(BUCKET)
            .key("path/to/file")
            .destination(PATH_TO_FILE)
            .credentials(GoogleCredentials.from(source.getCredentials()))
            .build();
        verify(googleStorageClient).downloadFile(eq(transferInfo));
    }

    private StorageItem localFolderItem() {
        return localItem(existingFolderPath.toString());
    }

    private StorageItem localFileItem() {
        return localItem(existingFilePath.toString());
    }
}
