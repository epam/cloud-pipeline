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

import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.AbstractDataStorageItem;
import com.epam.pipeline.entity.datastorage.DataStorageDownloadFileUrl;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.DataStorageItemType;
import com.epam.pipeline.entity.metadata.FolderWithMetadata;
import com.epam.pipeline.entity.metadata.MetadataClass;
import com.epam.pipeline.entity.metadata.MetadataEntity;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import com.epam.pipeline.entity.preference.Preference;
import com.epam.pipeline.entity.utils.ProviderUtils;
import com.epam.pipeline.exception.PipelineResponseApiException;
import com.epam.pipeline.ngs.project.sync.api.client.CloudPipelineAPIClient;
import com.epam.pipeline.ngs.project.sync.entity.NgsProjectSyncContext;
import com.epam.pipeline.utils.SampleSheetParser;
import com.epam.pipeline.utils.SystemPreferenceUtils;
import com.epam.pipeline.vo.metadata.MetadataEntityVO;
import com.epam.pipeline.vo.preprocessing.SampleSheetRegistrationVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@ShellComponent
@RequiredArgsConstructor
public class NGSProjectSynchronizer {

    public static final String UI_NGS_PROJECT_INDICATOR_PREF = "ui.ngs.project.indicator";
    public static final String NGS_PREPROCESSING_DATA_FOLDER_PREF = "ngs.preprocessing.data.folder";
    public static final String NGS_PREPROCESSING_MACHINE_RUN_METADATA_CLASS_PREF =
            "ngs.preprocessing.machine.run.metadata.class.name";
    public static final String NGS_PREPROCESSING_SAMPLE_METADATA_CLASS_PREF = "ngs.preprocessing.sample.metadata.class.name";
    public static final String NGS_PREPROCESSING_SAMPLESHEET_FILE_NAME_PREF = "ngs.preprocessing.samplesheet.file.name";
    public static final String NGS_PREPROCESSING_SAMPLESHEET_LINK_COLUMN_PREF = "ngs.preprocessing.samplesheet.link.column";
    public static final String NGS_PREPROCESSING_COMPLETION_MARK_NAME_PREF =
            "ngs.preprocessing.completion.mark.file.default.name";
    public static final String NGS_PREPROCESSING_COMPLETION_MARK_METADATA_KEY_PREF =
            "ngs.preprocessing.completion.mark.metadata.key";

    public static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'";
    public static final String DATA_STORAGE_PROVIDER_MASK = "[A-Za-z0-9]+://";
    public static final String SAMPLESHEET_FILE_NAME_TEMPLATE = ".*samplesheet.*\\.csv";
    public static final String UPDATED_DATE_COLUMN = "Updated Date";
    public static final String ZERO_TIME_POINT = "1970-01-01T00:00:00Z";

    private final CloudPipelineAPIClient apiClient;
    private NgsProjectSyncContext syncContext;

    @PostConstruct
    public void init() {
        syncContext = buildContext();
    }

    @ShellMethod(
            key = "sync-ngs-projects",
            value = "Sync metadata structure for the ngs project based on raw project data state.")
    public void syncNgsProjectsState() {
        log.info("Starting NGS project synchronization.");
        apiClient.filterFolders(
                SystemPreferenceUtils.parseProjectIndicator(getPreferenceValue(UI_NGS_PROJECT_INDICATOR_PREF))
                ).getChildFolders()
                .forEach(folder -> {
                    try {
                        syncProject(apiClient.getFolderWithMetadata(folder.getId()), syncContext);
                    } catch (Exception e) {
                        log.warn(String.format(
                                "Something went wrong with synchronization of the project: %s, skipping.",
                                   folder.getName())
                        );
                    }
                });
    }

