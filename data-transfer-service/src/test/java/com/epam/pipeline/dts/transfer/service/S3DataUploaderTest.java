/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.cmd.PipelineCLI;
import com.epam.pipeline.dts.transfer.model.StorageItem;
import com.epam.pipeline.dts.transfer.model.StorageType;
import com.epam.pipeline.dts.transfer.model.TransferTask;
import com.epam.pipeline.dts.transfer.service.impl.S3DataUploader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class S3DataUploaderTest extends AbstractTransferTest {

    private static final String API = "api";
    private static final String API_TOKEN = "apiToken";
    private final Path existingLocalPath = createTempFile("dts-s3-data-uploader-test");

    private final PipelineCliProvider pipelineCliProvider = mock(PipelineCliProvider.class);
    private final PipelineCLI pipelineCli = mock(PipelineCLI.class);
    private final DataUploader dataUploader = new S3DataUploader(pipelineCliProvider);

    @AfterEach
    void tearDown() {
        deleteFile(existingLocalPath);
    }

    @BeforeEach
    void setUp() {
        when(pipelineCliProvider.getPipelineCLI(API, API_TOKEN)).thenReturn(pipelineCli);
    }

    @Test
    void getStorageTypeShouldReturnS3() {
        assertThat(dataUploader.getStorageType(), is(StorageType.S3));
    }

    @Test
    void transferShouldFailIfSourceStorageTypeIsUnsupported() {
        final TransferTask transferTask = taskOf(gsItem(), nonExistingLocalItem());

        assertThrows(RuntimeException.class, () -> dataUploader.transfer(transferTask, false, null, null));
    }

    @Test
    void transferShouldFailIfDestinationStorageTypeIsUnsupported() {
        final TransferTask transferTask = taskOf(nonExistingLocalItem(), gsItem());

        assertThrows(RuntimeException.class, () -> dataUploader.transfer(transferTask, false, "", ""));
    }

    @Test
    void transferShouldNotFailIfLocalDestinationFileDoesNotExist() {
        final TransferTask transferTask = taskOf(s3Item(), nonExistingLocalItem());

        assertDoesNotThrow(() -> dataUploader.transfer(transferTask, false, null, null));
    }

    @Test
    void transferShouldFailIfS3SourcePathDoesNotStartWithS3Prefix() {
        final TransferTask transferTask = taskOf(invalidS3Item(), nonExistingLocalItem());

        assertThrows(RuntimeException.class, () -> dataUploader.transfer(transferTask, false, "", ""));
    }

    @Test
    void transferShouldFailIfS3DestinationPathDoesNotStartWithS3Prefix() {
        final TransferTask transferTask = taskOf(nonExistingLocalItem(), invalidS3Item());

        assertThrows(RuntimeException.class, () -> dataUploader.transfer(transferTask, false, null, null));
    }

    @Test
    void transferShouldCallPipelineCliUploadIfSourceIsLocalAndDestinationIsS3() {
        final StorageItem source = existingLocalItem();
        final StorageItem destination = s3Item();

        dataUploader.transfer(taskOf(source, destination), false, null, null);

        verify(pipelineCli).uploadData(eq(source.getPath()), eq(destination.getPath()), any(),
                isNull(), eq(false), eq(false), isNull(), isNull());
    }

    @Test
    void transferShouldCallPipelineCliDownloadIfSourceIsS3AndDestinationIsLocal() {
        final StorageItem source = s3Item();
        final StorageItem destination = nonExistingLocalItem();

        dataUploader.transfer(taskOf(source, destination), false, null, null);

        verify(pipelineCli).downloadData(eq(source.getPath()), eq(destination.getPath()), any(),
                isNull(), eq(false), eq(false), isNull(), isNull());
    }

    @Test
    void transferShouldRetrievePipelineCliByCredentialsFromS3SourceIfDestinationIsLocal() {
        dataUploader.transfer(taskOf(s3Item(), nonExistingLocalItem()));

        verify(pipelineCliProvider).getPipelineCLI(eq(API), eq(API_TOKEN));
    }

    @Test
    void transferShouldRetrievePipelineCliByCredentialsFromS3DestinationIfSourceIsLocal() {
        dataUploader.transfer(taskOf(existingLocalItem(), s3Item()));

        verify(pipelineCliProvider).getPipelineCLI(eq(API), eq(API_TOKEN));
    }

    @Test
    void transferShouldCallPipelineCliWithSourceAndDestinationPaths() {
        final StorageItem source = existingLocalItem();
        final StorageItem destination = s3Item();

        dataUploader.transfer(taskOf(source, destination));

        verify(pipelineCli).uploadData(eq(source.getPath()), eq(destination.getPath()), any(),
                isNull(), eq(false), eq(false), isNull(), isNull());
    }

    @Test
    void transferShouldCallPipelineCliWithIncludeParameterFromTransferTask() {
        final StorageItem source = existingLocalItem();
        final StorageItem destination = s3Item();
        final List<String> included = Arrays.asList("a", "b", "c");
        final String username = "manager";
        final TransferTask transferTask = TransferTask.builder()
            .source(source)
            .destination(destination)
            .included(included)
            .user(username)
            .build();

        dataUploader.transfer(transferTask, false, null, null);

        verify(pipelineCli).uploadData(eq(source.getPath()), eq(destination.getPath()), eq(included), eq(username),
                                       eq(false), eq(false), isNull(), isNull());
    }

    private StorageItem existingLocalItem() {
        return localItem(existingLocalPath.toString());
    }

}
