/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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
import com.epam.pipeline.entity.utils.DateUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Getter
@Setter
public class PipelineRun extends AbstractSecuredEntity {

    public static final String PARENT_ID_PARAM = "parent-id";
    public static final String KEY_VALUE_DELIMITER = "=";
    public static final String PARAM_DELIMITER = "|";
    public static final String DEFAULT_PIPELINE_NAME = "pipeline";
    public static final String GE_AUTOSCALING = "CP_CAP_AUTOSCALE";

    // Describes the user who actually launch this run, in common case will be the same as owner field,
    // but in case of runAs functionality will hold a name of the user who initially initiate a launch process
    private String originalOwner;

    private Long pipelineId;
    private Date startDate;
    private Date instanceStartDate;
    private String version;
    private Date endDate;
    private TaskStatus status;
    private CommitStatus commitStatus;
    private Date lastChangeCommitTime;
    private String params;

    private String dockerImage;
    private String actualDockerImage;
    private String platform;
    private String cmdTemplate;
    private String actualCmd;
    private Map<String, String> serviceUrl;

    private Boolean terminating = false;
    private Boolean sensitive;
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
    private Integer childRunsCount;
    private Integer activeChildRunsCount;
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
    private LocalDateTime lastNetworkConsumptionNotificationTime;
    private LocalDateTime prolongedAtTime;
    private ExecutionPreferences executionPreferences = ExecutionPreferences.getDefault();
    private String prettyUrl;
    /**
     * Pipeline run overall instance price per hour.
     */
    private BigDecimal pricePerHour;
    /**
     * Pipeline instance virtual machine price per hour.
     */
    private BigDecimal computePricePerHour;
    /**
     * Run filesystem price per hour (including size).
     */
    private BigDecimal fsPricePerHour;
    /**
     * Pipeline run instance disk gigabyte price per hour. 
     */
    private BigDecimal diskPricePerHour;
    private String stateReasonMessage;
    private List<RestartRun> restartedRuns;
    private List<RunStatus> runStatuses;

    // Is used to disable AUTO-pause functionality, see ResourceMonitoringManager.performPause()
    private boolean nonPause;

    // Is used to completely disable pause functionality, even per user request
    private boolean pauseDisabled;
    private Long projectId;

    /**
     * For CMD runs parent is TOOL, for usual runs - it is a PIPELINE
     */
    @JsonIgnore
    private AbstractSecuredEntity parent;
    private AclClass aclClass = AclClass.PIPELINE;
    private Map<String, String> tags;
    private boolean kubeServiceEnabled;
    /**
     * Cluster workers price estimation. This value shall be calculated for master runs only.
     */
    private BigDecimal workersPrice;
    private String logsStoragePath;

    public PipelineRun() {
        this.terminating = false;
        this.tags = new HashMap<>();
        this.setCreatedDate(null);
    }

    public PipelineRun(Long id, String name) {
        super(id, name);
    }

    public Boolean isTerminating() {
        return terminating;
    }

    public boolean isClusterRun() {
        return isMasterRun() || isWorkerRun();
    }

    public boolean isMasterRun() {
        //master node of autoscale cluster
        return this.hasBooleanParameter(GE_AUTOSCALING)
                // master node
                || this.getNodeCount() != null && this.getNodeCount() != 0;
    }

    public boolean isWorkerRun() {
        // worker node
        return this.getParentRunId() != null;
    }

    private boolean hasBooleanParameter(String parameterName) {
        return CollectionUtils.emptyIfNull(this.pipelineRunParameters).stream()
                .anyMatch(p -> p.getName().equals(parameterName) && p.getValue() != null
                               && p.getValue().equalsIgnoreCase("true"));
    }

    @JsonIgnore
    public Optional<String> getParameterValue(final String parameterName) {
        return ListUtils.emptyIfNull(pipelineRunParameters)
                .stream()
                .filter(param -> parameterName.equals(param.getName()) && param.getValue() != null)
                .map(PipelineRunParameter::getValue)
                .findFirst();
    }

    public void parseParameters() {
        if (CollectionUtils.isNotEmpty(pipelineRunParameters)) {
            return;
        }
        pipelineRunParameters = new ArrayList<>();
        if (StringUtils.isNotBlank(params)) {
            String[] parts = params.split("\\|");

            pipelineRunParameters = Arrays.stream(parts)
                    .map(part -> {
                        String[] chunks = part.split(KEY_VALUE_DELIMITER);
                        if (chunks.length == 2) {
                            return new PipelineRunParameter(chunks[0], chunks[1]);
                        } else if (chunks.length >= 3) {
                            //We consider everything between first `=` and last `=` - value
                            int valueStartIndex = chunks[0].length() + 1;
                            int valueEndIndex = StringUtils.lastIndexOf(part, KEY_VALUE_DELIMITER);
                            String value = part.substring(valueStartIndex, valueEndIndex);
                            String type = part.substring(
                                    valueEndIndex + 1);
                            return new PipelineRunParameter(chunks[0], value, type);
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
     *
     * @param key   key to be inserted
     * @param value value to be checked
     * @return true if put operation was successful or false otherwise
     */
    public boolean addTag(final String key, final String value) {
        return tags.putIfAbsent(key, value) == null;
    }

    /**
     * Remove tag from the given run
     * @param key key to be removed
     */
    public void removeTag(final String key) {
        tags.remove(key);
    }

    @JsonIgnore
    public LocalDateTime getInstanceStartDateTime() {
        return Optional.ofNullable(instanceStartDate).map(DateUtils::convertDateToLocalDateTime).orElse(null);
    }

    @JsonIgnore
    public void setInstanceStartDateTime(final LocalDateTime date) {
        instanceStartDate = Optional.ofNullable(date).map(DateUtils::convertLocalDateTimeToDate).orElse(null);
    }
}
