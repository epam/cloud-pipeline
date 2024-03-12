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

package com.epam.pipeline.manager.datastorage.providers.aws.omics;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider;
import com.amazonaws.services.omics.AmazonOmics;
import com.amazonaws.services.omics.AmazonOmicsClientBuilder;
import com.amazonaws.services.omics.model.*;
import com.epam.pipeline.entity.datastorage.DataStorageException;
import com.epam.pipeline.entity.datastorage.aws.AWSOmicsDataStorage;
import com.epam.pipeline.entity.datastorage.aws.AWSOmicsReferenceDataStorage;
import com.epam.pipeline.entity.datastorage.aws.AWSOmicsSequenceDataStorage;
import com.epam.pipeline.entity.datastorage.omics.AWSOmicsFileImportJob;
import com.epam.pipeline.entity.datastorage.omics.AWSOmicsFileImportJobFilter;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.entity.region.AwsRegionCredentials;
import com.epam.pipeline.manager.cloud.aws.AWSUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class OmicsHelper {
    private static final String REF_ID_SHOULD_NOT_BE_EMPTY_MESSAGE = "referenceId should not be empty";
    private static final String READSET_ID_SHOULD_NOT_BE_EMPTY_MESSAGE = "readSetId should not be empty";
    private static final String REFERENCE_IS_NOT_FOUND_MESSAGE = "Reference is not found by referenceId '%s'";
    private static final String READSET_IS_NOT_FOUND_MESSAGE = "ReadSet is not found by id '%s'";
    private static final String SSE_CONFIG_TYPE = "KMS";

    private final AwsRegion region;
    private final String roleArn;
    private final AwsRegionCredentials credentials;

    public OmicsHelper(final AwsRegion region, final String roleArn) {
        this(region, roleArn, null);
    }

    public OmicsHelper(final AwsRegion region, final AwsRegionCredentials credentials) {
        this(region, null, credentials);
    }

    public CreateReferenceStoreResult registerOmicsRefStorage(final AWSOmicsReferenceDataStorage storage) {
        final CreateReferenceStoreRequest createReferenceStoreRequest = new CreateReferenceStoreRequest()
                .withName(storage.getName())
                .withDescription(storage.getDescription());
        if (region.getKmsKeyArn() != null) {
            SseConfig sseConfig = new SseConfig();
            sseConfig.setKeyArn(region.getKmsKeyArn());
            sseConfig.setType(SSE_CONFIG_TYPE);
            createReferenceStoreRequest.setSseConfig(sseConfig);
        }
        return omics().createReferenceStore(createReferenceStoreRequest);
    }

    public DeleteReferenceStoreResult deleteOmicsRefStorage(final AWSOmicsReferenceDataStorage storage) {
        return omics()
                .deleteReferenceStore(
                        new DeleteReferenceStoreRequest().withId(storage.getCloudStorageId())
                );
    }

    public CreateSequenceStoreResult registerOmicsSeqStorage(final AWSOmicsSequenceDataStorage storage) {
        final CreateSequenceStoreRequest createReferenceStoreRequest = new CreateSequenceStoreRequest()
                .withName(storage.getName())
                .withDescription(storage.getDescription());
        final String keyArn = Optional.ofNullable(storage.getKmsKeyArn()).orElse(region.getKmsKeyArn());
        if (keyArn != null) {
            createReferenceStoreRequest.setSseConfig(
                    new SseConfig().withKeyArn(keyArn).withType("KMS")
            );
        }
        return omics().createSequenceStore(createReferenceStoreRequest);
    }

    public DeleteSequenceStoreResult deleteOmicsSeqStorage(final AWSOmicsSequenceDataStorage storage) {
        return omics()
                .deleteSequenceStore(
                        new DeleteSequenceStoreRequest().withId(storage.getCloudStorageId())
                );
    }

    public GetReferenceMetadataResult getOmicsRefStorageFile(final AWSOmicsReferenceDataStorage dataStorage,
                                                             final String referenceId) {
        if (StringUtils.isBlank(referenceId)) {
            throw new DataStorageException(REF_ID_SHOULD_NOT_BE_EMPTY_MESSAGE);
        }
        try {
            return omics().getReferenceMetadata(
                    new GetReferenceMetadataRequest().withReferenceStoreId(dataStorage.getCloudStorageId())
                            .withId(referenceId)
            );
        } catch (ResourceNotFoundException e) {
            return null;
        }
    }

    public GetReadSetMetadataResult getOmicsSeqStorageFile(final AWSOmicsSequenceDataStorage dataStorage,
                                                           final String readSetId) {
        if (StringUtils.isBlank(readSetId)) {
            throw new DataStorageException(READSET_ID_SHOULD_NOT_BE_EMPTY_MESSAGE);
        }
        try {
            return omics().getReadSetMetadata(
                    new GetReadSetMetadataRequest().withSequenceStoreId(dataStorage.getCloudStorageId())
                            .withId(readSetId)
            );
        } catch (ResourceNotFoundException e) {
            return null;
        }
    }

    public void deleteOmicsRefStorageFile(final AWSOmicsReferenceDataStorage dataStorage, final String referenceId) {
        if (StringUtils.isBlank(referenceId)) {
            throw new DataStorageException(REF_ID_SHOULD_NOT_BE_EMPTY_MESSAGE);
        }
        if (doesReferenceExist(dataStorage, referenceId)) {
            throw new DataStorageException(String.format(REFERENCE_IS_NOT_FOUND_MESSAGE, referenceId));
        }
        omics().deleteReference(
                new DeleteReferenceRequest()
                        .withReferenceStoreId(dataStorage.getCloudStorageId())
                        .withId(referenceId)
        );
    }

    public void deleteOmicsSeqStorageFile(final AWSOmicsSequenceDataStorage dataStorage,
                                          final String sequenceId) {
        if (StringUtils.isBlank(sequenceId)) {
            throw new DataStorageException(READSET_ID_SHOULD_NOT_BE_EMPTY_MESSAGE);
        }
        if (doesReadSetExist(dataStorage, sequenceId)) {
            throw new DataStorageException(String.format(READSET_IS_NOT_FOUND_MESSAGE, sequenceId));
        }
        omics().batchDeleteReadSet(
                new BatchDeleteReadSetRequest()
                        .withSequenceStoreId(dataStorage.getCloudStorageId())
                        .withIds(sequenceId)
        );
    }

    public ListReferencesResult listReferences(final AWSOmicsReferenceDataStorage dataStorage,
                                               final Integer pageSize, final String marker) {
        return omics().listReferences(
                new ListReferencesRequest()
                        .withReferenceStoreId(dataStorage.getCloudStorageId())
                        .withMaxResults(pageSize)
                        .withNextToken(marker)
        );
    }

    public ListReadSetsResult listReadSets(final AWSOmicsSequenceDataStorage dataStorage,
                                           final Integer pageSize, final String marker) {
        return omics().listReadSets(
                new ListReadSetsRequest()
                        .withSequenceStoreId(dataStorage.getCloudStorageId())
                        .withMaxResults(pageSize)
                        .withNextToken(marker)
        );
    }

    public StartReadSetImportJobResult importSeqOmicsFile(final AWSOmicsDataStorage storage,
                                                          final AWSOmicsFileImportJob importJob) {
        return omics().startReadSetImportJob(
                new StartReadSetImportJobRequest()
                        .withSequenceStoreId(storage.getCloudStorageId())
                        .withSources(importJob.getSources().stream().map(item ->
                            new StartReadSetImportJobSourceItem()
                                    .withName(item.getName())
                                    .withDescription(item.getDescription())
                                    .withGeneratedFrom(item.getGeneratedFrom())
                                    .withReferenceArn(item.getReferenceArn())
                                    .withSampleId(item.getSampleId())
                                    .withSubjectId(item.getSubjectId())
                                    .withSourceFileType(item.getSourceFileType().getId())
                                    .withSourceFiles(
                                            new SourceFiles()
                                                    .withSource1(item.getSourceFiles().getSource1())
                                                    .withSource2(item.getSourceFiles().getSource2())
                                    )
                        ).collect(Collectors.toList()))
                        .withRoleArn(fetchAWSOmicsServiceRole(importJob))
        );
    }

    public StartReferenceImportJobResult importRefOmicsFile(final AWSOmicsDataStorage storage,
                                                            final AWSOmicsFileImportJob importJob) {
        return omics().startReferenceImportJob(
                new StartReferenceImportJobRequest()
                        .withReferenceStoreId(storage.getCloudStorageId())
                        .withSources(importJob.getSources().stream().map(item ->
                                new StartReferenceImportJobSourceItem()
                                        .withName(item.getName())
                                        .withDescription(item.getDescription())
                                        .withSourceFile(item.getSourceFiles().getSource1())
                        ).collect(Collectors.toList()))
                        .withRoleArn(fetchAWSOmicsServiceRole(importJob))
        );
    }

    public ListReadSetImportJobsResult listSeqOmicsImportJobs(final AWSOmicsDataStorage storage,
                                                              final String nextToken, final Integer maxResults,
                                                              final AWSOmicsFileImportJobFilter filter) {
        return omics().listReadSetImportJobs(
                new ListReadSetImportJobsRequest()
                        .withSequenceStoreId(storage.getCloudStorageId())
                        .withMaxResults(maxResults)
                        .withNextToken(nextToken)
                        .withFilter(
                                Optional.ofNullable(filter).map(awsOmicsFileImportJobFilter ->
                                        new ImportReadSetFilter()
                                                .withStatus(
                                                        Optional.ofNullable(filter.getStatus())
                                                                .map(Enum::name).orElse(null)
                                                ).withCreatedAfter(filter.getCreatedAfter())
                                                .withCreatedBefore(filter.getCreatedBefore())
                                ).orElse(null)
                        )
        );
    }

    public ListReferenceImportJobsResult listRefOmicsImportJobs(final AWSOmicsDataStorage storage,
                                                                final String nextToken, final Integer maxResults,
                                                                final AWSOmicsFileImportJobFilter filter) {
        return omics().listReferenceImportJobs(
                new ListReferenceImportJobsRequest()
                        .withReferenceStoreId(storage.getCloudStorageId())
                        .withMaxResults(maxResults)
                        .withNextToken(nextToken)
                        .withFilter(
                                Optional.ofNullable(filter).map(awsOmicsFileImportJobFilter ->
                                    new ImportReferenceFilter().withStatus(
                                                    Optional.ofNullable(filter.getStatus())
                                                            .map(Enum::name).orElse(null)
                                            ).withCreatedAfter(filter.getCreatedAfter())
                                            .withCreatedBefore(filter.getCreatedBefore())
                                ).orElse(null)
                        )
        );
    }

    private String fetchAWSOmicsServiceRole(AWSOmicsFileImportJob importJob) {
        final String serviceRoleArn = Optional.ofNullable(importJob.getServiceRoleArn())
                .orElse(region.getOmicsServiceRole());
        if (serviceRoleArn == null) {
            throw new DataStorageException("Omics Service role ARN is not provided. " +
                    "Please, specify it in the region settings or provide with import job request.");
        }
        return serviceRoleArn;
    }

    private boolean doesReferenceExist(final AWSOmicsReferenceDataStorage dataStorage, final String referenceId) {
        return getOmicsRefStorageFile(dataStorage, referenceId) == null;
    }

    private boolean doesReadSetExist(final AWSOmicsSequenceDataStorage dataStorage, final String readSetId) {
        return getOmicsSeqStorageFile(dataStorage, readSetId) == null;
    }


    private AmazonOmics omics() {
        final AWSCredentialsProvider credentialsProvider;
        if (StringUtils.isNotBlank(roleArn)) {
            credentialsProvider = new STSAssumeRoleSessionCredentialsProvider.Builder(roleArn, AWSUtils.ROLE_SESSION_NAME)
                    .withRoleSessionDurationSeconds(AWSUtils.MIN_SESSION_DURATION)
                    .build();
        } else if (Objects.nonNull(credentials)) {
            credentialsProvider = new AWSStaticCredentialsProvider(
                    new BasicAWSCredentials(credentials.getKeyId(), credentials.getAccessKey())
            );
        } else {
            credentialsProvider = AWSUtils.getCredentialsProvider(region.getProfile());
        }
        return AmazonOmicsClientBuilder.standard().withCredentials(credentialsProvider).build();
    }
}
