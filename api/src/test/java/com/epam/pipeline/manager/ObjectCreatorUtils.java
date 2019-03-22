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

package com.epam.pipeline.manager;

import com.epam.pipeline.controller.vo.DataStorageVO;
import com.epam.pipeline.controller.vo.PipelineVO;
import com.epam.pipeline.controller.vo.configuration.RunConfigurationVO;
import com.epam.pipeline.controller.vo.metadata.MetadataEntityVO;
import com.epam.pipeline.entity.configuration.AbstractRunConfigurationEntry;
import com.epam.pipeline.entity.configuration.FirecloudRunConfigurationEntry;
import com.epam.pipeline.entity.configuration.InputsOutputs;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.configuration.RunConfiguration;
import com.epam.pipeline.entity.configuration.RunConfigurationEntry;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.StoragePolicy;
import com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage;
import com.epam.pipeline.entity.metadata.MetadataClass;
import com.epam.pipeline.entity.metadata.MetadataEntity;
import com.epam.pipeline.entity.metadata.MetadataFilter;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import com.epam.pipeline.entity.pipeline.CommitStatus;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.entity.region.AzureRegion;
import com.epam.pipeline.entity.region.AzureRegionCredentials;
import com.epam.pipeline.entity.region.CloudProvider;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

public final class ObjectCreatorUtils {

    private static final String TEST_REVISION_1 = "testtest1";
    private static final String TEST_NAME = "TEST";
    private static final String TEST_POD_ID = "pod1";
    private static final String TEST_SERVICE_URL = "service_url";

    private ObjectCreatorUtils() {
    }

    public static DataStorageVO constructDataStorageVO(String name, String description, DataStorageType storageType,
                                                       String path, Integer stsDuration, Integer ltsDuration,
                                                       Long parentFolderId, String mountPoint, String mountOptions) {
        DataStorageVO storageVO = constructDataStorageVO(name, description, storageType, path, parentFolderId,
                mountPoint, mountOptions);

        StoragePolicy policy = new StoragePolicy();
        if (stsDuration != null) {
            policy.setShortTermStorageDuration(stsDuration);
        }
        if (ltsDuration != null) {
            policy.setLongTermStorageDuration(ltsDuration);
        }
        storageVO.setStoragePolicy(policy);

        return storageVO;
    }

    public static DataStorageVO constructDataStorageVO(String name, String description, DataStorageType storageType,
                                                       String path, Long parentFolderId, String mountPoint,
                                                       String mountOptions) {
        DataStorageVO storageVO = new DataStorageVO();
        if (name != null) {
            storageVO.setName(name);
        }
        if (path != null) {
            storageVO.setPath(path);
        }
        if (description != null) {
            storageVO.setDescription(description);
        }
        if (storageType != null) {
            storageVO.setType(storageType);
        }
        storageVO.setMountOptions(mountOptions);
        storageVO.setMountPoint(mountPoint);

        if (parentFolderId != null) {
            storageVO.setParentFolderId(parentFolderId);
        }
        return storageVO;
    }

    public static PipelineVO constructPipelineVO(String name, String repo, Long parentFolderId) {
        PipelineVO pipeline = new PipelineVO();
        pipeline.setName(name);
        pipeline.setRepository(repo);
        pipeline.setParentFolderId(parentFolderId);
        return pipeline;
    }


    public static Pipeline constructPipeline(String name, String repo, Long parentFolderId) {
        Pipeline pipeline = new Pipeline();
        pipeline.setName(name);
        pipeline.setRepository(repo);
        pipeline.setParentFolderId(parentFolderId);
        return pipeline;
    }

    public static RunConfiguration createConfiguration(String name, String description,
                                                       Long parentFolderId, String owner,
                                                       List<AbstractRunConfigurationEntry> entries) {
        RunConfiguration configuration = new RunConfiguration();
        configuration.setName(name);
        if (parentFolderId != null) {
            configuration.setParent(new Folder(parentFolderId));
        }
        configuration.setDescription(description);
        configuration.setOwner(owner);
        configuration.setEntries(entries);
        return configuration;
    }

    public static RunConfigurationEntry createConfigEntry(String name, boolean defaultEntry,
                                                                  PipelineConfiguration configuration) {
        RunConfigurationEntry entry = new RunConfigurationEntry();
        entry.setName(name);
        entry.setDefaultConfiguration(defaultEntry);
        entry.setConfiguration(configuration);
        return entry;
    }

    public static FirecloudRunConfigurationEntry createFirecloudConfigEntry(String name,
                                                                            List<InputsOutputs> inputs,
                                                                            List<InputsOutputs> outputs,
                                                                            String methodName, String methodSnapshot,
                                                                            String configName) {
        FirecloudRunConfigurationEntry entry = new FirecloudRunConfigurationEntry();
        entry.setName(name);
        entry.setMethodInputs(inputs);
        entry.setMethodOutputs(outputs);
        entry.setMethodName(methodName);
        entry.setMethodSnapshot(methodSnapshot);
        entry.setMethodConfigurationName(configName);
        return entry;
    }

