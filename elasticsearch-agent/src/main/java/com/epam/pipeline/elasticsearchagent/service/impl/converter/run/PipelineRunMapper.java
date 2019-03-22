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
package com.epam.pipeline.elasticsearchagent.service.impl.converter.run;

import com.epam.pipeline.elasticsearchagent.model.EntityContainer;
import com.epam.pipeline.elasticsearchagent.model.PipelineRunWithLog;
import com.epam.pipeline.elasticsearchagent.service.EntityMapper;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.PipelineTask;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.pipeline.RunLog;
import com.epam.pipeline.entity.pipeline.run.RunStatus;
import com.epam.pipeline.entity.pipeline.run.parameter.PipelineRunParameter;
import com.epam.pipeline.entity.search.SearchDocumentType;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import static com.epam.pipeline.elasticsearchagent.service.ElasticsearchSynchronizer.DOC_TYPE_FIELD;

@Component
public class PipelineRunMapper implements EntityMapper<PipelineRunWithLog> {

    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(DATE_PATTERN);

    @Override
    public XContentBuilder map(final EntityContainer<PipelineRunWithLog> container) {
        PipelineRunWithLog run = container.getEntity();
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
                    .field("environment", Optional.ofNullable(run.getPipelineRun().getExecutionPreferences())
                            .map(preferences -> preferences.getEnvironment().name())
                            .orElse(null))
                    .field("pricePerHour", run.getPipelineRun().getPricePerHour().doubleValue())
                    .field("parentRunId", run.getPipelineRun().getParentRunId())
                    .field("nodeCount", run.getPipelineRun().getNodeCount())
                    .field("podId", run.getPipelineRun().getPodId());

            buildRunInstance(run.getPipelineRun().getInstance(), jsonBuilder);
            buildRunStatus(run.getPipelineRun().getRunStatuses(), jsonBuilder);
            buildRunParam(run.getPipelineRun().getPipelineRunParameters(), jsonBuilder);
            buildRunLog(run.getRunLogs(), jsonBuilder);
            buildUserContent(container.getOwner(), jsonBuilder);
            buildPermissions(container.getPermissions(), jsonBuilder);

            jsonBuilder.endObject();
            return jsonBuilder;
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to create elasticsearch document for pipeline run: ", e);
        }
    }

    private String getRunDescription(final PipelineRun pipelineRun) {
        if (StringUtils.isNotBlank(pipelineRun.getPipelineName()) &&
                StringUtils.isNotBlank(pipelineRun.getVersion())) {
            return pipelineRun.getPipelineName() + " " + pipelineRun.getVersion();
        }
        return pipelineRun.getDockerImage();
    }

    private void buildRunInstance(RunInstance instance, XContentBuilder jsonBuilder) throws IOException {
        if (instance == null) {
            return;
        }
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
        if (CollectionUtils.isEmpty(runParams)) {
            return;
        }
        jsonBuilder.array("parameters", runParams.stream()
                .map(param -> {
                    final String paramName = param.getName();
                    final String paramValue = StringUtils.defaultIfBlank(
                            StringUtils.defaultIfBlank(param.getResolvedValue(), param.getValue()), StringUtils.EMPTY);
                    if (StringUtils.isBlank(paramValue)) {
                        return paramName;
                    }
                    return paramName + " " + paramValue;
                })
                .toArray(String[]::new));
    }

    private void buildRunStatus(List<RunStatus> runStatuses, XContentBuilder jsonBuilder) throws IOException {
        if (CollectionUtils.isEmpty(runStatuses)) {
            return;
        }
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
        if (CollectionUtils.isEmpty(runLogs)) {
            return;
        }
        jsonBuilder.array("logs", runLogs.stream()
                .map(log -> {
                    final String logText = log.getLogText();
                    final String taskName = Optional.ofNullable(log.getTask())
                            .map(PipelineTask::getName)
                            .orElse(StringUtils.EMPTY);
                    if (StringUtils.isBlank(taskName)) {
                        return taskName;
                    }
                    return taskName + " " + logText;
                })
                .toArray(String[]::new));
    }

    private String parseLocalDataToString(LocalDateTime date) {
        if (date == null) {
            return null;
        }
        return dateTimeFormatter.format(date);
    }
}
