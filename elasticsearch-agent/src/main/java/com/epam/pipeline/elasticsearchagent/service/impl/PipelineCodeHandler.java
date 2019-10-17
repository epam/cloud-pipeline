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
package com.epam.pipeline.elasticsearchagent.service.impl;

import com.epam.pipeline.elasticsearchagent.exception.EntityNotFoundException;
import com.epam.pipeline.elasticsearchagent.model.EventType;
import com.epam.pipeline.elasticsearchagent.model.PermissionsContainer;
import com.epam.pipeline.elasticsearchagent.model.PipelineEvent;
import com.epam.pipeline.elasticsearchagent.model.git.GitEventData;
import com.epam.pipeline.elasticsearchagent.model.git.GitEventDescription;
import com.epam.pipeline.elasticsearchagent.model.git.GitEventType;
import com.epam.pipeline.elasticsearchagent.model.git.GitFileEvent;
import com.epam.pipeline.elasticsearchagent.service.impl.converter.pipeline.PipelineCodeMapper;
import com.epam.pipeline.elasticsearchagent.service.impl.converter.pipeline.PipelineLoader;
import com.epam.pipeline.elasticsearchagent.utils.EventProcessorUtils;
import com.epam.pipeline.elasticsearchagent.utils.Utils;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.elasticsearch.action.DocWriteRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Slf4j
public class PipelineCodeHandler {

    private static final String DOC_MAPPING_TYPE = "_doc";
    private static final String FOLDER_INDICATOR = "/";
    private static final String GIT_ENTRY_TYPE = "blob";
    private static final String DRAFT_NAME = "draft-";

    private final String indexPrefix;
    private final String pipelineCodeIndexName;
    private final CloudPipelineAPIClient cloudPipelineAPIClient;
    private final ElasticIndexService indexService;
    private final List<String> pipelineFileIndexPaths;
    private final ObjectMapper objectMapper;
    private final PipelineCodeMapper codeMapper;
    private final PipelineLoader pipelineLoader;
    private final String defaultBranchName;
    private final int codeLimitBytes;

    public PipelineCodeHandler(final @Value("${sync.index.common.prefix}") String indexPrefix,
                               final @Value("${sync.pipeline-code.index.name}") String pipelineCodeIndexName,
                               final CloudPipelineAPIClient cloudPipelineAPIClient,
                               final ElasticIndexService indexService,
                               final @Value("${sync.pipeline-code.index.paths}") String pipelineFileIndexPaths,
                               final ObjectMapper objectMapper,
                               final PipelineLoader pipelineLoader,
                               final PipelineCodeMapper codeMapper,
                               final @Value("${sync.pipeline-code.default-branch}") String defaultBranchName,
                               final @Value("${sync.pipeline-code.max.bytes:10240}") Integer codeLimitBytes) {
        this.indexPrefix = indexPrefix;
        this.pipelineCodeIndexName = pipelineCodeIndexName;
        this.cloudPipelineAPIClient = cloudPipelineAPIClient;
        this.indexService = indexService;
        this.pipelineFileIndexPaths = EventProcessorUtils.splitOnPaths(pipelineFileIndexPaths);
        this.objectMapper = objectMapper;
        this.codeMapper = codeMapper;
        this.pipelineLoader = pipelineLoader;
        this.defaultBranchName = defaultBranchName;
        this.codeLimitBytes = codeLimitBytes;
    }

    public List<DocWriteRequest> processGitEvents(final Long id,
                                                  final List<PipelineEvent> events) throws EntityNotFoundException {
        return pipelineLoader.loadEntity(id).map(entry -> {
            final Pipeline pipeline = entry.getEntity().getPipeline();
            String indexNameForPipelineCode = String.format(
                    "%s%s-%d", indexPrefix, pipelineCodeIndexName, pipeline.getId());
            PermissionsContainer permissions = new PermissionsContainer(
                    cloudPipelineAPIClient.loadPermissionsForEntity(id, AclClass.PIPELINE));

            Map<String, List<GitEventDescription>> versionEvents = events
                    .stream()
                    .map(this::mapPipelineEventToGitEvent)
                    .collect(Collectors.groupingBy(event -> event.getEventData().getVersion()));

            return versionEvents.values().stream()
                    .map(e -> createRequestsForVersionEvents(e, indexNameForPipelineCode, pipeline, permissions))
                    .flatMap(Collection::stream)
                    .collect(Collectors.toList());
        }).orElse(Collections.emptyList());
    }

