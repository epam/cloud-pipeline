/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.datastorage.lustre;

import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.fsx.AmazonFSx;
import com.amazonaws.services.fsx.AmazonFSxClient;
import com.amazonaws.services.fsx.model.CreateFileSystemLustreConfiguration;
import com.amazonaws.services.fsx.model.CreateFileSystemRequest;
import com.amazonaws.services.fsx.model.CreateFileSystemResult;
import com.amazonaws.services.fsx.model.DeleteFileSystemRequest;
import com.amazonaws.services.fsx.model.DescribeFileSystemsRequest;
import com.amazonaws.services.fsx.model.DescribeFileSystemsResult;
import com.amazonaws.services.fsx.model.FileSystem;
import com.amazonaws.services.fsx.model.FileSystemType;
import com.amazonaws.services.fsx.model.LustreDeploymentType;
import com.amazonaws.services.fsx.model.StorageType;
import com.amazonaws.services.fsx.model.Tag;
import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.cluster.CloudRegionsConfiguration;
import com.epam.pipeline.entity.cluster.NetworkConfiguration;
import com.epam.pipeline.entity.datastorage.LustreFS;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.exception.datastorage.exception.LustreFSException;
import com.epam.pipeline.manager.cloud.aws.AWSUtils;
import com.epam.pipeline.manager.cloud.aws.EC2Helper;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.region.CloudRegionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class LustreFSManager {

    private static final String RUN_ID_TAG_NAME = "RUN_ID";
    private static final String LUSTRE_MOUNT_TEMPLATE = "%s@tcp:/%s";
    private static final int LUSTRE_MIN_SIZE_GB = 1200;
    private static final int LUSTRE_SIZE_THRESHOLD_GB = 2400;
    private static final int LUSTRE_SIZE_STEP_SCRATCH_1 = 3600;
    private static final int LUSTRE_SIZE_STEP_SCRATCH_2 = 2400;

    private final PipelineRunManager runManager;
    private final CloudRegionManager regionManager;
    private final PreferenceManager preferenceManager;
    private final EC2Helper ec2Helper;
    private final MessageHelper messageHelper;

    public LustreFS getOrCreateLustreFS(final Long runId, final Integer size) {
        final AmazonFSx fsxClient = buildFsxClient(runId);
        return findFsForRun(runId, null, fsxClient)
                .map(this::convert)
                .orElseGet(() -> createLustreFs(runId, size, fsxClient));
    }

    public LustreFS getLustreFS(final Long runId) {
        final AmazonFSx fsxClient = buildFsxClient(runId);
        return findFsForRun(runId, null, fsxClient)
                .map(this::convert)
                .orElseThrow(() -> new LustreFSException(
                        messageHelper.getMessage(MessageConstants.ERROR_LUSTRE_NOT_FOUND, runId)));
    }

    public LustreFS deleteLustreFs(final Long runId) {
        final AmazonFSx fsxClient = buildFsxClient(runId);
        return findFsForRun(runId, null, fsxClient)
                .map(fs -> deleteFs(fs, fsxClient))
                .orElseThrow(() -> new LustreFSException(
                        messageHelper.getMessage(MessageConstants.ERROR_LUSTRE_NOT_FOUND, runId)));
    }

    private LustreFS deleteFs(final FileSystem fs, final AmazonFSx fsxClient) {
        log.debug("Deleting lustre fs with id {}.", fs.getFileSystemId());
        fsxClient.deleteFileSystem(new DeleteFileSystemRequest()
                .withFileSystemId(fs.getFileSystemId()));
        return convert(fs);
    }

    private LustreFS createLustreFs(final Long runId, final Integer size, final AmazonFSx fsxClient) {
        log.debug("Creating a new lustre fs for run id {}.", runId);
        final PipelineRun pipelineRun = runManager.loadPipelineRun(runId);
        final AwsRegion regionForRun = getRegion(pipelineRun);
        final LustreDeploymentType deploymentType = getDeploymentType();
        final CreateFileSystemLustreConfiguration lustreConfiguration = getLustreConfig(deploymentType);
        final CreateFileSystemRequest createFileSystemRequest = new CreateFileSystemRequest()
                .withFileSystemType(FileSystemType.LUSTRE)
                .withStorageCapacity(getFSSize(size, deploymentType))
                .withStorageType(StorageType.SSD)
                .withSecurityGroupIds(getSecurityGroups(regionForRun))
                .withSubnetIds(getSubnetId(pipelineRun, regionForRun))
                .withTags(new Tag().withKey(RUN_ID_TAG_NAME).withValue(String.valueOf(runId)))
                .withLustreConfiguration(lustreConfiguration);
        if (isPersistent(deploymentType)) {
            createFileSystemRequest.withKmsKeyId(regionForRun.getKmsKeyArn());
        }
        final CreateFileSystemResult result = fsxClient.createFileSystem(createFileSystemRequest);
        final FileSystem fs = result.getFileSystem();
        if (fs.getLifecycle().equals("FAILED")) {
            log.debug("Lustre fs creation failed: {}", fs);
            throw new LustreFSException(messageHelper.getMessage(MessageConstants.ERROR_LUSTRE_NOT_CREATED, runId));
        }
        return convert(fs);
    }

    private boolean isPersistent(final LustreDeploymentType deploymentType) {
        return LustreDeploymentType.PERSISTENT_1.equals(deploymentType);
    }

    private CreateFileSystemLustreConfiguration getLustreConfig(final LustreDeploymentType deploymentType) {
        final CreateFileSystemLustreConfiguration lustreConfiguration = new CreateFileSystemLustreConfiguration()
                .withDeploymentType(deploymentType);
        if (isPersistent(deploymentType)) {
            lustreConfiguration
                    .withPerUnitStorageThroughput(preferenceManager.getPreference(
                            SystemPreferences.LUSTRE_FS_DEFAULT_THROUGHPUT))
                    .withCopyTagsToBackups(false)
                    .withAutomaticBackupRetentionDays(preferenceManager.getPreference(
                            SystemPreferences.LUSTRE_FS_BKP_RETENTION_DAYS));
        }
        return lustreConfiguration;
    }

    private LustreDeploymentType getDeploymentType() {
        return LustreDeploymentType.valueOf(
                preferenceManager.getPreference(SystemPreferences.LUSTRE_FS_DEPLOYMENT_TYPE));
    }

    private String getSubnetId(final PipelineRun run, final AwsRegion region) {
        final Instance instance = ec2Helper.findInstance(run.getInstance().getNodeId(), region.getRegionCode())
                .orElseThrow(() -> new LustreFSException(messageHelper.getMessage(
                        MessageConstants.ERROR_LUSTRE_MISSING_INSTANCE, run.getInstance().getNodeId(), run.getId())));
        return instance.getSubnetId();
    }

    private List<String> getSecurityGroups(final AwsRegion regionForRun) {
        final CloudRegionsConfiguration networkSettings = preferenceManager.getPreference(
                SystemPreferences.CLUSTER_NETWORKS_CONFIG);
        final NetworkConfiguration settings = ListUtils.emptyIfNull(networkSettings.getRegions())
                .stream()
                .filter(region -> regionForRun.getId().equals(region.getRegionId()) ||
                        regionForRun.getRegionCode().equals(region.getName()))
                .findFirst()
                .orElseThrow(() -> new LustreFSException(
                        messageHelper.getMessage(MessageConstants.ERROR_LUSTRE_MISSING_CONFIG)));
        return settings.getSecurityGroups();
    }

    private int getFSSize(final Integer size, final LustreDeploymentType deploymentType) {
//        For SCRATCH_2 and PERSISTENT_1 SSD deployment types, valid values are 1200 GiB, 2400 GiB,
//        and increments of 2400 GiB.
//        For SCRATCH_1 deployment type, valid values are 1200 GiB, 2400 GiB, and increments of 3600 GiB.
        final Integer initialSize = Optional.ofNullable(size)
                .orElseGet(() -> preferenceManager.getPreference(SystemPreferences.LUSTRE_FS_DEFAULT_SIZE_GB));
        if (initialSize <= LUSTRE_MIN_SIZE_GB) {
            return LUSTRE_MIN_SIZE_GB;
        }
        if (initialSize <= LUSTRE_SIZE_THRESHOLD_GB) {
            return LUSTRE_SIZE_THRESHOLD_GB;
        }
        final int sizeStep = deploymentType.equals(LustreDeploymentType.SCRATCH_1) ?
                LUSTRE_SIZE_STEP_SCRATCH_1 : LUSTRE_SIZE_STEP_SCRATCH_2;
        if (initialSize % sizeStep == 0) {
            return initialSize;
        }
        return sizeStep * (1 + initialSize / sizeStep);
    }

    private LustreFS convert(final FileSystem lustre) {
        return LustreFS.builder()
                .id(lustre.getFileSystemId())
                .mountPath(String.format(LUSTRE_MOUNT_TEMPLATE, lustre.getDNSName(),
                        lustre.getLustreConfiguration().getMountName()))
                .status(lustre.getLifecycle())
                .mountOptions(preferenceManager.getPreference(SystemPreferences.LUSTRE_FS_MOUNT_OPTIONS))
                .build();
    }

    private Optional<FileSystem> findFsForRun(final Long runId, final String token, final AmazonFSx fsxClient) {
        final DescribeFileSystemsResult result = fsxClient.describeFileSystems(
                new DescribeFileSystemsRequest()
                        .withNextToken(token));
        final Optional<FileSystem> lustre = ListUtils.emptyIfNull(result.getFileSystems())
                .stream()
                .filter(fs -> FileSystemType.LUSTRE.name().equals(fs.getFileSystemType()) && hasRunTag(runId, fs))
                .findFirst();
        if (lustre.isPresent()) {
            log.debug("Found a lustre fs with id {} for run {}.", lustre.get().getFileSystemId(), runId);
            return lustre;
        }
        if (StringUtils.isNotBlank(result.getNextToken())) {
            return findFsForRun(runId, result.getNextToken(), fsxClient);
        }
        return Optional.empty();
    }

    private boolean hasRunTag(Long runId, FileSystem fileSystem) {
        return ListUtils.emptyIfNull(fileSystem.getTags())
                .stream()
                .anyMatch(tag -> RUN_ID_TAG_NAME.equals(tag.getKey()) && String.valueOf(runId).equals(tag.getValue()));
    }

    private AmazonFSx buildFsxClient(Long runId) {
        final AwsRegion region = getRegionForRun(runId);
        return AmazonFSxClient.builder()
                .withCredentials(AWSUtils.getCredentialsProvider(region.getProfile()))
                .withRegion(region.getRegionCode())
                .build();
    }

    private AwsRegion getRegionForRun(Long runId) {
        final PipelineRun pipelineRun = runManager.loadPipelineRun(runId);
        return getRegion(pipelineRun);
    }

    private AwsRegion getRegion(PipelineRun pipelineRun) {
        final Long cloudRegionId = pipelineRun.getInstance().getCloudRegionId();
        final AbstractCloudRegion region = regionManager.load(cloudRegionId);
        if (region instanceof AwsRegion) {
            return (AwsRegion)region;
        } else {
            throw new LustreFSException(messageHelper.getMessage(MessageConstants.ERROR_LUSTRE_REGION_NOT_SUPPORTED));
        }
    }
}