    private NgsProjectSyncContext buildContext() {
        return NgsProjectSyncContext.builder()
                .sampleSheetLinkColumn(getPreferenceValue(NGS_PREPROCESSING_SAMPLESHEET_LINK_COLUMN_PREF))
                .sampleSheetFileName(getPreferenceValue(NGS_PREPROCESSING_SAMPLESHEET_FILE_NAME_PREF))
                .machineRunClass(fetchMetadataClass(NGS_PREPROCESSING_MACHINE_RUN_METADATA_CLASS_PREF))
                .sampleClass(fetchMetadataClass(NGS_PREPROCESSING_SAMPLE_METADATA_CLASS_PREF))
                .build();
    }

    private void syncProject(final FolderWithMetadata project, final NgsProjectSyncContext syncContext) {
        log.info(String.format("Synchronization of project: %s id: %d", project.getName(), project.getId()));
        final Map<String, PipeConfValue> folderMetadata = MapUtils.emptyIfNull(project.getData());
        final String ngsDataPathMetadataKey = getPreferenceValue(NGS_PREPROCESSING_DATA_FOLDER_PREF);
        final String ngsDataPath = Optional.ofNullable(folderMetadata.get(ngsDataPathMetadataKey))
                .map(PipeConfValue::getValue)
                .orElseThrow(() -> new IllegalStateException(
                        String.format("No NGS data dir provided with metadata key: %s", ngsDataPathMetadataKey)));
        final String pathWithOutStorageMask = ngsDataPath.replaceAll(DATA_STORAGE_PROVIDER_MASK, StringUtils.EMPTY);

        final AbstractDataStorage storage = apiClient.findStorageByPath(pathWithOutStorageMask);
        final String internalNgsDataPath = getInternalStoragePath(storage, ngsDataPath);
        final List<AbstractDataStorageItem> machineRunFolders = apiClient.listDataStorageItems(
                        storage.getId(), internalNgsDataPath
                ).stream().filter(item -> item.getType().equals(DataStorageItemType.Folder))
                .collect(Collectors.toList());

        log.info(String.format("Found %d machine runs to sync.", machineRunFolders.size()));

        machineRunFolders.forEach(machineRunFolder -> {
            final String machineRun = machineRunFolder.getName();
            final MetadataEntity machineRunEntity = getOrCreateMachineRunEntity(project, syncContext, machineRun);
            final Pair<DataStorageFile, byte[]> sampleSheetFile = findSampleSheetFile(
                    storage, machineRunFolder, machineRunEntity);

            final AbstractDataStorageItem dataSyncCompleteMarkFile = fetchDataSyncCompleteMarkFile(
                    storage, machineRunFolder, folderMetadata);
            if (dataSyncCompleteMarkFile == null) {
                log.info("MachineRun hasn't data sync complete mark file, skipping.");
               return;
            }

            if (ArrayUtils.isEmpty(sampleSheetFile.getValue())) {
                log.warn(String.format(
                        "No sample sheet is found for machine run folder: %s It will be skipped!", machineRun));
                return;
            } else {
                log.info(String.format("Found sample sheet: %s for machine run: %s",
                        sampleSheetFile.getKey().getPath(), machineRun));
            }

            if (needToUpdateSampleSheet(syncContext, machineRunEntity, sampleSheetFile.getKey())) {
                // update sample sheet metadata
                SampleSheetRegistrationVO registrationVO = new SampleSheetRegistrationVO();
                registrationVO.setFolderId(project.getId());
                registrationVO.setMachineRunId(machineRunEntity.getId());

                registrationVO.setPath(getFullStorageFilePath(storage, sampleSheetFile.getKey().getPath()));
                final MetadataEntity updated = apiClient.registerSampleSheet(registrationVO, true);
                log.info(String.format("Register sample sheet: %s for machine run: %s",
                        sampleSheetFile.getKey().getName(), updated.getExternalId()));
            } else {
                log.info(String.format("No need to sync %s, skipping.", machineRun));
            }
        });

    }

