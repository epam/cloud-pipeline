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
package com.epam.pipeline.elasticsearchagent.service.impl.converter;

import com.epam.pipeline.elasticsearchagent.model.EventType;
import com.epam.pipeline.elasticsearchagent.model.PipelineEvent;
import com.epam.pipeline.elasticsearchagent.model.PipelineRunWithLog;
import com.epam.pipeline.elasticsearchagent.service.EventToRequestConverter;
import com.epam.pipeline.elasticsearchagent.service.impl.CloudPipelineAPIClient;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.pipeline.RunLog;
import com.epam.pipeline.entity.pipeline.run.RunStatus;
import com.epam.pipeline.entity.pipeline.run.parameter.PipelineRunParameter;
import com.epam.pipeline.entity.search.SearchDocumentType;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.exception.PipelineResponseException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.DocWriteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.epam.pipeline.elasticsearchagent.service.ElasticsearchSynchronizer.DOC_TYPE_FIELD;

//@Component
@Getter
@Slf4j
public class PipelineRunMapperOld implements EventToRequestConverter {

    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(DATE_PATTERN);

    private final String indexPrefix;
    private final String indexName;
    private final CloudPipelineAPIClient cloudPipelineAPIClient;

    public PipelineRunMapperOld(final @Value("${sync.index.common.prefix}") String indexPrefix,
                                final @Value("${sync.run.index.name}") String indexName,
                                final CloudPipelineAPIClient cloudPipelineAPIClient) {
        this.indexPrefix = indexPrefix;
        this.indexName = indexName;
        this.cloudPipelineAPIClient = cloudPipelineAPIClient;
    }

    @Override
    public String buildIndexName() {
        return indexPrefix + indexName;
    }

