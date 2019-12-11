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

package com.epam.pipeline.billingreportagent.dao;

import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.entity.pipeline.CommitStatus;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.region.CloudProvider;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcDaoSupport;

import java.sql.Array;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class PipelineRunDao extends NamedParameterJdbcDaoSupport {

    private String loadAllPipelineRunsQuery;

    public List<PipelineRun> loadAllPipelineRuns() {
        return getJdbcTemplate().query(loadAllPipelineRunsQuery,
                                       PipelineRunParameters.getRowMapper());
    }

    public void setLoadAllPipelineRunsQuery(String loadAllPipelineRunsQuery) {
        this.loadAllPipelineRunsQuery = loadAllPipelineRunsQuery;
    }

    public enum PipelineRunParameters {
        RUN_ID,
        PIPELINE_ID,
        VERSION,
        START_DATE,
        END_DATE,
        PARAMETERS,
        PARENT_ID,
        STATUS,
        COMMIT_STATUS,
        LAST_CHANGE_COMMIT_TIME,
        TERMINATING,
        POD_ID,
        PIPELINE_NAME,
        NODE_TYPE,
        NODE_IP,
        NODE_ID,
        NODE_DISK,
        NODE_IMAGE,
        NODE_NAME,
        NODE_CLOUD_REGION,
        NODE_CLOUD_PROVIDER,
        DOCKER_IMAGE,
        CMD_TEMPLATE,
        ACTUAL_CMD,
        TIMEOUT,
        OWNER,
        SERVICE_URL,
        POD_IP,
        SSH_PASSWORD,
        CONFIG_NAME,
        NODE_COUNT,
        INITIALIZATION_FINISHED,
        ENTITIES_IDS,
        IS_SPOT,
        CONFIGURATION_ID,
        POD_STATUS,
        ENV_VARS,
        LAST_NOTIFICATION_TIME,
        PROLONGED_AT_TIME,
        LAST_IDLE_NOTIFICATION_TIME,
        EXEC_PREFERENCES,
        PRETTY_URL,
        PRICE_PER_HOUR,
        STATE_REASON,
        NON_PAUSE,
        NODE_REAL_DISK;


        static MapSqlParameterSource getParameters(PipelineRun run, Connection connection) {
            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue(RUN_ID.name(), run.getId());
            params.addValue(PIPELINE_NAME.name(), run.getPipelineName());
            params.addValue(PIPELINE_ID.name(), run.getPipelineId());
            params.addValue(VERSION.name(), run.getVersion());
            params.addValue(START_DATE.name(), run.getStartDate());
            params.addValue(END_DATE.name(), run.getEndDate());
            params.addValue(PARAMETERS.name(), run.getParams());
            params.addValue(STATUS.name(), run.getStatus().getId());
            params.addValue(COMMIT_STATUS.name(), run.getCommitStatus().getId());
            params.addValue(LAST_CHANGE_COMMIT_TIME.name(), run.getLastChangeCommitTime());
            params.addValue(TERMINATING.name(), run.isTerminating());
            params.addValue(POD_ID.name(), run.getPodId());
            params.addValue(TIMEOUT.name(), run.getTimeout());
            params.addValue(DOCKER_IMAGE.name(), run.getDockerImage());
            params.addValue(CMD_TEMPLATE.name(), run.getCmdTemplate());
            params.addValue(ACTUAL_CMD.name(), run.getActualCmd());
            params.addValue(OWNER.name(), run.getOwner());
            params.addValue(SERVICE_URL.name(), run.getServiceUrl());
            params.addValue(POD_IP.name(), run.getPodIP());
            params.addValue(SSH_PASSWORD.name(), run.getSshPassword());
            params.addValue(CONFIG_NAME.name(), run.getConfigName());
            params.addValue(NODE_COUNT.name(), run.getNodeCount());
            params.addValue(PARENT_ID.name(), run.getParentRunId());
            params.addValue(CONFIGURATION_ID.name(), run.getConfigurationId());
            params.addValue(POD_STATUS.name(), run.getPodStatus());
            params.addValue(ENV_VARS.name(), JsonMapper.convertDataToJsonStringForQuery(run.getEnvVars()));
            params.addValue(PROLONGED_AT_TIME.name(), run.getProlongedAtTime());
            params.addValue(LAST_NOTIFICATION_TIME.name(), run.getLastNotificationTime());
            params.addValue(LAST_IDLE_NOTIFICATION_TIME.name(), run.getLastIdleNotificationTime());
            params.addValue(EXEC_PREFERENCES.name(),
                            JsonMapper.convertDataToJsonStringForQuery(run.getExecutionPreferences()));
            params.addValue(PRETTY_URL.name(), run.getPrettyUrl());
            params.addValue(PRICE_PER_HOUR.name(), run.getPricePerHour());
            params.addValue(STATE_REASON.name(), run.getStateReasonMessage());
            params.addValue(NON_PAUSE.name(), run.isNonPause());
            addInstanceFields(run, params);
            return params;
        }

        private static void addInstanceFields(PipelineRun run, MapSqlParameterSource params) {
            Optional<RunInstance> instance = Optional.ofNullable(run.getInstance());
            params.addValue(NODE_TYPE.name(), instance.map(RunInstance::getNodeType).orElse(null));
            params.addValue(NODE_IP.name(), instance.map(RunInstance::getNodeIP).orElse(null));
            params.addValue(NODE_ID.name(), instance.map(RunInstance::getNodeId).orElse(null));
            params.addValue(NODE_DISK.name(), instance.map(RunInstance::getNodeDisk).orElse(null));
            params.addValue(NODE_IMAGE.name(), instance.map(RunInstance::getNodeImage).orElse(null));
            params.addValue(NODE_NAME.name(), instance.map(RunInstance::getNodeName).orElse(null));
            params.addValue(IS_SPOT.name(), instance.map(RunInstance::getSpot).orElse(null));
            params.addValue(NODE_CLOUD_REGION.name(), instance.map(RunInstance::getCloudRegionId).orElse(null));
            params.addValue(NODE_REAL_DISK.name(), instance.map(RunInstance::getEffectiveNodeDisk).orElse(null));
            params.addValue(NODE_CLOUD_PROVIDER.name(),
                            instance.map(RunInstance::getCloudProvider).map(CloudProvider::name).orElse(null));
        }



        static RowMapper<PipelineRun> getRowMapper() {
            return (rs, rowNum) -> parsePipelineRun(rs);
        }

        public static PipelineRun parsePipelineRun(ResultSet rs) throws SQLException {
            PipelineRun run = new PipelineRun();
            run.setId(rs.getLong(RUN_ID.name()));
            long pipelineId = rs.getLong(PIPELINE_ID.name());
            if (!rs.wasNull()) {
                run.setPipelineId(pipelineId);
                run.setParent(new Pipeline(pipelineId));
            }
            run.setVersion(rs.getString(VERSION.name()));
            run.setStartDate(new Date(rs.getTimestamp(START_DATE.name()).getTime()));
            run.setParams(rs.getString(PARAMETERS.name()));
            run.setStatus(TaskStatus.getById(rs.getLong(STATUS.name())));
            run.setCommitStatus(CommitStatus.getById(rs.getLong(COMMIT_STATUS.name())));
            run.setLastChangeCommitTime(new Date(rs.getTimestamp(LAST_CHANGE_COMMIT_TIME.name()).getTime()));
            run.setTerminating(rs.getBoolean(TERMINATING.name()));
            run.setPodId(rs.getString(POD_ID.name()));
            run.setPodIP(rs.getString(POD_IP.name()));
            run.setOwner(rs.getString(OWNER.name()));
            run.setConfigName(rs.getString(CONFIG_NAME.name()));
            run.setNodeCount(rs.getInt(NODE_COUNT.name()));

            Timestamp end = rs.getTimestamp(END_DATE.name());
            if (!rs.wasNull()) {
                run.setEndDate(new Date(end.getTime()));
            }

            run.setDockerImage(rs.getString(DOCKER_IMAGE.name()));
            run.setCmdTemplate(rs.getString(CMD_TEMPLATE.name()));
            run.setActualCmd(rs.getString(ACTUAL_CMD.name()));
            RunInstance instance = new RunInstance();
            instance.setNodeDisk(rs.getInt(NODE_DISK.name()));
            instance.setEffectiveNodeDisk(rs.getInt(NODE_REAL_DISK.name()));
            instance.setNodeId(rs.getString(NODE_ID.name()));
            instance.setNodeIP(rs.getString(NODE_IP.name()));
            instance.setNodeType(rs.getString(NODE_TYPE.name()));
            instance.setNodeImage(rs.getString(NODE_IMAGE.name()));
            instance.setNodeName(rs.getString(NODE_NAME.name()));
            instance.setCloudRegionId(rs.getLong(NODE_CLOUD_REGION.name()));
            instance.setCloudProvider(CloudProvider.valueOf(rs.getString(NODE_CLOUD_PROVIDER.name())));

            boolean spot = rs.getBoolean(IS_SPOT.name());
            if (!rs.wasNull()) {
                instance.setSpot(spot);
            }
            if (!instance.isEmpty()) {
                run.setInstance(instance);
            }
            run.setTimeout(rs.getLong(TIMEOUT.name()));
            run.setServiceUrl(rs.getString(SERVICE_URL.name()));
            Long parentRunId = rs.getLong(PARENT_ID.name());
            if (!rs.wasNull()) {
                run.setParentRunId(parentRunId);
            }
            run.parseParameters();
            Array entitiesIdsArray = rs.getArray(ENTITIES_IDS.name());
            if (entitiesIdsArray != null) {
                List<Long> entitiesIds = Arrays.asList((Long[]) entitiesIdsArray.getArray());
                run.setEntitiesIds(entitiesIds);
            }
            run.setConfigurationId(rs.getLong(CONFIGURATION_ID.name()));
            run.setPodStatus(rs.getString(POD_STATUS.name()));

            Timestamp lastNotificationTime = rs.getTimestamp(LAST_NOTIFICATION_TIME.name());
            if (!rs.wasNull()) {
                run.setLastNotificationTime(new Date(lastNotificationTime.getTime()));
            }

            Timestamp lastIdleNotifiactionTime = rs.getTimestamp(LAST_IDLE_NOTIFICATION_TIME.name());
            if (!rs.wasNull()) {
                run.setLastIdleNotificationTime(lastIdleNotifiactionTime.toLocalDateTime()); // convert to UTC
            }

            Timestamp idleNotificationStartingTime = rs.getTimestamp(PROLONGED_AT_TIME.name());
            if (!rs.wasNull()) {
                run.setProlongedAtTime(idleNotificationStartingTime.toLocalDateTime());
            }
            run.setPrettyUrl(rs.getString(PRETTY_URL.name()));
            run.setPricePerHour(rs.getBigDecimal(PRICE_PER_HOUR.name()));
            String stateReasonMessage = rs.getString(STATE_REASON.name());
            if (!rs.wasNull()) {
                run.setStateReasonMessage(stateReasonMessage);
            }
            boolean nonPause = rs.getBoolean(NON_PAUSE.name());
            if (!rs.wasNull()) {
                run.setNonPause(nonPause);
            }
            return run;
        }

        static RowMapper<PipelineRun> getExtendedRowMapper() {
            return getExtendedRowMapper(false);
        }

        static RowMapper<PipelineRun> getExtendedRowMapper(final boolean loadEnvVars) {
            return (rs, rowNum) -> {
                PipelineRun run = parsePipelineRun(rs);
                run.setPipelineName(rs.getString(PIPELINE_NAME.name()));
                run.setInitialized(rs.getBoolean(INITIALIZATION_FINISHED.name()));
                if (loadEnvVars) {
                    run.setEnvVars(getEnvVarsRowMapper().mapRow(rs, rowNum));
                }
                return run;
            };
        }

        static RowMapper<Map<String, String>> getEnvVarsRowMapper() {
            return (rs, rowNum) -> JsonMapper.parseData(rs.getString(ENV_VARS.name()),
                                                        new TypeReference<Map<String, String>>() {});
        }
    }
}