    private AbstractDataStorageItem fetchDataSyncCompleteMarkFile(final AbstractDataStorage storage,
                                                          final AbstractDataStorageItem machineRunFolder,
                                                          @NotNull final Map<String, PipeConfValue> folderMetadata) {
        final String completionMarkDefaultName = getPreferenceValue(NGS_PREPROCESSING_COMPLETION_MARK_NAME_PREF);
        final String completionMarkMetadataKey = getPreferenceValue(NGS_PREPROCESSING_COMPLETION_MARK_METADATA_KEY_PREF);

        final String completionMarkName = Optional.ofNullable(folderMetadata.get(completionMarkMetadataKey))
                .map(PipeConfValue::getValue).orElse(completionMarkDefaultName);
        try {
            return apiClient.listDataStorageItems(
                    storage.getId(),
                    Paths.get(machineRunFolder.getPath(), completionMarkName).toString()
            ).stream().filter(item -> DataStorageItemType.File.equals(item.getType())).findFirst().orElse(null);
        } catch (PipelineResponseApiException e) {
            log.debug(e.getMessage());
            log.debug(String.format(
                    "Can't load data sync completion file with name: %s, in machine run folder: %s",
                    completionMarkName, machineRunFolder.getPath()));
            return null;
        }
    }

    private boolean needToUpdateSampleSheet(final NgsProjectSyncContext syncContext,
                                            final MetadataEntity machineRunEntity,
                                            final DataStorageFile sampleSheetFile) {

        final Map<String, PipeConfValue> machineRunData = MapUtils.emptyIfNull(machineRunEntity.getData());
        if (machineRunData.containsKey(syncContext.getSampleSheetLinkColumn())) {
            final Date sampleSheetTimestamp;
            final Date lastUpdateTimestamp;
            try {
                sampleSheetTimestamp = DateUtils.parseDate(sampleSheetFile.getChanged(), DATE_FORMAT);
                lastUpdateTimestamp = DateUtils.parseDate(
                        Optional.ofNullable(machineRunData.get(UPDATED_DATE_COLUMN))
                                .map(PipeConfValue::getValue).orElse(ZERO_TIME_POINT), DATE_FORMAT
                );
            } catch (ParseException e) {
                log.warn(String.format(
                        "Can't parse a date for machine run: %s", machineRunEntity.getExternalId()), e);
                return false;
            }
            log.info(String.format("Last update time of MachineRun Entity: %s, sample sheet file change time: %s",
                    lastUpdateTimestamp, sampleSheetTimestamp));
            return sampleSheetTimestamp.after(lastUpdateTimestamp);
        } else {
            log.info(String.format(
                    "For machine run %s there is no linked sample sheet yet, will proceed with sync",
                    machineRunEntity.getExternalId()));
            return true;
        }
    }

    private MetadataClass fetchMetadataClass(final String classPrefName) {
        final String machineRunClassName = getPreferenceValue(classPrefName);
        MetadataClass machineRunClass;
        try {
            machineRunClass = apiClient.getMetadataClass(machineRunClassName);
        } catch (PipelineResponseApiException e) {
            log.debug(String.format("Can't load metadata class %s, try to create it.", machineRunClassName));
            machineRunClass = apiClient.registerMetadataClass(machineRunClassName);
        }
        return machineRunClass;
    }

    private Pair<DataStorageFile, byte[]> findSampleSheetFile(final AbstractDataStorage storage,
                                                final AbstractDataStorageItem machineRunFolder,
                                                final MetadataEntity machineRunEntity) {
        return fetchLinkedSampleSheetFile(storage, machineRunEntity)
                .orElse(fetchLatestSampleSheetFile(storage, machineRunFolder));
    }

    private Optional<Pair<DataStorageFile, byte[]>> fetchLinkedSampleSheetFile(final AbstractDataStorage storage,
                                                                               final MetadataEntity machineRunEntity) {
        return Optional.ofNullable(MapUtils.emptyIfNull(machineRunEntity.getData())
                        .get(syncContext.getSampleSheetLinkColumn())
                ).map(PipeConfValue::getValue)
                .flatMap(path -> apiClient.listDataStorageItems(storage.getId(), getInternalStoragePath(storage, path))
                        .stream().filter(item -> DataStorageItemType.File.equals(item.getType())).findFirst()
                ).map(item -> verifyAndGetSampleSheetContent(storage, (DataStorageFile) item));
    }

