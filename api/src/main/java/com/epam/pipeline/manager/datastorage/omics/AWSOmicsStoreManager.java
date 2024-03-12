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

package com.epam.pipeline.manager.datastorage.omics;

import com.amazonaws.services.omics.model.ListReadSetImportJobsResult;
import com.amazonaws.services.omics.model.ListReferenceImportJobsResult;
import com.amazonaws.services.omics.model.StartReadSetImportJobResult;
import com.amazonaws.services.omics.model.StartReferenceImportJobResult;
import com.epam.pipeline.entity.datastorage.aws.AWSDataStorage;
import com.epam.pipeline.entity.datastorage.aws.AWSOmicsDataStorage;
import com.epam.pipeline.entity.datastorage.aws.AWSOmicsReferenceDataStorage;
import com.epam.pipeline.entity.datastorage.aws.AWSOmicsSequenceDataStorage;
import com.epam.pipeline.entity.datastorage.omics.*;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.entity.region.AwsRegionCredentials;
import com.epam.pipeline.manager.datastorage.providers.aws.omics.OmicsHelper;
import com.epam.pipeline.manager.region.CloudRegionManager;
import lombok.AllArgsConstructor;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class AWSOmicsStoreManager {

    private final CloudRegionManager cloudRegionManager;

    public AWSOmicsFileImportJob importOmicsFiles(final AWSOmicsDataStorage storage,
                                                  final AWSOmicsFileImportJob importJob) {
        final AwsRegion region = getAwsRegion(storage);
        if (storage instanceof AWSOmicsSequenceDataStorage) {
            return map(getOmicsHelper(storage, region).importSeqOmicsFile(storage, importJob));
        } else if (storage instanceof AWSOmicsReferenceDataStorage) {
            return map(getOmicsHelper(storage, region).importRefOmicsFile(storage, importJob));
        } else {
            throw new IllegalArgumentException("Unsupported storage type: " + storage.getType());
        }
    }

    public AWSOmicsFileImportJobListing listImportJobs(final AWSOmicsDataStorage storage, final String nextToken,
                                                       final Integer maxResults,
                                                       final AWSOmicsFileImportJobFilter filter) {
        final AwsRegion region = getAwsRegion(storage);
        if (storage instanceof AWSOmicsSequenceDataStorage) {
            return map(getOmicsHelper(storage, region).listSeqOmicsImportJobs(storage, nextToken, maxResults, filter));
        } else if (storage instanceof AWSOmicsReferenceDataStorage) {
            return map(getOmicsHelper(storage, region).listRefOmicsImportJobs(storage, nextToken, maxResults, filter));
        } else {
            throw new IllegalArgumentException("Unsupported storage type: " + storage.getType());
        }
    }

    protected OmicsHelper getOmicsHelper(final AWSDataStorage dataStorage, final AwsRegion region) {
        if (dataStorage.isUseAssumedCredentials()) {
            final String roleArn = Optional.ofNullable(dataStorage.getTempCredentialsRole())
                    .orElse(region.getTempCredentialsRole());
            return new OmicsHelper(region, roleArn);
        }
        if (StringUtils.isNotBlank(region.getIamRole())) {
            return new OmicsHelper(region, region.getIamRole());
        }
        return new OmicsHelper(region, getAwsCredentials(region));
    }

    private AwsRegion getAwsRegion(final AWSDataStorage dataStorage) {
        return cloudRegionManager.getAwsRegion(dataStorage);
    }

    private AwsRegionCredentials getAwsCredentials(final AwsRegion region) {
        return cloudRegionManager.loadCredentials(region);
    }

    private AWSOmicsFileImportJob map(final StartReadSetImportJobResult startReadSetImportJobResult) {
        return AWSOmicsFileImportJob.builder()
                .id(startReadSetImportJobResult.getId())
                .storeId(startReadSetImportJobResult.getSequenceStoreId())
                .serviceRoleArn(startReadSetImportJobResult.getRoleArn())
                .status(AWSOmicsFileImportJobStatus.valueOf(startReadSetImportJobResult.getStatus()))
                .creationTime(startReadSetImportJobResult.getCreationTime())
                .build();
    }

    private AWSOmicsFileImportJob map(final StartReferenceImportJobResult startReadSetImportJobResult) {
        return AWSOmicsFileImportJob.builder()
                .id(startReadSetImportJobResult.getId())
                .storeId(startReadSetImportJobResult.getReferenceStoreId())
                .serviceRoleArn(startReadSetImportJobResult.getRoleArn())
                .status(AWSOmicsFileImportJobStatus.valueOf(startReadSetImportJobResult.getStatus()))
                .creationTime(startReadSetImportJobResult.getCreationTime())
                .build();
    }

    private AWSOmicsFileImportJobListing map(final ListReferenceImportJobsResult listReferenceImportJobsResult) {
        return AWSOmicsFileImportJobListing.builder()
            .jobs(
                    ListUtils.emptyIfNull(listReferenceImportJobsResult.getImportJobs()).stream().map(item ->
                    AWSOmicsFileImportJob.builder()
                        .id(item.getId())
                        .storeId(item.getReferenceStoreId())
                        .serviceRoleArn(item.getRoleArn())
                        .status(AWSOmicsFileImportJobStatus.valueOf(item.getStatus()))
                        .creationTime(item.getCreationTime())
                        .build()
                ).collect(Collectors.toList())
            )
            .nextToken(listReferenceImportJobsResult.getNextToken())
            .build();
    }

    private AWSOmicsFileImportJobListing map(final ListReadSetImportJobsResult listReadSetImportJobsResult) {
        return AWSOmicsFileImportJobListing.builder()
            .jobs(
                    ListUtils.emptyIfNull(listReadSetImportJobsResult.getImportJobs()).stream().map(item ->
                    AWSOmicsFileImportJob.builder()
                        .id(item.getId())
                        .storeId(item.getSequenceStoreId())
                        .serviceRoleArn(item.getRoleArn())
                        .status(AWSOmicsFileImportJobStatus.valueOf(item.getStatus()))
                        .creationTime(item.getCreationTime())
                        .build()
                ).collect(Collectors.toList())
            )
            .nextToken(listReadSetImportJobsResult.getNextToken())
            .build();
    }
}
