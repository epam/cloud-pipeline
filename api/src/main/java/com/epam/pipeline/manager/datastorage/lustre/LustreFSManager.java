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

import com.amazonaws.services.fsx.AmazonFSx;
import com.amazonaws.services.fsx.AmazonFSxClient;
import com.amazonaws.services.fsx.model.*;
import com.epam.pipeline.entity.datastorage.LustreFS;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.manager.cloud.aws.AWSUtils;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.region.CloudRegionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class LustreFSManager {

    private static final String RUN_ID_TAG_NAME = "RUN_ID";
    private static final String LUSTRE_MOUNT_TEMPLATE = "%s@tcp:/%s";

    private final PipelineRunManager runManager;
    private final CloudRegionManager regionManager;
    private final PreferenceManager preferenceManager;

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
                .orElseThrow(() -> new IllegalArgumentException("Failed to find lustre Fs for run id"));
    }

    public LustreFS deleteLustreFs(final Long runId) {
        final AmazonFSx fsxClient = buildFsxClient(runId);
        return findFsForRun(runId, null, fsxClient)
                .map(fs -> deleteFs(fs, fsxClient))
                .orElseThrow(() -> new IllegalArgumentException("Failed to find lustre Fs for run id"));
    }

    private LustreFS deleteFs(final FileSystem fs, final AmazonFSx fsxClient) {
        log.debug("Deleting lustre fs with id {}.", fs.getFileSystemId());
        fsxClient.deleteFileSystem(new DeleteFileSystemRequest()
                .withFileSystemId(fs.getFileSystemId()));
        return convert(fs);
    }

    private LustreFS createLustreFs(final Long runId, final Integer size, final AmazonFSx fsxClient) {
        log.debug("Creating a new lustre fs for run id {}.", runId);
        final AwsRegion regionForRun = getRegionForRun(runId);
        preferenceManager.getPreference(SystemPreferences.CLUSTER_NETWORKS_CONFIG);
        final CreateFileSystemResult result = fsxClient.createFileSystem(new CreateFileSystemRequest()
                .withFileSystemType(FileSystemType.LUSTRE)
                .withStorageCapacity(Optional.ofNullable(size)
                        .orElseGet(() -> preferenceManager.getPreference(SystemPreferences.LUSTRE_FS_DEFAULT_SIZE)))
                .withStorageType(StorageType.SSD)
                .withSecurityGroupIds(preferenceManager.getPreference(SystemPreferences.LUSTRE_FS_SECURITY_GROUPS))
                .withSubnetIds(preferenceManager.getPreference(SystemPreferences.LUSTRE_FS_VPC_ID))
                .withKmsKeyId(regionForRun.getKmsKeyArn())
                .withTags(new Tag().withKey(RUN_ID_TAG_NAME).withValue(String.valueOf(runId)))
                .withLustreConfiguration(new CreateFileSystemLustreConfiguration()
                        .withCopyTagsToBackups(false)
                        .withAutomaticBackupRetentionDays(preferenceManager.getPreference(
                                SystemPreferences.LUSTRE_FS_BKP_RETENTION_DAYS))
                        .withDeploymentType(LustreDeploymentType.PERSISTENT_1)
                        .withPerUnitStorageThroughput(preferenceManager.getPreference(
                                SystemPreferences.LUSTRE_FS_DEFAULT_THROUGHPUT))));
        final FileSystem fs = result.getFileSystem();
        if (fs.getLifecycle().equals("FAILED")) {
            log.debug("Lustre fs creation failed: {}", fs);
            throw new IllegalArgumentException("Failed to created Lustre FS");
        }
        return convert(fs);
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
        DescribeFileSystemsResult result = fsxClient.describeFileSystems(
                new DescribeFileSystemsRequest()
                        .withNextToken(token));
        for (final FileSystem fileSystem : ListUtils.emptyIfNull(result.getFileSystems())) {
            if (FileSystemType.LUSTRE.name().equals(fileSystem.getFileSystemType()) &&
                    hasRunTag(runId, fileSystem)) {
                log.debug("Found a lustre fs with id {} for run {}.", fileSystem.getFileSystemId(), runId);
                return Optional.of(fileSystem);
            }
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
        final Long cloudRegionId = pipelineRun.getInstance().getCloudRegionId();
        final AbstractCloudRegion region = regionManager.load(cloudRegionId);
        if (region instanceof AwsRegion) {
            return (AwsRegion)region;
        } else {
            throw new IllegalArgumentException("Lustre FS is supported for AWS regions only");
        }
    }
}
