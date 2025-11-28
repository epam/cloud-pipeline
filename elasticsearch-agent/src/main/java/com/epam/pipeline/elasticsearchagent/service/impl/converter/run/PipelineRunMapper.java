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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.epam.pipeline.elasticsearchagent.service.ElasticsearchSynchronizer.DOC_TYPE_FIELD;

@Component
public class PipelineRunMapper implements EntityMapper<PipelineRunWithLog> {

    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(DATE_PATTERN);

    private final int maxLogLines;

    public PipelineRunMapper(@Value("${sync.run.log.lines.size:1000}") final int maxLogLines) {
        this.maxLogLines = maxLogLines;
    }

    @Override
    public Map<String, ?> map(final EntityContainer<PipelineRunWithLog> container) {
        final PipelineRunWithLog run = container.getEntity();
        final Map<String, Object> jsonMap = new HashMap<>();

        jsonMap.put("id", run.getPipelineRun().getId());
        jsonMap.put(DOC_TYPE_FIELD, SearchDocumentType.PIPELINE_RUN.name());
        jsonMap.put("description", getRunDescription(run.getPipelineRun()));
        jsonMap.put("createdDate", parseDataToString(run.getPipelineRun().getCreatedDate()));
        jsonMap.put("startDate", parseDataToString(run.getPipelineRun().getStartDate()));
        jsonMap.put("endDate", parseDataToString(run.getPipelineRun().getEndDate()));
        jsonMap.put("pipelineName", run.getPipelineRun().getPipelineName());
        jsonMap.put("pipelineVersion", run.getPipelineRun().getVersion());
        jsonMap.put("status", run.getPipelineRun().getStatus());
        jsonMap.put("dockerImage", run.getPipelineRun().getDockerImage());
        jsonMap.put("actualCmd", run.getPipelineRun().getActualCmd());
        jsonMap.put("configurationName", run.getPipelineRun().getConfigName());
        jsonMap.put("configurationId", run.getPipelineRun().getConfigurationId());
        jsonMap.put("environment", Optional.ofNullable(run.getPipelineRun().getExecutionPreferences())
                .map(preferences -> preferences.getEnvironment().name())
                .orElse(null));
        jsonMap.put("pricePerHour", run.getPipelineRun().getPricePerHour().doubleValue());
        jsonMap.put("parentRunId", run.getPipelineRun().getParentRunId());
        jsonMap.put("nodeCount", run.getPipelineRun().getNodeCount());
        jsonMap.put("name", run.getPipelineRun().getPodId());
        jsonMap.put("podId", run.getPipelineRun().getPodId());

        buildRunInstance(run.getPipelineRun().getInstance(), jsonMap);
        buildRunStatus(run.getPipelineRun().getRunStatuses(), jsonMap);
        buildRunParam(run.getPipelineRun().getPipelineRunParameters(), jsonMap);
        buildRunLog(run.getRunLogs(), jsonMap);
        buildUserContent(container.getOwner(), jsonMap);
        buildPermissions(container.getPermissions(), jsonMap);

        return jsonMap;
    }

    private String getRunDescription(final PipelineRun pipelineRun) {
        if (StringUtils.isNotBlank(pipelineRun.getPipelineName()) &&
                StringUtils.isNotBlank(pipelineRun.getVersion())) {
            return pipelineRun.getPipelineName() + " " + pipelineRun.getVersion();
        }
        return pipelineRun.getDockerImage();
    }

    private void buildRunInstance(final RunInstance instance, final Map<String, Object> jsonMap) {
        if (instance == null) {
            return;
        }
        final Map<String, Object> instanceMap = new HashMap<>();
        instanceMap.put("nodeType", instance.getNodeType());
        instanceMap.put("nodeDisk", instance.getNodeDisk());
        instanceMap.put("nodeIP", instance.getNodeIP());
        instanceMap.put("nodeId", instance.getNodeId());
        instanceMap.put("nodeImage", instance.getNodeImage());
        instanceMap.put("nodeName", instance.getNodeName());
        instanceMap.put("priceType", instance.getSpot());
        instanceMap.put("cloudRegionId", instance.getCloudRegionId());

        jsonMap.put("instance", instanceMap);
    }

    private void buildRunParam(final List<PipelineRunParameter> runParams, final Map<String, Object> jsonMap) {
        if (CollectionUtils.isEmpty(runParams)) {
            return;
        }
        jsonMap.put("parameters", runParams.stream()
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

    private void buildRunStatus(final List<RunStatus> runStatuses, final Map<String, Object> jsonMap) {
        if (CollectionUtils.isEmpty(runStatuses)) {
            return;
        }
        final List<Map<String, Object>> statuses = new ArrayList<>();
        for (RunStatus runStatus : runStatuses) {
            final Map<String, Object> runStatusMap = new HashMap<>();
            runStatusMap.put("status", runStatus.getStatus());
            runStatusMap.put("timestamp", parseLocalDataToString(runStatus.getTimestamp()));
            statuses.add(runStatusMap);
        }
        jsonMap.put("statuses", statuses);
    }

    private void buildRunLog(final List<RunLog> runLogs, final Map<String, Object> jsonMap) {
        if (CollectionUtils.isEmpty(runLogs)) {
            return;
        }
        jsonMap.put("logs", runLogs.stream()
                .sorted(Comparator.comparing(RunLog::getDate).reversed())
                .limit(maxLogLines)
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
