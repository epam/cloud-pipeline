/*
 * Copyright 2024 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.pipeline;

import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.entity.datastorage.DataStorageStreamingContent;
import com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage;
import com.epam.pipeline.entity.pipeline.run.runtime.RunSyncRuntimeDataConfig;
import com.epam.pipeline.entity.pipeline.run.runtime.RunSyncRuntimeDataConfigEntry;
import com.epam.pipeline.entity.pipeline.run.runtime.RunSyncRuntimeDataType;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import com.epam.pipeline.manager.pipeline.runtime.PipelineRunNextflowTraceDataExtractor;
import com.epam.pipeline.manager.pipeline.runtime.PipelineRunRuntimeDataExtractor;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import org.apache.commons.lang.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.security.access.AccessDeniedException;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import static com.epam.pipeline.util.CustomAssertions.assertThrows;


public class PipelineRunRuntimeDataManagerUnitTest {

    public static final long STORAGE_ID = 1L;
    public static final long RUN_ID = STORAGE_ID;
    public static final int SYNC_TIMEOUT = 60;
    public static final String S3_TRACE_FILE_FULL_PATH = "runfolder/1/prefix/trace.txt";
    public static final String TRACE_TXT = "trace.txt";
    public static final String S3_STORAGE_PATH = "s3://storage";
    public static final String STORAGE_PATH = "storage";
    public static final String DATA_PATH_PREFIX = "prefix";
    public static final String FOLDER_STORAGE_PREFIX = "/runfolder";

    @Mock
    private PreferenceManager preferenceManager;

    @Mock
    private DataStorageManager storageManager;

    private final List<PipelineRunRuntimeDataExtractor> dataExtractors = Collections.singletonList(
            new PipelineRunNextflowTraceDataExtractor(new JsonMapper())
    );

    @BeforeEach    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void getPipelineRunRuntimeDataShouldFailIfPreferenceIsNotConfiguredTest() {
        final PipelineRunRuntimeDataManager runRuntimeDataManager = new PipelineRunRuntimeDataManager(
                preferenceManager, storageManager, dataExtractors);

        Mockito.when(preferenceManager.getPreference(Mockito.eq(SystemPreferences.LAUNCH_RUN_SYNC_RUNTIME_DATA)))
                .thenReturn(new RunSyncRuntimeDataConfig(SYNC_TIMEOUT, null));
        assertThrows(IllegalArgumentException.class, () -> runRuntimeDataManager.getPipelineRunRuntimeData(RUN_ID,
            RunSyncRuntimeDataType.NF_TRACE, null));
    }

    @Test//(expected = .class)
    public void getPipelineRunRuntimeDataShouldFailIfExtractorDoesntDefinedTest() {
        final PipelineRunRuntimeDataManager runRuntimeDataManager = new PipelineRunRuntimeDataManager(
                preferenceManager, storageManager, Collections.emptyList());
        Mockito.when(preferenceManager.getPreference(Mockito.eq(SystemPreferences.LAUNCH_RUN_SYNC_RUNTIME_DATA)))
            .thenReturn(new RunSyncRuntimeDataConfig(SYNC_TIMEOUT,
                Collections.singletonMap(
                    RunSyncRuntimeDataType.NF_TRACE,
                    new RunSyncRuntimeDataConfigEntry(S3_STORAGE_PATH + FOLDER_STORAGE_PREFIX, DATA_PATH_PREFIX)))
            );
        assertThrows(IllegalArgumentException.class, () -> runRuntimeDataManager.getPipelineRunRuntimeData(RUN_ID,
            RunSyncRuntimeDataType.NF_TRACE, null));
    }

    @Test
    public void getPipelineRunRuntimeDataShouldCallExtractorAndStorageManagerWithRightDataTest() {
        Mockito.when(preferenceManager.getPreference(Mockito.eq(SystemPreferences.LAUNCH_RUN_SYNC_RUNTIME_DATA)))
            .thenReturn(new RunSyncRuntimeDataConfig(SYNC_TIMEOUT,
                Collections.singletonMap(
                    RunSyncRuntimeDataType.NF_TRACE,
                    new RunSyncRuntimeDataConfigEntry(S3_STORAGE_PATH + FOLDER_STORAGE_PREFIX, DATA_PATH_PREFIX)))
            );
        DataStorageManager dataStorageManagerMock = Mockito.mock(DataStorageManager.class);
        S3bucketDataStorage storage = new S3bucketDataStorage();
        storage.setId(STORAGE_ID);
        storage.setPath(STORAGE_PATH);

        Mockito.when(dataStorageManagerMock.loadByPathOrId(Mockito.eq(STORAGE_PATH + FOLDER_STORAGE_PREFIX)))
                .thenReturn(storage);
        Mockito.when(dataStorageManagerMock.getStreamingContent(
            Mockito.eq(STORAGE_ID), Mockito.eq(S3_TRACE_FILE_FULL_PATH), Mockito.eq(null))
        ).thenReturn(
            new DataStorageStreamingContent(
                new ByteArrayInputStream(StringUtils.EMPTY.getBytes(StandardCharsets.UTF_8)), TRACE_TXT
            )
        );

        PipelineRunNextflowTraceDataExtractor extractorMock = Mockito.mock(PipelineRunNextflowTraceDataExtractor.class);
        Mockito.when(extractorMock.getDataType()).thenReturn(RunSyncRuntimeDataType.NF_TRACE);
        Mockito.when(extractorMock.getDataFilePath(Mockito.any())).thenReturn(TRACE_TXT);

        final PipelineRunRuntimeDataManager runRuntimeDataManager = new PipelineRunRuntimeDataManager(
                preferenceManager, dataStorageManagerMock, Collections.singletonList(extractorMock));
        runRuntimeDataManager.getPipelineRunRuntimeData(RUN_ID, RunSyncRuntimeDataType.NF_TRACE, null);

        Mockito.verify(extractorMock, Mockito.times(1)).getDataType();
        Mockito.verify(dataStorageManagerMock, Mockito.times(1))
                .loadByPathOrId(Mockito.eq(STORAGE_PATH + FOLDER_STORAGE_PREFIX));

        Mockito.verify(dataStorageManagerMock, Mockito.times(1))
                .getStreamingContent(Mockito.eq(STORAGE_ID),
                        Mockito.eq(S3_TRACE_FILE_FULL_PATH), Mockito.eq(null));

    }
}