    public static RunConfigurationVO createRunConfigurationVO(String name, String description, Long parentFolderId,
                                                              List<AbstractRunConfigurationEntry> entries) {
        RunConfigurationVO runConfigurationVO = new RunConfigurationVO();
        runConfigurationVO.setName(name);
        runConfigurationVO.setDescription(description);
        runConfigurationVO.setParentId(parentFolderId);
        runConfigurationVO.setEntries(entries);
        return runConfigurationVO;
    }

    public static Folder createFolder(String name, Long parentId) {
        Folder folder = new Folder();
        folder.setName(name);
        folder.setParentId(parentId);
        return folder;
    }

    public static MetadataClass createMetadataClass(String name) {
        MetadataClass metadataClass = new MetadataClass();
        metadataClass.setName(name);
        return metadataClass;
    }

    public static MetadataEntity createMetadataEntity(Folder folder, MetadataClass metadataClass, String name,
                                                      String externalId, Map<String, PipeConfValue> data) {
        MetadataEntity metadataEntity = new MetadataEntity();
        metadataEntity.setName(name);
        metadataEntity.setClassEntity(metadataClass);
        metadataEntity.setExternalId(externalId);
        metadataEntity.setParent(folder);
        metadataEntity.setData(data);
        return metadataEntity;
    }

    public static MetadataEntityVO createMetadataEntityVo(Long classId, Long parentId,
                                                          String name, String externalId,
                                                          Map<String, PipeConfValue> data) {
        MetadataEntityVO vo = new MetadataEntityVO();
        vo.setClassId(classId);
        vo.setEntityName(name);
        vo.setExternalId(externalId);
        vo.setParentId(parentId);
        vo.setData(data);
        return vo;
    }

    public static MetadataFilter createMetadataFilter(String metadataClass, Long folderId,
                                                      Integer pageSize, Integer page) {
        MetadataFilter filter = new MetadataFilter();
        filter.setFolderId(folderId);
        filter.setMetadataClass(metadataClass);
        filter.setPage(page);
        filter.setPageSize(pageSize);
        return filter;
    }

    public static S3bucketDataStorage clone(S3bucketDataStorage s3Bucket) {
        S3bucketDataStorage storage = new S3bucketDataStorage(
                s3Bucket.getId(), s3Bucket.getName(), s3Bucket.getPath(),
                s3Bucket.getStoragePolicy(), s3Bucket.getMountPoint());
        storage.setParentFolderId(s3Bucket.getParentFolderId());
        storage.setParent(s3Bucket.getParent());
        storage.setMountOptions(s3Bucket.getMountOptions());
        storage.setOwner(s3Bucket.getOwner());
        storage.setDescription(s3Bucket.getDescription());
        storage.setRegionId(s3Bucket.getRegionId());
        storage.setAllowedCidrs(new ArrayList<>(s3Bucket.getAllowedCidrs()));
        return storage;
    }

    public static S3bucketDataStorage createS3Bucket(Long id, String name, String path, String owner) {
        S3bucketDataStorage s3bucketDataStorage = new S3bucketDataStorage(id, name, path);
        s3bucketDataStorage.setOwner(owner);
        return s3bucketDataStorage;
    }

    public static PipelineRun createPipelineRun(Long runId, Long pipelineId,
                                                Long parentRunId, Long regionId) {
        PipelineRun run = new PipelineRun();
        run.setId(runId);
        run.setPipelineId(pipelineId);
        run.setVersion(TEST_REVISION_1);
        run.setStartDate(new Date());
        run.setEndDate(new Date());
        run.setStatus(TaskStatus.RUNNING);
        run.setCommitStatus(CommitStatus.NOT_COMMITTED);
        run.setLastChangeCommitTime(new Date());
        run.setPodId(TEST_POD_ID);
        run.setOwner(TEST_NAME);
        run.setParentRunId(parentRunId);
        run.setServiceUrl(TEST_SERVICE_URL);

        RunInstance instance = new RunInstance();
        instance.setSpot(true);
        instance.setNodeId("1");
        instance.setCloudRegionId(regionId);
        instance.setCloudProvider(CloudProvider.AWS);
        run.setInstance(instance);
        return run;
    }

    public static AwsRegion getDefaultAwsRegion() {
        AwsRegion region = new AwsRegion();
        region.setRegionCode("us-east-1");
        region.setName("US East");
        region.setDefault(true);
        return region;
    }

    public static AzureRegion getDefaultAzureRegion(final String resourceGroup,
                                                    final String storageAcc) {
        AzureRegion region = new AzureRegion();
        region.setDefault(true);
        region.setProvider(CloudProvider.AZURE);
        region.setResourceGroup(resourceGroup);
        region.setStorageAccount(storageAcc);
        return region;
    }

    public static AzureRegionCredentials getAzureCredentials(final String storageKey) {
        AzureRegionCredentials creds = new AzureRegionCredentials();
        creds.setStorageAccountKey(storageKey);
        return creds;
    }
}