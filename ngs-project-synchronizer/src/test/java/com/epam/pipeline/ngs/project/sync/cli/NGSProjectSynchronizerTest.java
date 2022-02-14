/*
 * Copyright 2022 EPAM Systems, Inc. (https://www.epam.com/)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.ngs.project.sync.cli;

import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.DataStorageFolder;
import com.epam.pipeline.entity.datastorage.S3bucketDataStorage;
import com.epam.pipeline.entity.metadata.FolderWithMetadata;
import com.epam.pipeline.entity.metadata.MetadataClass;
import com.epam.pipeline.entity.metadata.MetadataEntity;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.entity.preference.Preference;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.entity.utils.ProviderUtils;
import com.epam.pipeline.ngs.project.sync.api.client.CloudPipelineAPIClient;
import com.epam.pipeline.utils.SystemPreferenceUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Optional;

import static com.epam.pipeline.ngs.project.sync.cli.NGSProjectSynchronizer.NGS_PREPROCESSING_COMPLETION_MARK_METADATA_KEY_PREF;
import static com.epam.pipeline.ngs.project.sync.cli.NGSProjectSynchronizer.NGS_PREPROCESSING_COMPLETION_MARK_NAME_PREF;
import static com.epam.pipeline.ngs.project.sync.cli.NGSProjectSynchronizer.NGS_PREPROCESSING_DATA_FOLDER_PREF;
import static com.epam.pipeline.ngs.project.sync.cli.NGSProjectSynchronizer.NGS_PREPROCESSING_MACHINE_RUN_METADATA_CLASS_PREF;
import static com.epam.pipeline.ngs.project.sync.cli.NGSProjectSynchronizer.NGS_PREPROCESSING_SAMPLESHEET_FILE_NAME_PREF;
import static com.epam.pipeline.ngs.project.sync.cli.NGSProjectSynchronizer.NGS_PREPROCESSING_SAMPLESHEET_LINK_COLUMN_PREF;
import static com.epam.pipeline.ngs.project.sync.cli.NGSProjectSynchronizer.NGS_PREPROCESSING_SAMPLE_METADATA_CLASS_PREF;
import static com.epam.pipeline.ngs.project.sync.cli.NGSProjectSynchronizer.UI_NGS_PROJECT_INDICATOR_PREF;

class NGSProjectSynchronizerTest {

    public static final byte[] SAMPLE_SHEET_CONTENT = Base64.getDecoder().decode(
            "W0hlYWRlcl0KRGF0ZSwyMDE3LTA0LTE1CldvcmtmbG93LEN1c3RvbSBBbXBsaWNvbgpBcHBsaWNhdGlvbixUZXN0I" +
                    "EFtcGxpY29uCkFzc2F5LFRlc3QgQW1wbGljb24KRGVzY3JpcHRpb24sQ2hlbWlzdHJ5LEFtcGxpY29uCltNY" +
                    "W5pZmVzdHNdCkEsVGVzdEFtcGxpY29uTWFuaWZlc3QtMS50eHQKQixUZXN0QW1wbGljb25NYW5pZmVzdC0yL" +
                    "nR4dApbUmVhZHNdCjE1MQoxNTEKW1NldHRpbmdzXQpWYXJpYW50RmlsdGVyUXVhbGl0eUN1dG9mZiwzMApvd" +
                    "XRwdXRnZW5vbWV2Y2YsRkFMU0UKW0RhdGFdClNhbXBsZV9JRCxTYW1wbGVfTmFtZSxJN19JbmRleF9JRCxpb" +
                    "mRleCxJNV9JbmRleF9JRCxpbmRleDIsTWFuaWZlc3QsR2Vub21lRm9sZGVyCkExMDEwMDEsU2FtcGxlX0EsQ" +
                    "TcwMSxBVENBQ1RDR0FDLEE1MDEsVEdBQ1RBQ0NUVCxBLFRlc3RcV2hvbGVHZW5vbWVGYXN0YQpBMTAwMjAyL" +
                    "FNhbXBsZV9CLEE3MDIsQUNBR1RHQ1RHVCxBNTAxLFRHQUNUQUNDVFQsQSxUZXN0XFdob2xlR2Vub21lRmFzd" +
                    "GEKQTEwMDQwMyxTYW1wbGVfQyxBNzAzLENBR0FUQ1RDQ0EsQTUwMSxUR0FDVEFDQ1RULEIsVGVzdFxXaG9sZ" +
                    "Udlbm9tZUZhc3RhCkExMDAwdzQsU2FtcGxlX0QsQTcwNCxBQ0FBQUNHQ1RHLEE1MDEsVENUR0FBQ0NUVCxCL" +
                    "FRlc3RcV2hvbGVHZW5vbWVGYXN0YQo="
    );

    public static final long FOLDER_WITH_OUT_DATA_TAG_ID = 1L;
    public static final long NGS_PROJECT_ID = 2L;
    public static final String BUCKET = "bucket";
    public static final String S3 = "s3://";
    public static final String DATA_FOLDER_INTERNAL = "data";
    public static final String S3_BUCKET_DATA_PATH = S3 + BUCKET  + ProviderUtils.DELIMITER + DATA_FOLDER_INTERNAL;
    public static final String MACHINE_RUN_CLASS_NAME = "MachineRun";
    public static final String SAMPLE_CLASS_NAME = "Sample";
    public static final String MACHINE_RUN_NAME = "machine_run_1";
    public static final String RTACOMPLETE_TXT = "RTAComplete.txt";
    public static final String SAMPLESHEET_CSV = "samplesheet.csv";
    public static final String SAMPLE_SHEET_COLUMN = "Sample Sheet";
    public static final String STRING_TYPE = "string";
    public static final String UPDATED_DATE_COLUMN = "Updated Date";
    public static final String DATE_TYPE = "date";
    @Mock
    private CloudPipelineAPIClient client = Mockito.mock(CloudPipelineAPIClient.class);

    private NGSProjectSynchronizer synchronizer;

    @BeforeEach
    void setUp() {
        synchronizer = Mockito.spy(new NGSProjectSynchronizer(client));
        Mockito.doReturn(new Preference(NGS_PREPROCESSING_SAMPLESHEET_LINK_COLUMN_PREF, SAMPLE_SHEET_COLUMN))
                .when(client).getPreference(Mockito.eq(NGS_PREPROCESSING_SAMPLESHEET_LINK_COLUMN_PREF));
        Mockito.doReturn(new Preference(UI_NGS_PROJECT_INDICATOR_PREF, "type=project,project-type=ngs"))
                .when(client).getPreference(Mockito.eq(UI_NGS_PROJECT_INDICATOR_PREF));
        Mockito.doReturn(new Preference(NGS_PREPROCESSING_COMPLETION_MARK_METADATA_KEY_PREF, "complete-mark"))
                .when(client).getPreference(Mockito.eq(NGS_PREPROCESSING_COMPLETION_MARK_METADATA_KEY_PREF));
        Mockito.doReturn(new Preference(NGS_PREPROCESSING_COMPLETION_MARK_NAME_PREF, RTACOMPLETE_TXT))
                .when(client).getPreference(Mockito.eq(NGS_PREPROCESSING_COMPLETION_MARK_NAME_PREF));
        Mockito.doReturn(new Preference(NGS_PREPROCESSING_DATA_FOLDER_PREF, "ngs-data"))
                .when(client).getPreference(Mockito.eq(NGS_PREPROCESSING_DATA_FOLDER_PREF));
        Mockito.doReturn(new Preference(NGS_PREPROCESSING_SAMPLESHEET_FILE_NAME_PREF, SAMPLESHEET_CSV))
                .when(client).getPreference(Mockito.eq(NGS_PREPROCESSING_SAMPLESHEET_FILE_NAME_PREF));
        Mockito.doReturn(new Preference(NGS_PREPROCESSING_MACHINE_RUN_METADATA_CLASS_PREF, MACHINE_RUN_CLASS_NAME))
                .when(client).getPreference(Mockito.eq(NGS_PREPROCESSING_MACHINE_RUN_METADATA_CLASS_PREF));
        Mockito.doReturn(new Preference(NGS_PREPROCESSING_SAMPLE_METADATA_CLASS_PREF, SAMPLE_CLASS_NAME))
                .when(client).getPreference(Mockito.eq(NGS_PREPROCESSING_SAMPLE_METADATA_CLASS_PREF));
        Mockito.doReturn(new MetadataClass(1L, MACHINE_RUN_CLASS_NAME))
                .when(client).getMetadataClass(MACHINE_RUN_CLASS_NAME);
        Mockito.doReturn(new MetadataClass(2L, SAMPLE_CLASS_NAME))
                .when(client).getMetadataClass(SAMPLE_CLASS_NAME);
        synchronizer.init();
    }

    @Test
    void willSkipProjectWithoutNgsDataFolderTag() {
        mockNgsProject(FOLDER_WITH_OUT_DATA_TAG_ID);
        FolderWithMetadata withOutDataPathTag = new FolderWithMetadata();
        withOutDataPathTag.setData(Collections.emptyMap());
        Mockito.doReturn(withOutDataPathTag).when(client)
                .getFolderWithMetadata(Mockito.eq(FOLDER_WITH_OUT_DATA_TAG_ID));
        synchronizer.syncNgsProjectsState();
        Mockito.verify(client, Mockito.never()).registerSampleSheet(Mockito.any(), Mockito.anyBoolean());
    }

    @Test
    void willSkipProjectWithOutRTACompleteFile() {
        FolderWithMetadata project = mockValidProjectFolder();
        S3bucketDataStorage s3bucketDataStorage = mockStorage();
        mockMachineRunMetadata(project,
                mockMachineRunFolder(s3bucketDataStorage, DateUtils.now(), false, false),
                false, null);

        mockNgsProject(project.getId());

        synchronizer.syncNgsProjectsState();
        Mockito.verify(synchronizer, Mockito.times(1))
                .fetchDataSyncCompleteMarkFile(Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.verify(client, Mockito.never()).registerSampleSheet(Mockito.any(), Mockito.anyBoolean());
    }

    @Test
    void willRegisterProjectIfThereIsNoLinkedSampleSheet() {
        FolderWithMetadata project = mockValidProjectFolder();
        S3bucketDataStorage s3bucketDataStorage = mockStorage();
        mockNgsProject(project.getId());
        DataStorageFolder machineRunFolder = mockMachineRunFolder(s3bucketDataStorage,
                DateUtils.now(), true, true);
        mockMachineRunMetadata(project, machineRunFolder, false, null);

        synchronizer.syncNgsProjectsState();
        Mockito.verify(client, Mockito.times(1)).registerSampleSheet(Mockito.any(), Mockito.anyBoolean());
    }

    @Test
    void willRegisterProjectIfLinkedSampleSheetChanged() {
        FolderWithMetadata project = mockValidProjectFolder();
        S3bucketDataStorage s3bucketDataStorage = mockStorage();
        mockNgsProject(project.getId());
        DataStorageFolder machineRunFolder = mockMachineRunFolder(s3bucketDataStorage,
                DateUtils.now(), true, true);
        mockMachineRunMetadata(project, machineRunFolder,
                true, Date.from(DateUtils.nowUTC().toInstant(ZoneOffset.UTC).minus(1, ChronoUnit.HOURS)));

        synchronizer.syncNgsProjectsState();
        Mockito.verify(client, Mockito.times(1)).registerSampleSheet(Mockito.any(), Mockito.anyBoolean());
    }

    @Test
    void willNotRegisterProjectIfLinkedSampleSheetNotChanged() {
        FolderWithMetadata project = mockValidProjectFolder();
        S3bucketDataStorage s3bucketDataStorage = mockStorage();
        DataStorageFolder machineRunFolder = mockMachineRunFolder(s3bucketDataStorage,
                Date.from(DateUtils.nowUTC().toInstant(ZoneOffset.UTC).minus(1, ChronoUnit.HOURS)),
                true, true);
        mockNgsProject(project.getId());
        mockMachineRunMetadata(project, machineRunFolder,
                true, DateUtils.now());

        synchronizer.syncNgsProjectsState();
        Mockito.verify(client, Mockito.never()).registerSampleSheet(Mockito.any(), Mockito.anyBoolean());
    }

    private void mockNgsProject(Long project) {
        FolderWithMetadata hierarchy = new FolderWithMetadata();
        hierarchy.setChildFolders(Collections.singletonList(new Folder(project)));
        Mockito.doReturn(hierarchy).when(client)
                .filterFolders(Mockito.eq(
                        SystemPreferenceUtils.parseProjectIndicator("type=project,project-type=ngs")));
    }


    private DataStorageFolder mockMachineRunFolder(final S3bucketDataStorage storage,
                                                   final Date sampleSheetChanged,
                                                   final boolean mockSampleSheet,
                                                   final boolean mockRTAComplete) {
        DataStorageFolder machineRunFolder = new DataStorageFolder();
        machineRunFolder.setName(MACHINE_RUN_NAME);
        machineRunFolder.setPath(DATA_FOLDER_INTERNAL + ProviderUtils.DELIMITER + MACHINE_RUN_NAME);
        Mockito.doReturn(Collections.singletonList(machineRunFolder))
                .when(client).listDataStorageItems(Mockito.eq(storage.getId()),
                        Mockito.eq(DATA_FOLDER_INTERNAL));

        if (mockRTAComplete) {
            mockStorageFile(RTACOMPLETE_TXT, DateUtils.now(), machineRunFolder, storage);
        }

        if (mockSampleSheet) {
            DataStorageFile sampleSheet = mockStorageFile(SAMPLESHEET_CSV,
                    sampleSheetChanged, machineRunFolder, storage);
            Mockito.doReturn(Collections.singletonList(sampleSheet))
                    .when(client).listDataStorageItems(Mockito.eq(storage.getId()),
                            Mockito.eq(machineRunFolder.getPath()));
            Mockito.doReturn(SAMPLE_SHEET_CONTENT).when(synchronizer).getStorageFileContent(storage, sampleSheet);
        }
        return machineRunFolder;
    }

    private void mockMachineRunMetadata(final FolderWithMetadata project, final DataStorageFolder machineRunFolder,
                                        final boolean linkSampleSheet, final Date updatedDate) {
        final MetadataEntity metadata = new MetadataEntity(MACHINE_RUN_NAME, project);
        final HashMap<String, PipeConfValue> data = new HashMap<>();
        if (linkSampleSheet) {
            data.put(SAMPLE_SHEET_COLUMN, new PipeConfValue(STRING_TYPE,
                    machineRunFolder.getPath() + ProviderUtils.DELIMITER + SAMPLESHEET_CSV));
            data.put(UPDATED_DATE_COLUMN, new PipeConfValue(DATE_TYPE,
                    DateFormatUtils.format(
                            Optional.ofNullable(updatedDate).orElse(DateUtils.now()),
                            NGSProjectSynchronizer.DATE_FORMAT))
            );
        }
        metadata.setData(data);
        Mockito.doReturn(metadata)
                .when(client).getMetadata(project.getId(), MACHINE_RUN_CLASS_NAME, MACHINE_RUN_NAME);
    }

    private DataStorageFile mockStorageFile(final String fileName,
                                            final Date changed,
                                            final DataStorageFolder machineRunFolder,
                                            final S3bucketDataStorage s3bucketDataStorage) {
        DataStorageFile file = new DataStorageFile();
        file.setName(fileName);
        file.setChanged(DateFormatUtils.format(changed, NGSProjectSynchronizer.DATE_FORMAT));
        file.setPath(machineRunFolder.getPath() + ProviderUtils.DELIMITER + fileName);
        Mockito.doReturn(Collections.singletonList(file))
                .when(client).listDataStorageItems(Mockito.eq(s3bucketDataStorage.getId()), Mockito.eq(file.getPath()));
        return file;
    }

    private FolderWithMetadata mockValidProjectFolder() {
        final FolderWithMetadata project = new FolderWithMetadata();
        final HashMap<String, PipeConfValue> data = new HashMap<String, PipeConfValue>(){{
                put("ngs-data", new PipeConfValue(STRING_TYPE, S3_BUCKET_DATA_PATH));
                put("type", new PipeConfValue(STRING_TYPE, "project"));
                put("project-type", new PipeConfValue(STRING_TYPE, "project-ngs"));
            }};
        project.setId(NGS_PROJECT_ID);
        project.setName("ngs-project");
        project.setData(data);
        Mockito.doReturn(project).when(client)
                .getFolderWithMetadata(Mockito.eq(NGS_PROJECT_ID));
        return project;
    }

    private S3bucketDataStorage mockStorage() {
        final S3bucketDataStorage storage = new S3bucketDataStorage();
        storage.setId(1L);
        storage.setPath(BUCKET);
        Mockito.doReturn(storage).when(client)
                .findStorageByPath(Mockito.eq(BUCKET + ProviderUtils.DELIMITER + DATA_FOLDER_INTERNAL));
        return storage;
    }

}