    private List<DocWriteRequest> createRequestsForVersionEvents(final List<GitEventDescription> versionEvents,
                                                                 final String indexName,
                                                                 final Pipeline pipeline,
                                                                 final PermissionsContainer permissions) {
        return versionEvents.stream()
                .collect(Collectors.groupingBy(event -> event.getEventData().getGitEventType()))
                .entrySet()
                .stream()
                .map(entry ->
                        getDocRequestsByType(indexName, pipeline, permissions, entry.getKey(), entry.getValue()))
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    private List<DocWriteRequest> getDocRequestsByType(final String indexName,
                                                       final Pipeline pipeline,
                                                       final PermissionsContainer permissions,
                                                       final GitEventType gitEventType,
                                                       final List<GitEventDescription> events) {
        events.sort(Comparator.comparing(data -> data.getPipelineEvent().getCreatedDate()));
        switch (gitEventType) {
            case tag_push:
                return processTagEvent(indexName, pipeline, permissions, events);
            case push:
                return processPushEvent(indexName, pipeline, events, permissions);
            default:
                log.error("Unsupported event type {}", gitEventType);
                return Collections.emptyList();

        }
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    public List<DocWriteRequest> createPipelineCodeDocuments(final Pipeline pipeline,
                                                             final PermissionsContainer permissions,
                                                             final String revisionName,
                                                             final String indexName,
                                                             final List<String> pipelineFileIndexPaths) {
        return pipelineFileIndexPaths.stream()
                .map(path -> {
                    log.debug("Fetching path {} for revisionName {}", path, revisionName);
                    if (path.endsWith(FOLDER_INDICATOR)) {
                        return ListUtils.emptyIfNull(
                                cloudPipelineAPIClient.loadRepositoryContents(
                                        pipeline.getId(),
                                        revisionName,
                                        path.replace(FOLDER_INDICATOR, StringUtils.EMPTY)))
                                .stream()
                                .filter(content -> (content.getType().equals(GIT_ENTRY_TYPE)))
                                .map(content ->
                                        createIndexRequest(
                                                pipeline, revisionName, indexName, content.getPath(), permissions))
                                .collect(Collectors.toList());
                    }
                    return Collections.singletonList(createIndexRequest(
                            pipeline, revisionName, indexName, path, permissions));
                })
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }


    private GitEventDescription mapPipelineEventToGitEvent(final PipelineEvent event) {
        try {
            GitEventData gitEventData = objectMapper.readValue(event.getData(), GitEventData.class);
            return new GitEventDescription(event, gitEventData);
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not parse json data " + event.getData(), e);
        }
    }

    private List<DocWriteRequest> processPushEvent(final String indexName,
                                                   final Pipeline pipeline,
                                                   final List<GitEventDescription> gitEventData,
                                                   final PermissionsContainer permissions) {
        final Map<String, List<GitEventDescription>> pathWithEvents = gitEventData
                .stream()
                .map(event -> event.getEventData().getPaths().stream()
                        .map(path -> new ImmutablePair<>(event, path))
                        .collect(Collectors.toList()))
                .flatMap(Collection::stream)
                .collect(Collectors.groupingBy(ImmutablePair::getRight,
                        Collectors.mapping(ImmutablePair::getLeft, Collectors.toList())));

        return pathWithEvents.entrySet().stream()
                .peek(entry -> entry.getValue()
                        .sort(Comparator.comparing(gitEvent -> gitEvent.getPipelineEvent().getCreatedDate())))
                .map(entry -> getGitFileEvent(entry.getKey(), Utils.last(entry.getValue())))
                .map(event -> convertFilePushToDocRequest(event, indexName, pipeline, permissions))
                .collect(Collectors.toList());

    }

    private DocWriteRequest convertFilePushToDocRequest(final GitFileEvent filePushEvent,
                                                        final String indexName,
                                                        final Pipeline pipeline,
                                                        final PermissionsContainer permissions) {
        if (filePushEvent.getEventType() == EventType.DELETE) {
            return new DeleteRequest(indexName, DOC_MAPPING_TYPE,
                    buildFileId(filePushEvent.getPath(), getVersionNameForIndex(filePushEvent.getVersion())));
        } else {
            return createIndexRequest(pipeline, filePushEvent.getVersion(), indexName,
                    filePushEvent.getPath(), permissions);
        }
    }

    private GitFileEvent getGitFileEvent(final String path, final GitEventDescription event) {
        return GitFileEvent.builder()
                .path(path)
                .version(event.getEventData().getVersion())
                .timestamp(event.getPipelineEvent().getCreatedDate())
                .eventType(event.getPipelineEvent().getEventType())
                .build();
    }

    private List<DocWriteRequest> processTagEvent(final String indexName,
                                                  final Pipeline pipeline,
                                                  final PermissionsContainer permissions,
                                                  final List<GitEventDescription> gitEventData) {
        final GitEventDescription lastEvent = Utils.last(gitEventData);
        final GitEventData eventData = lastEvent.getEventData();
        final String versionName = eventData.getVersion();
        if (lastEvent.getPipelineEvent().getEventType() == EventType.DELETE) {
            return indexService.getDeleteRequestsByTerm("pipelineVersion", versionName, indexName);
        }
        return createPipelineCodeDocuments(pipeline, permissions, versionName, indexName, pipelineFileIndexPaths);
    }

    private IndexRequest createIndexRequest(final Pipeline pipeline,
                                            final String revisionName,
                                            final String indexName,
                                            final String repoEntryPath,
                                            final PermissionsContainer permissionsContainer) {
        log.debug("Indexing entry {}", repoEntryPath);
        final String fileContent =
                cloudPipelineAPIClient.getTruncatedPipelineFile(pipeline.getId(), revisionName, repoEntryPath,
                                                                codeLimitBytes);
        if (StringUtils.isBlank(fileContent)) {
            log.debug("Missing file content for path {} revision {}", repoEntryPath, revisionName);
            return null;
        }
        final String versionName = getVersionNameForIndex(revisionName);
        return new IndexRequest(indexName, DOC_MAPPING_TYPE, buildFileId(repoEntryPath, versionName))
                .source(codeMapper
                        .pipelineCodeToDocument(
                                pipeline, versionName, repoEntryPath, fileContent, permissionsContainer));
    }

    private String getVersionNameForIndex(final String revisionName) {
        if (revisionName.startsWith(DRAFT_NAME)) {
            return defaultBranchName;
        }
        return revisionName;
    }

    private String buildFileId(final String repoEntryPath, final String versionName) {
        return String.format("%s-%s", versionName, repoEntryPath);
    }
}
