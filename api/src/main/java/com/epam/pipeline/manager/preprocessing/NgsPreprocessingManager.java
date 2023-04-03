/*
 * Copyright 2022 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.controller.vo.EntityVO;
import com.epam.pipeline.controller.vo.data.storage.UpdateDataStorageItemVO;
import com.epam.pipeline.controller.vo.metadata.MetadataEntityVO;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageItemType;
import com.epam.pipeline.entity.metadata.MetadataClass;
import com.epam.pipeline.entity.metadata.MetadataEntity;
import com.epam.pipeline.entity.metadata.MetadataEntry;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import com.epam.pipeline.entity.metadata.PipeConfValueType;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.entity.pipeline.run.parameter.DataStorageLink;
import com.epam.pipeline.entity.samplesheet.SampleSheet;
import com.epam.pipeline.controller.vo.preprocessing.SampleSheetRegistrationVO;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import com.epam.pipeline.manager.datastorage.providers.ProviderUtils;
import com.epam.pipeline.manager.metadata.MetadataEntityManager;
import com.epam.pipeline.manager.metadata.MetadataManager;
import com.epam.pipeline.manager.metadata.parser.EntityTypeField;
import com.epam.pipeline.manager.metadata.parser.MetadataParsingResult;
import com.epam.pipeline.manager.pipeline.FolderManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.utils.SystemPreferenceUtils;
import com.epam.pipeline.utils.DataStorageUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.time.DateFormatUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("PMD.AvoidCatchingGenericException")
public class NgsPreprocessingManager {

    public static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'";
    public static final String DATA_STORAGE_PROVIDER_MASK = "[A-Za-z0-9]+://";
    public static final String TAG_KEY_VALUE_DELIMITER = "=";
    public static final String SAMPLE_PREFIX = "S";
    public static final String LANE_PREFIX = "L";
    public static final String NAME_DELIMITER = "_";
    public static final String UPDATED_DATE_COLUMN_NAME = "Updated Date";

    private final FolderManager folderManager;
    private final MetadataManager metadataManager;
    private final MetadataEntityManager metadataEntityManager;
    private final DataStorageManager storageManager;
    private final PreferenceManager preferenceManager;
    private final MessageHelper messageHelper;

    @Transactional(propagation = Propagation.REQUIRED)
    public void registerSampleSheet(final SampleSheetRegistrationVO registrationVO) {
        final Long folderId = registrationVO.getFolderId();
        Assert.notNull(folderId,
                messageHelper.getMessage(MessageConstants.ERROR_NGS_PREPROCESSING_FOLDER_ID_NOT_PROVIDED));
        final Folder folder = folderManager.load(folderId);

        final MetadataEntry folderMetadata = fetchFolderMetadata(folder);
        final DataStorageLink dataFolderPath = fetchDataFolder(folderMetadata);
        final AbstractDataStorage storage = storageManager.load(dataFolderPath.getDataStorageId());

        final Long machineRunId = registrationVO.getMachineRunId();
        Assert.notNull(machineRunId,
                messageHelper.getMessage(MessageConstants.ERROR_NGS_PREPROCESSING_MACHINE_RUN_NOT_PROVIDED));
        final MetadataEntity machineRunMetadataEntity = fetchMachineRunMetadataEntity(dataFolderPath, machineRunId);

        final byte[] content = registrationVO.getContent();
        Assert.state(ArrayUtils.isNotEmpty(content),
                messageHelper.getMessage(MessageConstants.ERROR_NGS_PREPROCESSING_SAMPLESHEET_CONTENT_NOT_PROVIDED));
        final SampleSheet sampleSheet = SampleSheetParser.parseSampleSheet(content);

        final String sampleMetadataClassName = preferenceManager.getPreference(
                SystemPreferences.PREPROCESSING_SAMPLE_CLASS);
        final MetadataClass sampleMetadataClass = metadataEntityManager.getOrCreate(sampleMetadataClassName);

        final List<MetadataEntityVO> samples = mapSampleSheetToMetadataEntities(
                folderId, machineRunMetadataEntity, sampleSheet, sampleMetadataClass);

        unregisterSampleSheet(folderId, machineRunId, true);
        samples.forEach(metadataEntityManager::updateMetadataEntity);

        final String sampleSheetFileInternalPath = Paths.get(
                dataFolderPath.getPath(),
                machineRunMetadataEntity.getExternalId(),
                preferenceManager.getPreference(SystemPreferences.PREPROCESSING_SAMPLESHEET_FILE_NAME)
        ).toString();

        final DataStorageLink sampleSheetFileLink = DataStorageUtils.constructDataStorageFileLink(
                storage, sampleSheetFileInternalPath);

        storageManager.createDataStorageFile(dataFolderPath.getDataStorageId(), sampleSheetFileInternalPath, content);

        linkSamplesToMachineRun(folderId, machineRunMetadataEntity, sampleMetadataClass,
                samples, sampleSheetFileLink.getAbsolutePath());
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void unregisterSampleSheet(final Long folderId, final Long machineRunId, final boolean deleteFile) {
        final MetadataEntry folderMetadata = metadataManager.listMetadataItems(
                        Collections.singletonList(new EntityVO(folderId, AclClass.FOLDER))).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        messageHelper.getMessage(MessageConstants.ERROR_NGS_PREPROCESSING_FOLDER_HAS_NO_METADATA,
                                folderId)));
        final DataStorageLink dataFolderPath = fetchDataFolder(folderMetadata);
        final AbstractDataStorage storage = storageManager.load(dataFolderPath.getDataStorageId());

        final MetadataEntity machineRunMetadata = fetchMachineRunMetadataEntity(dataFolderPath, machineRunId);

        final String sampleMetadataClassName = preferenceManager.getPreference(
                SystemPreferences.PREPROCESSING_SAMPLE_CLASS);
        final MetadataClass sampleMetadataClass = metadataEntityManager.loadClass(sampleMetadataClassName);

        final String machineRunToSampleColumn = preferenceManager.getPreference(
                SystemPreferences.PREPROCESSING_MACHINE_RUN_TO_SAMPLE_COLUMN);
        final String machineRunLinkedSampleSheetColumn = preferenceManager
                .getPreference(SystemPreferences.PREPROCESSING_SAMPLESHEET_LINK_COLUMN);

        final Map<String, PipeConfValue> machineRunData = machineRunMetadata.getData();
        Optional.ofNullable(machineRunData.get(machineRunToSampleColumn))
                .ifPresent(value -> {
                    if (EntityTypeField.isArrayType(value.getType())) {
                        final List<String> samples = JsonMapper
                                .parseData(value.getValue(), new TypeReference<List<String>>() {});
                        samples.stream().map(sample -> metadataEntityManager
                                .loadByExternalId(sample, sampleMetadataClass.getName(), folderId))
                                .forEach(sampleMetadataEntity ->
                                        metadataEntityManager.deleteMetadataEntity(sampleMetadataEntity.getId()));
                    }
                });

        final DataStorageLink linkedSampleSheetLink = Optional.ofNullable(
                machineRunData.get(machineRunLinkedSampleSheetColumn)
        ).map(PipeConfValue::getValue)
                .map(fullPath -> DataStorageUtils.constructDataStorageFileLink(storage, fullPath))
                .orElse(null);

        machineRunData.put(machineRunToSampleColumn, null);
        machineRunData.put(machineRunLinkedSampleSheetColumn,
                new PipeConfValue(PipeConfValueType.STRING.toString(), StringUtils.EMPTY));
        machineRunData.put(UPDATED_DATE_COLUMN_NAME, null);

        final MetadataEntityVO metadataEntityVO = new MetadataEntityVO();
        metadataEntityVO.setEntityName(machineRunMetadata.getName());
        metadataEntityVO.setExternalId(machineRunMetadata.getExternalId());
        metadataEntityVO.setClassId(machineRunMetadata.getClassEntity().getId());
        metadataEntityVO.setParentId(folderId);
        metadataEntityVO.setEntityId(machineRunId);
        metadataEntityVO.setData(machineRunData);
        metadataEntityManager.updateMetadataEntity(metadataEntityVO);

        if (deleteFile && linkedSampleSheetLink != null) {
            deleteStorageFileIfExists(dataFolderPath.getDataStorageId(), linkedSampleSheetLink.getPath());
        }
    }

    private void linkSamplesToMachineRun(final Long folderId, final MetadataEntity machineRunMetadata,
                                         final MetadataClass sampleMetadataClass, final List<MetadataEntityVO> samples,
                                         final String absolutePath) {
        final String machineRunToSampleColumn = preferenceManager.getPreference(
                SystemPreferences.PREPROCESSING_MACHINE_RUN_TO_SAMPLE_COLUMN);
        machineRunMetadata.getData().put(
                machineRunToSampleColumn,
                new PipeConfValue(String.format(EntityTypeField.ARRAY_TYPE, sampleMetadataClass.getName()),
                        JsonMapper.convertDataToJsonStringForQuery(
                                samples.stream().map(MetadataEntityVO::getExternalId).collect(Collectors.toList())
                        )
                ));
        machineRunMetadata.getData().put(
                preferenceManager.getPreference(SystemPreferences.PREPROCESSING_SAMPLESHEET_LINK_COLUMN),
                new PipeConfValue(PipeConfValueType.STRING.toString(), absolutePath)
        );
        machineRunMetadata.getData().put(
                UPDATED_DATE_COLUMN_NAME,
                new PipeConfValue(PipeConfValueType.DATE.toString(),
                        DateFormatUtils.format(DateUtils.now(), DATE_FORMAT))
        );
        final MetadataParsingResult toUpd = new MetadataParsingResult(
                machineRunMetadata.getClassEntity(),
                Collections.singletonMap(sampleMetadataClass.getName(),
                        samples.stream().map(MetadataEntityVO::getExternalId).collect(Collectors.toSet())
                ),
                Collections.singletonMap(machineRunMetadata.getExternalId(), machineRunMetadata)
        );
        metadataEntityManager.createAndUpdateEntities(folderId, toUpd);
    }

    private MetadataEntity fetchMachineRunMetadataEntity(final DataStorageLink dataFolderPath,
                                                         final Long machineRunId) {
        final String machineRunMetadataClass = preferenceManager.getPreference(
                SystemPreferences.PREPROCESSING_MACHINE_RUN_CLASS);

        final MetadataEntity machineRunMetadataEntity = metadataEntityManager.load(machineRunId);
        Assert.notNull(machineRunMetadataEntity,
                messageHelper.getMessage(
                        MessageConstants.ERROR_NGS_PREPROCESSING_NO_MACHINE_RUN_METADATA, machineRunId));
        Assert.state(machineRunMetadataEntity.getClassEntity().getName().equals(machineRunMetadataClass),
                messageHelper.getMessage(MessageConstants.ERROR_NGS_PREPROCESSING_MACHINE_RUN_WRONG_METADATA_CLASS,
                machineRunId, machineRunMetadataEntity.getClassEntity().getName(), machineRunMetadataClass));

        // check that folder for machineRun exists
        if (!checkPathExistence(dataFolderPath.getDataStorageId(),
                Paths.get(dataFolderPath.getPath(), machineRunMetadataEntity.getExternalId()).toString())) {
            throw new IllegalStateException(
                    messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_PATH_NOT_FOUND,
                            dataFolderPath.getPath(), dataFolderPath.getDataStorageId()));
        }
        return machineRunMetadataEntity;
    }

    private void deleteStorageFileIfExists(final Long storageId, final String internalPath) {
        final AbstractDataStorage dataStorage = storageManager.load(storageId);
        if (checkPathExistence(dataStorage.getId(), internalPath)) {
            final UpdateDataStorageItemVO sampleSheetItem = new UpdateDataStorageItemVO();
            sampleSheetItem.setPath(internalPath);
            sampleSheetItem.setType(DataStorageItemType.File);
            storageManager.deleteDataStorageItems(
                    storageId,
                    Collections.singletonList(sampleSheetItem),
                    dataStorage.isVersioningEnabled()
            );
        }
    }

    private DataStorageLink fetchDataFolder(final MetadataEntry metadata) {
        final String dataFolderMetadataKey = preferenceManager.getPreference(
                SystemPreferences.PREPROCESSING_DATA_FOLDER);
        final PipeConfValue dataPath = metadata.getData().get(dataFolderMetadataKey);
        Assert.notNull(dataPath,
                messageHelper.getMessage(MessageConstants.ERROR_NGS_PREPROCESSING_FOLDER_SHOULD_HAVE_METADATA,
                        dataFolderMetadataKey)
        );

        final String pathWithOutStorageMask = dataPath.getValue()
                .replaceAll(DATA_STORAGE_PROVIDER_MASK, StringUtils.EMPTY);
        final AbstractDataStorage dataStorage = storageManager.loadByPathOrId(pathWithOutStorageMask);

        final DataStorageLink dataStorageLink = DataStorageUtils.constructDataStorageLink(
                dataStorage, dataPath.getValue(), dataStorage.getPathMask() + ProviderUtils.DELIMITER);
        if (!checkPathExistence(dataStorageLink.getDataStorageId(), dataStorageLink.getPath())) {
            throw new IllegalStateException(
                    messageHelper.getMessage(MessageConstants.ERROR_NGS_PREPROCESSING_FOLDER_SHOULD_HAVE_DATA_PATH,
                    dataFolderMetadataKey + TAG_KEY_VALUE_DELIMITER + dataPath.getValue()));
        }
        return dataStorageLink;
    }

    private MetadataEntry fetchFolderMetadata(final Folder folder) {
        final MetadataEntry folderMetadata = metadataManager.listMetadataItems(
                        Collections.singletonList(new EntityVO(folder.getId(), AclClass.FOLDER))).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        messageHelper.getMessage(
                                MessageConstants.ERROR_NGS_PREPROCESSING_FOLDER_HAS_NO_METADATA,
                                folder.getId())));

        checkFolderAttribute(preferenceManager.getPreference(SystemPreferences.UI_PROJECT_INDICATOR), folderMetadata);
        checkFolderAttribute(
                preferenceManager.getPreference(SystemPreferences.UI_NGS_PROJECT_INDICATOR), folderMetadata);

        return folderMetadata;
    }

    private void checkFolderAttribute(final String attribute, final MetadataEntry folderMetadata) {
        final Set<Pair<String, String>> projectIndicators = SystemPreferenceUtils.parseProjectIndicator(attribute);
        projectIndicators.forEach(indicator -> {
            final PipeConfValue projectAttribute = folderMetadata.getData().get(indicator.getKey());
            Assert.state(projectAttribute != null
                            && projectAttribute.getValue().equals(indicator.getValue()),
                    messageHelper.getMessage(
                            MessageConstants.ERROR_NGS_PREPROCESSING_FOLDER_SHOULD_HAVE_METADATA,
                            projectIndicators
                    ));
        });
    }

    private List<MetadataEntityVO> mapSampleSheetToMetadataEntities(final Long folderId,
                                                                    final MetadataEntity machineRun,
                                                                    final SampleSheet sampleSheet,
                                                                    final MetadataClass sampleMetadataClass) {
        final List<String> dataHeader = sampleSheet.getDataHeader();
        final int sampleIdIndex = dataHeader.indexOf(SampleSheetParser.SAMPLE_ID_COLUMN);
        Assert.state(sampleIdIndex != -1,
                messageHelper.getMessage(MessageConstants.ERROR_NGS_PREPROCESSING_SAMPLE_ID_NOT_FOUND));
        final int laneIndex = dataHeader.indexOf(SampleSheetParser.LANE_COLUMN);

        final List<MetadataEntityVO> result = new ArrayList<>();
        for (int i = 0; i < sampleSheet.getDataLines().size(); i++) {
            final String l = sampleSheet.getDataLines().get(i);
            final List<String> fields = Arrays.asList(l.split(SampleSheetParser.SAMPLESHEET_DELIMITER));

            final MetadataEntityVO entityVO = new MetadataEntityVO();

            entityVO.setClassName(sampleMetadataClass.getName());
            entityVO.setClassId(sampleMetadataClass.getId());
            entityVO.setParentId(folderId);
            if (laneIndex < 0) {
                entityVO.setExternalId(
                        String.join(NAME_DELIMITER, machineRun.getExternalId(), fields.get(sampleIdIndex),
                                SAMPLE_PREFIX, Integer.toString(i))
                );
            } else {
                entityVO.setExternalId(
                        String.join(NAME_DELIMITER, machineRun.getExternalId(), fields.get(sampleIdIndex),
                                SAMPLE_PREFIX, Integer.toString(i), LANE_PREFIX, fields.get(laneIndex))
                );
            }
            final Map<String, PipeConfValue> data = new HashMap<>();
            for (int j = 0; j < dataHeader.size(); j++) {
                data.put(dataHeader.get(j),
                        new PipeConfValue(
                                PipeConfValueType.STRING.toString(),
                                fields.size() > j ? fields.get(j) : StringUtils.EMPTY
                        )
                );
            }
            data.put(
                    preferenceManager.getPreference(SystemPreferences.PREPROCESSING_MACHINE_RUN_COLUMN_NAME),
                    new PipeConfValue(
                            machineRun.getClassEntity().getName() +  EntityTypeField.NAME_DELIMITER
                                    + EntityTypeField.REFERENCE_SUFFIX,
                            machineRun.getExternalId()
                    )
            );
            entityVO.setData(data);
            result.add(entityVO);
        }

        return result;
    }

    private boolean checkPathExistence(final Long dataStorageId, final String path) {
        try {
            // if we can list it, it should exist
            storageManager.getDataStorageItems(dataStorageId, path, false, 1, null, false);
            return true;
        } catch (RuntimeException e) {
            log.debug("Fail to list storage", e);
            return false;
        }
    }
}
