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

package com.epam.pipeline.entity.pipeline;

import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.configuration.PipeConfValueVO;
import com.epam.pipeline.entity.pipeline.run.ExecutionPreferences;
import com.epam.pipeline.entity.pipeline.run.RestartRun;
import com.epam.pipeline.entity.pipeline.run.RunStatus;
import com.epam.pipeline.entity.pipeline.run.parameter.PipelineRunParameter;
import com.epam.pipeline.entity.pipeline.run.parameter.RunSid;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.collections4.ListUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Getter
@Setter
@AllArgsConstructor
public class PipelineRun extends AbstractSecuredEntity {

    public static final String PARENT_ID_PARAM = "parent-id";
    public static final String KEY_VALUE_DELIMITER = "=";
    public static final String PARAM_DELIMITER = "|";
    public static final String DEFAULT_PIPELINE_NAME = "pipeline";
    private static final Pattern PARAMS_REGEXP = Pattern.compile("([a-zA-Z0-9_]*=[a-zA-Z0-9_]*)");

    private Long pipelineId;
    private Date startDate;
    private String version;
    private Date endDate;
    private TaskStatus status;
    private CommitStatus commitStatus;
    private Date lastChangeCommitTime;
    private String params;

    private String dockerImage;
    private String cmdTemplate;
    private String actualCmd;
    private String serviceUrl;

    private Boolean terminating = false;
    private String podId;
    private String pipelineName;
    private List<PipelineRunParameter> pipelineRunParameters;
    private RunInstance instance;
    private Long timeout;
    private String repository;
    private String revisionName;
    private String podIP;
    private String sshPassword;
    private String configName;
    private Integer nodeCount;
    private Long parentRunId;
    private List<PipelineRun> childRuns;
    private Boolean initialized;
    private Boolean queued;
    private List<Long> entitiesIds;
    private Long configurationId;
    private String podStatus;
    private List<RunSid> runSids;
    private Map<String, String> envVars;
    /**
     * Last time the notification on long-running pipeline was issued
     */
    private Date lastNotificationTime;
    /**
     * Last time the notification on idle pipeline was issued
     */
    private LocalDateTime lastIdleNotificationTime;
    private LocalDateTime prolongedAtTime;
    private ExecutionPreferences executionPreferences = ExecutionPreferences.getDefault();
    private String prettyUrl;
    private BigDecimal pricePerHour;
    private String stateReasonMessage;
    private List<RestartRun> restartedRuns;
    private List<RunStatus> runStatuses;
    private boolean nonPause;

    /**
     * For CMD runs parent is TOOL, for usual runs - it is a PIPELINE
     */
    @JsonIgnore
    private AbstractSecuredEntity parent;
    private AclClass aclClass = AclClass.PIPELINE;
    private Map<String, String> tags;


    public PipelineRun() {
        this.terminating = false;
        this.tags = new HashMap<>();
    }

    public PipelineRun(Long id, String name) {
        super(id, name);
    }

    public Boolean isTerminating() {
        return terminating;
    }

    public void convertParamsToString(Map<String, PipeConfValueVO> parameters) {
        params = parameters
                .entrySet().stream()
                .map(entry -> {
                    String param = entry.getKey() + KEY_VALUE_DELIMITER +
                            entry.getValue().getValue();
                    if (StringUtils.hasText(entry.getValue().getType())) {
                        param += KEY_VALUE_DELIMITER + (entry.getValue().getType());
                    }
                    return param;
                })
                .collect(Collectors.joining(PARAM_DELIMITER));
    }

    public void parseParameters() {
        pipelineRunParameters = new ArrayList<>();
        if (StringUtils.hasText(params)) {
            String[] parts = params.split("\\|");

            pipelineRunParameters = Arrays.stream(parts)
                    .map(part -> {
                        String[] chunks = part.split(KEY_VALUE_DELIMITER);
                        if (chunks.length == 2) {
                            return new PipelineRunParameter(chunks[0], chunks[1]);
                        } else if (chunks.length == 3) {
                            return new PipelineRunParameter(chunks[0], chunks[1], chunks[2]);
                        }
                        return new PipelineRunParameter(part);
                    })
                    .collect(Collectors.toList());
        }

        if (parentRunId != null &&
                pipelineRunParameters.stream().noneMatch(p -> p.getName().equals(PARENT_ID_PARAM))) {
            pipelineRunParameters.add(new PipelineRunParameter(PARENT_ID_PARAM, parentRunId.toString()));
        }
    }

    public Map<String, PipeConfValueVO> convertParamsToMap() {
        return ListUtils.emptyIfNull(pipelineRunParameters)
                .stream()
                .collect(Collectors.toMap(PipelineRunParameter::getName,
                    p -> new PipeConfValueVO(p.getValue(), p.getType()), (p1, p2) -> p1));
    }

    public String getTaskName() {
        return StringUtils.isEmpty(pipelineName) ? podId : pipelineName;
    }

    /**
     * Check if given key represented in tag map
     * @param key key to be checked
     * @return true - if tag map contains the given key, false - otherwise
     */
    public boolean hasTag(final String key) {
        return tags.containsKey(key);
    }

    /**
     * Add tag to the given run
     * @param key key to be inserted
     * @param value value to be checked
     */
    public void addTag(final String key, final String value) {
        tags.putIfAbsent(key, value);
    }

    /**
     * Remove tag from the given run
     * @param key key to be removed
     */
    public void removeTag(final String key) {
        tags.remove(key);
    }
}
