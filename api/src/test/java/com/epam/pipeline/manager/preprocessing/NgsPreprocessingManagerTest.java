/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.preprocessing;

import com.epam.pipeline.controller.vo.EntityVO;
import com.epam.pipeline.controller.vo.preprocessing.SampleSheetRegistrationVO;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage;
import com.epam.pipeline.entity.metadata.MetadataClass;
import com.epam.pipeline.entity.metadata.MetadataEntity;
import com.epam.pipeline.entity.metadata.MetadataEntry;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import com.epam.pipeline.entity.metadata.PipeConfValueType;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.AbstractManagerTest;
import com.epam.pipeline.manager.EntityManager;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import com.epam.pipeline.manager.datastorage.providers.ProviderUtils;
import com.epam.pipeline.manager.metadata.MetadataEntityManager;
import com.epam.pipeline.manager.metadata.MetadataManager;
import com.epam.pipeline.manager.pipeline.FolderManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import org.apache.commons.lang.time.DateFormatUtils;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import org.mockito.internal.verification.Times;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

import java.text.ParseException;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static com.epam.pipeline.util.CustomMatchers.matches;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;

@Transactional
@SuppressWarnings({"PMD.UnusedPrivateField"})
public class NgsPreprocessingManagerTest extends AbstractManagerTest {

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

    public static final long ID = 1L;
    private static final MetadataClass SAMPLE_METADATA_CLASS = new MetadataClass(
            ID, SystemPreferences.PREPROCESSING_SAMPLE_CLASS.getDefaultValue());
    public static final int SEC = 1000;
    public static final String SAMPLESHEET_CSV = "samplesheet.csv";
    public static final String MACHINE_RUN_EXTERNAL = "MachineRun1";
    public static final String TYPE = "type";
    public static final String PROJECT_TAG = "project";
    public static final String NGS_DATA = "ngs-data";
    public static final String PROJECT_TYPE = "project-type";
    public static final String NGS = "ngs";
    public static final String NGS_DATA_STORAGE = "ngs-data-storage";
    public static final String NGS_PROJECT = "ngs-project";


    @InjectMocks
    @Autowired
    private NgsPreprocessingManager ngsPreprocessingManager;

    @MockBean
    private FolderManager folderManager;

    @MockBean
    private MetadataEntityManager metadataEntityManager;

    @MockBean
    private MetadataManager metadataManager;

    @MockBean
    private DataStorageManager storageManager;

    @MockBean
    private EntityManager entityManager;

    @Test
    public void registerSampleSheetTest() throws ParseException, InterruptedException {
        String machineRunId = MACHINE_RUN_EXTERNAL;

        S3bucketDataStorage storage = getS3bucketDataStorage();
        Folder project = getFolder(storage);
        MetadataEntry projectMetadata = getMetadataEntry(project,
                new HashMap<String, String>() {{
                    put(NGS_DATA, storage.getPathMask() + ProviderUtils.DELIMITER);
                    put(TYPE, PROJECT_TAG);
                    put(PROJECT_TYPE, NGS);
                }});

        MetadataEntity machineRunMetadataEntity = getMachineRunMetadataEntity(machineRunId,
                new HashMap<String, String>(){{
                    put(NgsPreprocessingManager.UPDATED_DATE_COLUMN_NAME,
                            DateFormatUtils.format(new Date(), NgsPreprocessingManager.DATE_FORMAT));
                }});
        SampleSheetRegistrationVO registrationVO = getSampleSheetRegistrationVO(project);

        mockBehaviour(machineRunId, storage, project, projectMetadata, machineRunMetadataEntity);

        MetadataEntity sampleSheet = ngsPreprocessingManager.registerSampleSheet(registrationVO, false);
        Assert.assertNotNull(sampleSheet);
        Assert.assertTrue(sampleSheet.getData()
                .get(SystemPreferences.PREPROCESSING_SAMPLESHEET_LINK_COLUMN.getDefaultValue())
                .getValue().endsWith(SAMPLESHEET_CSV));

        //check that we allow to overwrite it
        doReturn(SAMPLE_METADATA_CLASS).when(metadataEntityManager)
                .loadClass(SystemPreferences.PREPROCESSING_SAMPLE_CLASS.getDefaultValue());

        doReturn(new MetadataEntity()).when(metadataEntityManager).loadByExternalId(
                any(),
                argThat(matches(className ->
                        className.equals(SystemPreferences.PREPROCESSING_SAMPLE_CLASS.getDefaultValue()))),
                argThat(matches(folderId -> folderId.equals(project.getId()))));

        Date now = DateUtils.now();
        Thread.sleep(SEC);
        sampleSheet = ngsPreprocessingManager.registerSampleSheet(registrationVO, true);
        Assert.assertNotNull(sampleSheet);
        Assert.assertTrue(sampleSheet.getData()
                .get(SystemPreferences.PREPROCESSING_SAMPLESHEET_LINK_COLUMN.getDefaultValue())
                .getValue().endsWith(SAMPLESHEET_CSV));
        Assert.assertTrue(
                org.apache.commons.lang.time.DateUtils.parseDate(
                        sampleSheet.getData().get(NgsPreprocessingManager.UPDATED_DATE_COLUMN_NAME).getValue(),
                        new String[] {NgsPreprocessingManager.DATE_FORMAT}).after(now));
    }

