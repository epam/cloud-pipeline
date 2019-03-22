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

package com.epam.pipeline.dao.filter;

import com.epam.pipeline.dao.pipeline.PipelineRunDao;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.manager.filter.AclExpressionType;
import com.epam.pipeline.manager.filter.FilterField;
import com.epam.pipeline.manager.filter.FilterOperandType;
import com.epam.pipeline.manager.filter.composers.ListComposer;
import com.epam.pipeline.manager.filter.composers.PipelineRunParameterComposer;
import com.epam.pipeline.manager.filter.composers.WildCardComposer;
import com.epam.pipeline.manager.filter.converters.CommitStatusConverter;
import com.epam.pipeline.manager.filter.converters.DateConverter;
import com.epam.pipeline.manager.filter.converters.IntegerConverter;
import com.epam.pipeline.manager.filter.converters.LongListConverter;
import com.epam.pipeline.manager.filter.converters.PipelineRunParameterConverter;
import com.epam.pipeline.manager.filter.converters.PipelineRunParentIdParameterConverter;
import com.epam.pipeline.manager.filter.converters.RunStatusConverter;
import com.epam.pipeline.manager.filter.converters.WildCardConverter;
import org.springframework.jdbc.core.RowMapper;

public enum FilterRunParameters {
    @FilterField(
            displayName = "run.id",
            databaseTableAlias = "r",
            databaseFieldName = "run_id",
            supportedOperands = {FilterOperandType.EQUALS, FilterOperandType.NOT_EQUALS},
            description = "Pipeline run identifier",
            converter = IntegerConverter.class)
    @FilterField(
            displayName = "id",
            databaseTableAlias = "r",
            databaseFieldName = "run_id",
            supportedOperands = {FilterOperandType.EQUALS, FilterOperandType.NOT_EQUALS},
            description = "Pipeline run identifier",
            converter = IntegerConverter.class)
    RUN_ID,
    @FilterField(
            displayName = "pipeline.id",
            databaseTableAlias = "r",
            description = "Pipeline identifier",
            supportedOperands = {FilterOperandType.EQUALS, FilterOperandType.NOT_EQUALS},
            converter = IntegerConverter.class)
    PIPELINE_ID,
    @FilterField(
            aclField = AclExpressionType.PIPELINE_IDS,
            displayName = "pipeline.ids",
            databaseTableAlias = "r",
            databaseFieldName = "PIPELINE_ID",
            description = "Pipeline identifiers",
            supportedOperands = {FilterOperandType.EQUALS, FilterOperandType.NOT_EQUALS},
            converter = LongListConverter.class,
            composer = ListComposer.class)
    PIPELINE_IDS,
    @FilterField(
            displayName = "pipeline.version",
            databaseTableAlias = "r",
            description = "Pipeline version",
            composer = WildCardComposer.class,
            converter = WildCardConverter.class,
            supportedOperands = {FilterOperandType.EQUALS, FilterOperandType.NOT_EQUALS})
    VERSION,
    @FilterField(
            displayName = "run.start",
            databaseTableAlias = "r",
            databaseFieldName = "start_date",
            description = "Pipeline run start date",
            converter = DateConverter.class)
    @FilterField(
            displayName = "startDate",
            databaseTableAlias = "r",
            databaseFieldName = "start_date",
            description = "Pipeline run start date",
            converter = DateConverter.class)
    START_DATE,
    @FilterField(
            displayName = "run.end",
            databaseTableAlias = "r",
            databaseFieldName = "end_date",
            description = "Pipeline run finish date",
            converter = DateConverter.class)
    @FilterField(
            displayName = "endDate",
            databaseTableAlias = "r",
            databaseFieldName = "end_date",
            description = "Pipeline run finish date",
            converter = DateConverter.class)
    END_DATE,
    PARAMETERS,
    @FilterField(
            displayName = "parent.id",
            databaseTableAlias = "r",
            databaseFieldName = "parameters",
            multiplePlaceholders = true,
            supportedOperands = {FilterOperandType.EQUALS, FilterOperandType.NOT_EQUALS},
            converter = PipelineRunParentIdParameterConverter.class,
            composer = PipelineRunParameterComposer.class,
            description = "Parent run identifier"
    )
    PARENT_ID,
    @FilterField(
            displayName = "run.status",
            databaseTableAlias = "r",
            supportedOperands = {FilterOperandType.EQUALS, FilterOperandType.NOT_EQUALS},
            description = "Pipeline run status",
            converter = RunStatusConverter.class)
    @FilterField(
            displayName = "status",
            databaseTableAlias = "r",
            supportedOperands = {FilterOperandType.EQUALS, FilterOperandType.NOT_EQUALS},
            description = "Pipeline run status",
            converter = RunStatusConverter.class)
    STATUS,
    @FilterField(
            displayName = "commit.status",
            databaseTableAlias = "r",
            supportedOperands = {FilterOperandType.EQUALS, FilterOperandType.NOT_EQUALS},
            description = "Pipeline run commit status",
            converter = CommitStatusConverter.class)
    COMMIT_STATUS,
    LAST_CHANGE_COMMIT_TIME,
    TERMINATING,
    @FilterField(
            displayName = "pod.id",
            databaseTableAlias = "r",
            description = "Pipeline run pod identifier",
            supportedOperands = {FilterOperandType.EQUALS, FilterOperandType.NOT_EQUALS})
    POD_ID,
    @FilterField(
            displayName = "pipeline.name",
            databaseTableAlias = "pipelines",
            description = "Pipeline name",
            composer = WildCardComposer.class,
            converter = WildCardConverter.class,
            supportedOperands = {FilterOperandType.EQUALS, FilterOperandType.NOT_EQUALS})
    PIPELINE_NAME,
    @FilterField(
            displayName = "config.name",
            databaseTableAlias = "r",
            description = "Pipeline configuration name",
            composer = WildCardComposer.class,
            converter = WildCardConverter.class,
            supportedOperands = {FilterOperandType.EQUALS, FilterOperandType.NOT_EQUALS})
    CONFIG_NAME,
    @FilterField(
            displayName = "node.type",
            databaseTableAlias = "r",
            description = "Pipeline run node type",
            composer = WildCardComposer.class,
            converter = WildCardConverter.class,
            supportedOperands = {FilterOperandType.EQUALS, FilterOperandType.NOT_EQUALS})
    NODE_TYPE,
    @FilterField(
            displayName = "node.ip",
            databaseTableAlias = "r",
            description = "Pipeline run node IP address",
            composer = WildCardComposer.class,
            converter = WildCardConverter.class,
            supportedOperands = {FilterOperandType.EQUALS, FilterOperandType.NOT_EQUALS})
    NODE_IP,
    @FilterField(
            displayName = "node.id",
            databaseTableAlias = "r",
            description = "Pipeline run node identifier",
            composer = WildCardComposer.class,
            converter = WildCardConverter.class,
            supportedOperands = {FilterOperandType.EQUALS, FilterOperandType.NOT_EQUALS})
    NODE_ID,
    @FilterField(
            displayName = "node.disk",
            databaseTableAlias = "r",
            description = "Pipeline run node disk size",
            converter = IntegerConverter.class)
    NODE_DISK,
    @FilterField(
            displayName = "node.image",
            databaseTableAlias = "r",
            description = "Pipeline run node image",
            composer = WildCardComposer.class,
            converter = WildCardConverter.class,
            supportedOperands = {FilterOperandType.EQUALS, FilterOperandType.NOT_EQUALS})
    NODE_IMAGE,
    @FilterField(
            displayName = "node.name",
            databaseTableAlias = "r",
            description = "Pipeline run node name",
            composer = WildCardComposer.class,
            converter = WildCardConverter.class,
            supportedOperands = {FilterOperandType.EQUALS, FilterOperandType.NOT_EQUALS})
    NODE_NAME,
    @FilterField(
            displayName = "docker.image",
            databaseTableAlias = "r",
            description = "Pipeline run docker image",
            composer = WildCardComposer.class,
            converter = WildCardConverter.class,
            supportedOperands = {FilterOperandType.EQUALS, FilterOperandType.NOT_EQUALS})
    DOCKER_IMAGE,
    CMD_TEMPLATE,
    ACTUAL_CMD,
    TIMEOUT,
    @FilterField(
            displayName = "run.owner",
            databaseTableAlias = "r",
            description = "Pipeline run owner",
            composer = WildCardComposer.class,
            converter = WildCardConverter.class,
            supportedOperands = {FilterOperandType.EQUALS, FilterOperandType.NOT_EQUALS})
    @FilterField(
            aclField = AclExpressionType.OWNERSHIP,
            displayName = "owner",
            databaseTableAlias = "r",
            description = "Pipeline run owner",
            composer = WildCardComposer.class,
            converter = WildCardConverter.class,
            supportedOperands = {FilterOperandType.EQUALS, FilterOperandType.NOT_EQUALS})
    OWNER,
    SERVICE_URL,
    PIPELINE_ALLOWED,
    OWNERSHIP,
    @FilterField(
            displayName = "pod.ip",
            databaseTableAlias = "r",
            description = "Container IP address",
            supportedOperands = {FilterOperandType.EQUALS, FilterOperandType.NOT_EQUALS})
    POD_IP,
    SSH_PASSWORD,

    START_DATE_FROM,
    END_DATE_TO,

    @FilterField(
            isRegex = true,
            displayName = "parameter\\.[^ \\/]+",
            databaseTableAlias = "r",
            databaseFieldName = "parameters",
            multiplePlaceholders = true,
            supportedOperands = {FilterOperandType.EQUALS},
            converter = PipelineRunParameterConverter.class,
            composer = PipelineRunParameterComposer.class
    )
    OTHER_PARAMETER;

    static RowMapper<PipelineRun> getRowMapper() {
        return (rs, rowNum) -> {
            PipelineRun run = PipelineRunDao.PipelineRunParameters.parsePipelineRun(rs);
            run.setPipelineName(rs.getString(PIPELINE_NAME.name()));
            return run;
        };
    }
}