    @Override
    public List<DocWriteRequest> convertEventsToRequest(final List<PipelineEvent> events,
                                                        final String indexName) {
        final Map<String, PipelineUser> users = cloudPipelineAPIClient.loadAllUsers();
        return events.stream()
                .map(event -> {
                    if (event.getEventType() == EventType.DELETE) {
                        return Optional.of(createDeleteRequest(event, indexName));
                    }
                    return getRequestForRun(indexName, event, users);
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    private Optional<DocWriteRequest> getRequestForRun(final String indexName,
                                                       final PipelineEvent event,
                                                       final Map<String, PipelineUser> users) {
        try {
            PipelineRunWithLog run = cloudPipelineAPIClient.loadPipelineRunWithLogs(event.getObjectId());
            PipelineUser pipelineUser = users.get(run.getPipelineRun().getOwner());
            run.setRunOwner(pipelineUser);
            return Optional.of(new IndexRequest(indexName, INDEX_TYPE, String.valueOf(run.getPipelineRun().getId()))
                    .source(pipelineRunToDocument(run)));
        } catch (PipelineResponseException e) {
            log.error(e.getMessage(), e);
            if (isRunMissingError(event, e)) {
                return Optional.of(createDeleteRequest(event, indexName));
            }
            return Optional.empty();
        }
    }

    private boolean isRunMissingError(final PipelineEvent event, final PipelineResponseException e) {
        return e.getMessage().contains(String.format("%d was not found", event.getObjectId()));
    }

    private XContentBuilder pipelineRunToDocument(final PipelineRunWithLog run) {
        try (XContentBuilder jsonBuilder = XContentFactory.jsonBuilder()) {
            jsonBuilder
                    .startObject()
                    .field("id", run.getPipelineRun().getId())
                    .field(DOC_TYPE_FIELD, SearchDocumentType.PIPELINE_RUN.name())
                    .field("description", getRunDescription(run.getPipelineRun()))
                    .field("createdDate", parseDataToString(run.getPipelineRun().getCreatedDate()))
                    .field("startDate", parseDataToString(run.getPipelineRun().getStartDate()))
                    .field("endDate", parseDataToString(run.getPipelineRun().getEndDate()))
                    .field("pipelineName", run.getPipelineRun().getPipelineName())
                    .field("pipelineVersion", run.getPipelineRun().getVersion())
                    .field("status", run.getPipelineRun().getStatus())
                    .field("dockerImage", run.getPipelineRun().getDockerImage())
                    .field("actualCmd", run.getPipelineRun().getActualCmd())
                    .field("configurationName", run.getPipelineRun().getConfigName())
                    .field("configurationId", run.getPipelineRun().getConfigurationId())
                    .field("environment", run.getPipelineRun().getExecutionPreferences().getEnvironment().name())
                    .field("pricePerHour", run.getPipelineRun().getPricePerHour().doubleValue())
                    .field("parentRunId", run.getPipelineRun().getParentRunId())
                    .field("nodeCount", run.getPipelineRun().getNodeCount())
                    .field("podId", run.getPipelineRun().getPodId());

            buildUserContent(run.getRunOwner(), jsonBuilder);

            if (!StringUtils.isEmpty(run.getPipelineRun().getInstance())) {
                buildRunInstance(run.getPipelineRun().getInstance(), jsonBuilder);
            }

            if (!StringUtils.isEmpty(run.getPipelineRun().getPipelineRunParameters())) {
                buildRunParam(run.getPipelineRun().getPipelineRunParameters(), jsonBuilder);
            }

            if (!StringUtils.isEmpty(run.getPipelineRun().getRunStatuses())) {
                buildRunStatus(run.getPipelineRun().getRunStatuses(), jsonBuilder);
            }

            if (!StringUtils.isEmpty(run.getRunLogs())) {
                buildRunLog(run.getRunLogs(), jsonBuilder);
            }

            jsonBuilder.endObject();
            return jsonBuilder;
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to create elasticsearch document for pipeline run: ", e);
        }
    }

    private String getRunDescription(final PipelineRun pipelineRun) {
        if (StringUtils.hasText(pipelineRun.getPipelineName()) &&
                StringUtils.hasText(pipelineRun.getVersion())) {
            return pipelineRun.getPipelineName() + " " + pipelineRun.getVersion();
        }
        return pipelineRun.getDockerImage();
    }

    private void buildRunInstance(RunInstance instance, XContentBuilder jsonBuilder) throws IOException {
        jsonBuilder
                .field("instance")
                .startObject()
                .field("nodeType", instance.getNodeType())
                .field("nodeDisk", instance.getNodeDisk())
                .field("nodeIP", instance.getNodeIP())
                .field("nodeId", instance.getNodeId())
                .field("nodeImage", instance.getNodeImage())
                .field("nodeName", instance.getNodeName())
                .field("priceType", instance.getSpot())
                .field("awsRegion", instance.getAwsRegionId())
                .endObject();
    }

    private void buildRunParam(List<PipelineRunParameter> runParams, XContentBuilder jsonBuilder) throws IOException {
        jsonBuilder.startArray("parameters");
        for (PipelineRunParameter runParameter : runParams) {
            jsonBuilder
                    .startObject()
                    .field("parameter", runParameter.getName())
                    .field("value", runParameter.getValue())
                    .endObject();
        }
        jsonBuilder.endArray();
    }

    private void buildRunStatus(List<RunStatus> runStatuses, XContentBuilder jsonBuilder) throws IOException {
        jsonBuilder.startArray("statuses");
        for (RunStatus runStatus : runStatuses) {
            jsonBuilder
                    .startObject()
                    .field("status", runStatus.getStatus())
                    .field("timestamp", parseLocalDataToString(runStatus.getTimestamp()))
                    .endObject();
        }
        jsonBuilder.endArray();
    }

    private void buildRunLog(List<RunLog> runLogs, XContentBuilder jsonBuilder) throws IOException {
        jsonBuilder.startArray("logs");
        for (RunLog runLog : runLogs) {
            jsonBuilder
                    .startObject()
                    .field("timestamp", parseDataToString(runLog.getDate()))
                    .field("taskName", runLog.getTaskName())
                    .field("logText", runLog.getLogText())
                    .field("status", runLog.getStatus())
                    .endObject();
        }
        jsonBuilder.endArray();
    }

    private String parseLocalDataToString(LocalDateTime date) {
        if (date == null) {
            return null;
        }
        return dateTimeFormatter.format(date);
    }
}