    @Test(expected = IllegalArgumentException.class)
    public void registerSampleSheetFailIfNoDataFolderTagTest() {
        S3bucketDataStorage storage = getS3bucketDataStorage();
        Folder project = getFolder(storage);
        MetadataEntry projectMetadata = getMetadataEntry(project,
                new HashMap<String, String>() {{
                    put(TYPE, PROJECT_TAG);
                    put(PROJECT_TYPE, NGS);
                }});

        MetadataEntity machineRunMetadataEntity = getMachineRunMetadataEntity(MACHINE_RUN_EXTERNAL,
                new HashMap<String, String>(){{
                    put(SystemPreferences.PREPROCESSING_SAMPLESHEET_LINK_COLUMN.getDefaultValue(),
                            storage.getPathMask() + ProviderUtils.DELIMITER + SAMPLESHEET_CSV);
                    put(NgsPreprocessingManager.UPDATED_DATE_COLUMN_NAME,
                            DateFormatUtils.format(new Date(), NgsPreprocessingManager.DATE_FORMAT));
                }});
        SampleSheetRegistrationVO registrationVO = getSampleSheetRegistrationVO(project);

        mockBehaviour(MACHINE_RUN_EXTERNAL, storage, project, projectMetadata, machineRunMetadataEntity);

        ngsPreprocessingManager.registerSampleSheet(registrationVO, false);
    }

    @Test(expected = IllegalStateException.class)
    public void registerSampleSheetFailIfNoProjectTagsTest() {
        String machineRunId = MACHINE_RUN_EXTERNAL;

        S3bucketDataStorage storage = getS3bucketDataStorage();
        Folder project = getFolder(storage);
        MetadataEntry projectMetadata = getMetadataEntry(project,
                new HashMap<String, String>() {{
                    put(NGS_DATA, storage.getPathMask() + ProviderUtils.DELIMITER);
                    put(TYPE, PROJECT_TAG);
                }});

        MetadataEntity machineRunMetadataEntity = getMachineRunMetadataEntity(machineRunId,
                new HashMap<String, String>(){{
                    put(SystemPreferences.PREPROCESSING_SAMPLESHEET_LINK_COLUMN.getDefaultValue(),
                            storage.getPathMask() + ProviderUtils.DELIMITER + SAMPLESHEET_CSV);
                    put(NgsPreprocessingManager.UPDATED_DATE_COLUMN_NAME,
                            DateFormatUtils.format(new Date(), NgsPreprocessingManager.DATE_FORMAT));
                }});
        SampleSheetRegistrationVO registrationVO = getSampleSheetRegistrationVO(project);

        mockBehaviour(machineRunId, storage, project, projectMetadata, machineRunMetadataEntity);

        ngsPreprocessingManager.registerSampleSheet(registrationVO, false);
    }

    @Test
    public void unregisterSampleSheetTest() {
        String machineRunId = MACHINE_RUN_EXTERNAL;

        S3bucketDataStorage storage = getS3bucketDataStorage();
        Folder project = getFolder(storage);
        MetadataEntry projectMetadata = getMetadataEntry(project,
                new HashMap<String, String>() {{
                    put(NGS_DATA, storage.getPathMask() + ProviderUtils.DELIMITER);
                    put(TYPE, PROJECT_TAG);
                    put(PROJECT_TYPE, NGS);
                }});

        MetadataEntity machineRunMetadataEntity = getMachineRunMetadataEntity(machineRunId,
                new HashMap<String, String>(){{
                    put(NgsPreprocessingManager.UPDATED_DATE_COLUMN_NAME,
                            DateFormatUtils.format(new Date(), NgsPreprocessingManager.DATE_FORMAT));
                }});
        mockBehaviour(machineRunId, storage, project, projectMetadata, machineRunMetadataEntity);

        doReturn(SAMPLE_METADATA_CLASS).when(metadataEntityManager)
                .loadClass(SystemPreferences.PREPROCESSING_SAMPLE_CLASS.getDefaultValue());

        doReturn(new MetadataEntity()).when(metadataEntityManager).loadByExternalId(
                any(),
                argThat(matches(className ->
                        className.equals(SystemPreferences.PREPROCESSING_SAMPLE_CLASS.getDefaultValue()))),
                argThat(matches(folderId -> folderId.equals(project.getId()))));

        SampleSheetRegistrationVO registrationVO = getSampleSheetRegistrationVO(project);
        ngsPreprocessingManager.registerSampleSheet(registrationVO, false);
        ngsPreprocessingManager.unregisterSampleSheet(ID, ID, true);
        Mockito.verify(metadataEntityManager, new Times(4)).deleteMetadataEntity(any());
        Mockito.verify(storageManager, new Times(2)).deleteDataStorageItems(eq(ID), any(), eq(false));
    }