    private Pair<DataStorageFile, byte[]> fetchLatestSampleSheetFile(final AbstractDataStorage storage,
                                                                     final AbstractDataStorageItem machineRunFolder) {
        return ListUtils.emptyIfNull(
                        apiClient.listDataStorageItems(storage.getId(),
                                getInternalStoragePath(storage, machineRunFolder.getPath()))
                ).stream()
                .filter(item -> item.getType() == DataStorageItemType.File)
                .filter(item -> item.getName().toLowerCase(Locale.ROOT).matches(SAMPLESHEET_FILE_NAME_TEMPLATE))
                .map(item -> (DataStorageFile) item)
                .map(item -> verifyAndGetSampleSheetContent(storage, item))
                .filter(content -> ArrayUtils.isNotEmpty(content.getValue()))
                .max((i1, i2) -> {
                    // Sort with non-decreasing order
                    try {
                        return DateUtils.parseDate(i1.getKey().getChanged(), DATE_FORMAT)
                                .compareTo(DateUtils.parseDate(i2.getKey().getChanged(), DATE_FORMAT));
                    } catch (ParseException e) {
                        log.warn(String.format("Can't parse changed dates for sample sheets: %s, %s",
                                i1.getKey().getName(), i2.getKey().getName()));
                        return 0;
                    }
                }).orElse(ImmutablePair.of(null, new byte[0]));
    }

    private Pair<DataStorageFile, byte[]> verifyAndGetSampleSheetContent(final AbstractDataStorage storage,
                                                                         final DataStorageFile item) {
        final DataStorageDownloadFileUrl url = apiClient.getStorageItemContent(
                storage.getId(), getInternalStoragePath(storage, item.getPath()));
        try {
            byte[] bytes = IOUtils.toByteArray(new URL(url.getUrl()));
            SampleSheetParser.parseSampleSheet(bytes);
            return ImmutablePair.of(item, bytes);
        } catch (IOException | IllegalStateException e) {
            log.warn("Can't parse sample sheet file content from URL: ", e);
           return ImmutablePair.of(item, new byte[0]);
        }
    }


    @NotNull
    private MetadataEntity getOrCreateMachineRunEntity(final FolderWithMetadata project,
                                                       final NgsProjectSyncContext syncContext,
                                                       final String machineRun) {
        try {
            return apiClient.getMetadata(project.getId(),
                    syncContext.getMachineRunClass().getName(), machineRun);
        } catch (PipelineResponseApiException e) {
            // machine run entity is not created - we should create and proceed
            MetadataEntityVO entityVO = new MetadataEntityVO();
            entityVO.setParentId(project.getId());
            entityVO.setClassId(syncContext.getMachineRunClass().getId());
            entityVO.setClassName(syncContext.getMachineRunClass().getName());
            entityVO.setExternalId(machineRun);
            entityVO.setEntityName(machineRun);
            return apiClient.createMetadata(entityVO);
        }
    }

    @NotNull
    private String getPreferenceValue(final String name) {
        return Optional.ofNullable(apiClient.getPreference(name)).map(Preference::getValue)
                .orElseThrow(() -> new IllegalStateException("No preference value: " + name));
    }

    @NotNull
    private String getInternalStoragePath(final AbstractDataStorage storage, final String path) {
        return path.replace(storage.getPathMask() + ProviderUtils.DELIMITER, StringUtils.EMPTY);
    }

    @NotNull
    private String getFullStorageFilePath(final AbstractDataStorage storage,
                                          final String path) {
        if (!path.startsWith(storage.getPathMask())) {
            return storage.getPathMask() + ProviderUtils.DELIMITER +
                    (path.startsWith(ProviderUtils.DELIMITER) ? path.substring(1) : path);
        }
        return path;
    }
}