    private void mockBehaviour(String machineRunId, S3bucketDataStorage storage, Folder project,
                               MetadataEntry projectMetadata, MetadataEntity machineRunMetadataEntity) {
        doReturn(project).when(folderManager)
                .create(argThat(matches(Predicates.forFolderTemplate(project.getName()))));

        doReturn(project).when(folderManager)
                .load(argThat(matches(Predicates.byId(project.getId()))));

        doReturn(storage).when(storageManager)
                .loadByPathOrId(argThat(matches(Predicates.forStorageByPath(storage))));

        doReturn(storage).when(storageManager)
                .load(argThat(matches(Predicates.byId(storage.getId()))));

        doReturn(Collections.singletonList(projectMetadata)).when(metadataManager)
                .listMetadataItems(argThat(matches(Predicates.forFolderMetadata(project))));

        doReturn(SAMPLE_METADATA_CLASS).when(metadataEntityManager)
                .getOrCreateMetadataClass(eq(SystemPreferences.PREPROCESSING_SAMPLE_CLASS.getDefaultValue()));

        doReturn(machineRunMetadataEntity).when(metadataEntityManager).loadByExternalId(
                argThat(matches(id -> id.equals(machineRunId))),
                argThat(matches(className ->
                        className.equals(SystemPreferences.PREPROCESSING_MACHINE_RUN_CLASS.getDefaultValue()))),
                argThat(matches(folderId -> folderId.equals(project.getId()))));

        doReturn(machineRunMetadataEntity).when(metadataEntityManager)
                .load(argThat(matches(Predicates.byId(machineRunMetadataEntity.getId()))));
    }

    private SampleSheetRegistrationVO getSampleSheetRegistrationVO(Folder project) {
        SampleSheetRegistrationVO registrationVO = new SampleSheetRegistrationVO();
        registrationVO.setFolderId(project.getId());
        registrationVO.setMachineRunId(ID);
        registrationVO.setContent(SAMPLE_SHEET_CONTENT);
        return registrationVO;
    }

    private MetadataEntity getMachineRunMetadataEntity(String machineRunId,
                                                       Map<String, String> info) {
        MetadataEntity metadataEntity = new MetadataEntity();
        metadataEntity.setId(ID);
        metadataEntity.setExternalId(machineRunId);
        HashMap<String, PipeConfValue> metadata = new HashMap<String, PipeConfValue>(){{
                info.forEach((k, v) -> put(k, new PipeConfValue(PipeConfValueType.STRING.toString(), v)));
            }};
        metadataEntity.setData(metadata);
        metadataEntity.setClassEntity(
                new MetadataClass(ID, SystemPreferences.PREPROCESSING_MACHINE_RUN_CLASS.getDefaultValue()));
        return metadataEntity;
    }

    private MetadataEntry getMetadataEntry(Folder project, Map<String, String> info) {
        MetadataEntry metadataEntry = new MetadataEntry();
        Map<String, PipeConfValue> data = new HashMap<String, PipeConfValue>(){{
                info.forEach((k, v) -> put(k, new PipeConfValue(PipeConfValueType.STRING.toString(), v)));
            }};
        metadataEntry.setData(data);
        metadataEntry.setEntity(new EntityVO(project.getId(), AclClass.FOLDER));
        return metadataEntry;
    }

    private S3bucketDataStorage getS3bucketDataStorage() {
        S3bucketDataStorage storage = new S3bucketDataStorage();
        storage.setPath(NGS_DATA_STORAGE);
        storage.setId(ID);
        return storage;
    }

    private Folder getFolder(S3bucketDataStorage storage) {
        Folder project = new Folder();
        project.setId(ID);
        project.setName(NGS_PROJECT);
        project.setHasMetadata(true);
        project.setStorages(Collections.singletonList(storage));
        return project;
    }

    private static class Predicates {
        static Predicate<Folder> forFolderTemplate(String name) {
            return f -> f.getName().equals(name);
        }

        static Predicate<List<EntityVO>> forFolderMetadata(Folder folder) {
            return le -> le.stream().findFirst()
                    .map(e -> e.getEntityClass().equals(folder.getAclClass())
                            && e.getEntityId().equals(folder.getId())).orElse(false);
        }

        public static Predicate<String> forStorageByPath(AbstractDataStorage storage) {
            return path -> path.startsWith(storage.getPath());
        }

        public static Predicate<Long> byId(Long expectedId) {
            return id -> id.equals(expectedId);
        }

    }
}
