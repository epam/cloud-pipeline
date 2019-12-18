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

package com.epam.pipeline.dao.pipeline;

import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.controller.vo.PagingRunFilterVO;
import com.epam.pipeline.controller.vo.PipelineRunFilterVO;
import com.epam.pipeline.dao.DaoHelper;
import com.epam.pipeline.entity.BaseEntity;
import com.epam.pipeline.entity.pipeline.CommitStatus;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.run.ExecutionPreferences;
import com.epam.pipeline.entity.pipeline.run.parameter.RunAccessType;
import com.epam.pipeline.entity.pipeline.run.parameter.RunSid;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.entity.user.PipelineUser;
import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcDaoSupport;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Array;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class PipelineRunDao extends NamedParameterJdbcDaoSupport {
    private Pattern wherePattern = Pattern.compile("@WHERE@");
    private static final String AND = " AND ";
    private static final String POSTGRE_TYPE_BIGINT = "BIGINT";
    private static final int STRING_BUFFER_SIZE = 70;

    @Autowired
    private DaoHelper daoHelper;

    @Value("${run.pipeline.init.task.name?:InitializeEnvironment}")
    private String initTaskName;

    @Value("${run.pipeline.nodeup.task.name?:InitializeNode}")
    private String nodeUpTaskName;

    private TaskStatus initTaskStatus = TaskStatus.SUCCESS;

    private String pipelineRunSequence;
    private String createPipelineRunQuery;
    private String loadAllRunsByVersionIdQuery;
    private String loadRunByIdQuery;
    private String loadSshPasswordQuery;
    private String updateRunStatusQuery;
    private String updateRunCommitStatusQuery;
    private String loadAllRunsByPipelineIdQuery;
    private String loadAllRunsByPipelineIdAndVersionQuery;
    private String loadRunningAndTerminatedPipelineRunsQuery;
    private String loadRunningPipelineRunsQuery;
    private String loadActiveServicesQuery;
    private String countActiveServicesQuery;
    private String loadTerminatingPipelineRunsQuery;
    private String searchPipelineRunsBaseQuery;
    private String countFilteredPipelineRunsBaseQuery;
    private String loadPipelineRunsWithPipelineByIdsQuery;
    private String updateRunInstanceQuery;
    private String updatePodIPQuery;
    private String deleteRunsByPipelineQuery;
    private String updateServiceUrlQuery;
    private String loadRunsGroupingQuery;
    private String countRunGroupsQuery;
    private String createPipelineRunSidsQuery;
    private String deleteRunSidsByRunIdQuery;
    private String loadRunSidsQuery;
    private String loadRunSidsQueryForList;
    private String updatePodStatusQuery;
    private String loadEnvVarsQuery;
    private String updateLastNotificationQuery;

    private String updateProlongedAtTimeAndLastIdleNotificationTimeQuery;

    private String updateRunQuery;
    private String loadRunByPrettyUrlQuery;
    private String updateTagsQuery;
    private String loadAllRunsPossiblyActiveInPeriodQuery;

    // We put Propagation.REQUIRED here because this method can be called from non-transaction context
    // (see PipelineRunManager, it performs internal call for launchPipeline)

    @Transactional(propagation = Propagation.REQUIRED)
    public Long createRunId() {
        return daoHelper.createId(pipelineRunSequence);
    }
    // We put Propagation.REQUIRED here because this method can be called from non-transaction context
    // (see PipelineRunManager, it performs internal call for launchPipeline)

    @Transactional(propagation = Propagation.REQUIRED)
    public void createPipelineRun(PipelineRun run) {
        if (run.getId() == null) {
            run.setId(createRunId());
        }

        getNamedParameterJdbcTemplate().update(createPipelineRunQuery,
                PipelineRunParameters.getParameters(run, getConnection()));

        createRunSids(run.getId(), run.getRunSids());
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public PipelineRun loadPipelineRun(Long id) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue(PipelineRunParameters.RUN_ID.name(), id);
        addTaskStatusParams(params);

        List<PipelineRun> items = getNamedParameterJdbcTemplate().query(loadRunByIdQuery, params,
                PipelineRunParameters.getExtendedRowMapper());
        if (!items.isEmpty()) {
            PipelineRun pipelineRun = items.get(0);
            List<RunSid> runSids = getJdbcTemplate().query(loadRunSidsQuery,
                    PipelineRunParameters.getRunSidsRowMapper(), id);
            pipelineRun.setRunSids(runSids);
            List<Map<String, String>> envVars = getJdbcTemplate().query(loadEnvVarsQuery,
                    PipelineRunParameters.getEnvVarsRowMapper(), pipelineRun.getId());
            pipelineRun.setEnvVars(CollectionUtils.isEmpty(envVars) ? null : envVars.get(0));
            return pipelineRun;
        } else {
            return null;
        }
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public List<PipelineRun> loadPipelineRunsActiveInPeriod(final LocalDateTime start, final LocalDateTime end) {
        final MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("PERIOD_START", start);
        params.addValue("PERIOD_END", end);
        return getNamedParameterJdbcTemplate().query(loadAllRunsPossiblyActiveInPeriodQuery,
                                                     params,
                                                     PipelineRunParameters.getRowMapper());
    }

    public String loadSshPassword(Long id) {
        List<String> items = getJdbcTemplate().query(loadSshPasswordQuery,
                PipelineRunParameters.getPasswordRowMapper(), id);
        return !items.isEmpty() ? items.get(0) : null;
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    public List<PipelineRun> loadPipelineRuns(List<Long> runIds) {
        if (runIds.isEmpty()) {
            return new ArrayList<>();
        }
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("list", runIds);
        addTaskStatusParams(params);
        return getNamedParameterJdbcTemplate().query(loadPipelineRunsWithPipelineByIdsQuery,
                params,
                PipelineRunParameters.getExtendedRowMapper());
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    public List<PipelineRun> loadAllRunsForVersion(String version) {
        return getJdbcTemplate().query(loadAllRunsByVersionIdQuery,
                PipelineRunParameters.getRowMapper(), version);
    }

    public List<PipelineRun> loadAllRunsForPipeline(Long pipelineId) {
        return getJdbcTemplate().query(loadAllRunsByPipelineIdQuery,
                PipelineRunParameters.getRowMapper(), pipelineId);
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    public List<PipelineRun> loadAllRunsForPipeline(Long pipelineId, String version) {
        return getJdbcTemplate().query(loadAllRunsByPipelineIdAndVersionQuery,
                PipelineRunParameters.getRowMapper(), pipelineId, version);
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    public Optional<PipelineRun> loadRunByPrettyUrl(String prettyUrl) {
        return getJdbcTemplate().query(loadRunByPrettyUrlQuery,
                PipelineRunParameters.getRowMapper(), prettyUrl).stream().findFirst();
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void updateRun(PipelineRun run) {
        getNamedParameterJdbcTemplate().update(updateRunQuery, PipelineRunParameters
                .getParameters(run, getConnection()));
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void updateRunStatus(PipelineRun run) {
        getNamedParameterJdbcTemplate().update(updateRunStatusQuery, PipelineRunParameters
                .getParameters(run, getConnection()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void updateRunCommitStatus(PipelineRun run) {
        getNamedParameterJdbcTemplate().update(updateRunCommitStatusQuery, PipelineRunParameters
            .getParameters(run, getConnection()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void updatePodStatus(PipelineRun run) {
        getNamedParameterJdbcTemplate().update(updatePodStatusQuery, PipelineRunParameters
                .getParameters(run, getConnection()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void updateRunInstance(PipelineRun run) {
        getNamedParameterJdbcTemplate().update(updateRunInstanceQuery, PipelineRunParameters
                .getParameters(run, getConnection()));
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void updatePodIP(PipelineRun run) {
        getNamedParameterJdbcTemplate().update(updatePodIPQuery, PipelineRunParameters
                .getParameters(run, getConnection()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void updateRunLastNotification(PipelineRun run) {
        getNamedParameterJdbcTemplate().update(updateLastNotificationQuery, PipelineRunParameters
                .getParameters(run, getConnection()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void updateRunsLastNotification(Collection<PipelineRun> runs) {
        if (CollectionUtils.isEmpty(runs)) {
            return;
        }

        MapSqlParameterSource[] params = runs.stream()
            .map(run -> PipelineRunParameters.getParameters(run, getConnection()))
            .toArray(MapSqlParameterSource[]::new);

        getNamedParameterJdbcTemplate().batchUpdate(updateLastNotificationQuery, params);
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    public List<PipelineRun> loadRunningAndTerminatedPipelineRuns() {
        return getJdbcTemplate().query(loadRunningAndTerminatedPipelineRunsQuery,
                PipelineRunParameters.getExtendedRowMapper(true));
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    public List<PipelineRun> loadRunningPipelineRuns() {
        return getJdbcTemplate().query(loadRunningPipelineRunsQuery, PipelineRunParameters.getExtendedRowMapper());
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    public List<PipelineRun> loadActiveSharedRuns(final PagingRunFilterVO filter, final PipelineUser user) {
        final MapSqlParameterSource params = getPagingParameters(filter);
        final String query = wherePattern.matcher(loadActiveServicesQuery)
                .replaceFirst(makeRunSidsCondition(user, params));
        final List<PipelineRun> services = getNamedParameterJdbcTemplate()
                .query(query, params, PipelineRunParameters.getRowMapper());
        if (CollectionUtils.isEmpty(services)) {
            return services;
        }
        final MapSqlParameterSource sidParams = new MapSqlParameterSource();
        final Map<Long, PipelineRun> idToRun = services.stream().collect(Collectors.toMap(BaseEntity::getId,
                Function.identity()));
        sidParams.addValue("list", idToRun.keySet());
        final List<RunSid> runSids = getNamedParameterJdbcTemplate()
                .query(loadRunSidsQueryForList, sidParams, PipelineRunParameters.getRunSidsRowMapper());
        ListUtils.emptyIfNull(runSids).forEach(sid -> {
            final PipelineRun run = idToRun.get(sid.getRunId());
            if (run.getRunSids() == null) {
                run.setRunSids(new ArrayList<>());
            }
            run.getRunSids().add(sid);
        });
        return services;
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    public int countActiveSharedRuns(PipelineUser user) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String query = wherePattern.matcher(countActiveServicesQuery).replaceFirst(makeRunSidsCondition(user, params));
        return getNamedParameterJdbcTemplate().queryForObject(query, params, Integer.class);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteRunsByPipeline(Long id) {
        getJdbcTemplate().update(deleteRunsByPipelineQuery, id);
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    public List<PipelineRun> loadTerminatingRuns() {
        return getJdbcTemplate().query(loadTerminatingPipelineRunsQuery, PipelineRunParameters.getRowMapper());
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    public List<PipelineRun> searchPipelineRuns(PagingRunFilterVO filter) {
        return searchPipelineRuns(filter, null);
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    public List<PipelineRun> searchPipelineRuns(PagingRunFilterVO filter,
                                                PipelineRunFilterVO.ProjectFilter projectFilter) {
        MapSqlParameterSource params = getPagingParameters(filter);
        String query = wherePattern.matcher(searchPipelineRunsBaseQuery).replaceFirst(makeFilterCondition(filter,
                projectFilter, params, true));
        return getNamedParameterJdbcTemplate().query(query, params, PipelineRunParameters.getExtendedRowMapper());
    }

    public List<PipelineRun> searchPipelineGroups(PagingRunFilterVO filter,
                                                  PipelineRunFilterVO.ProjectFilter projectFilter) {
        MapSqlParameterSource params = getPagingParameters(filter);
        String query = wherePattern.matcher(loadRunsGroupingQuery)
                .replaceFirst(makeFilterCondition(filter, projectFilter, params, false));
        Collection<PipelineRun> runs = getNamedParameterJdbcTemplate()
                .query(query, params, PipelineRunParameters.getRunGroupExtractor());
        return runs.stream()
                .filter(run -> run.getParentRunId() == null)
                .sorted(getPipelineRunComparator())
                .collect(Collectors.toList());
    }

    public Integer countRootRuns(PipelineRunFilterVO filter, PipelineRunFilterVO.ProjectFilter projectFilter) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String query = wherePattern.matcher(countRunGroupsQuery).replaceFirst(makeFilterCondition(filter,
                projectFilter, params, false));
        return getNamedParameterJdbcTemplate().queryForObject(query, params, Integer.class);
    }

    /**
     * Updates service url for the provided run in a database
     * @param run run with updated service url
     **/
    @Transactional(propagation = Propagation.MANDATORY)
    public void updateServiceUrl(PipelineRun run) {
        getNamedParameterJdbcTemplate().update(updateServiceUrlQuery, PipelineRunParameters
                .getParameters(run, getConnection()));
    }

    /**
     * Updates tags the provided run in a database
     * @param run run with updated tags
     **/
    @Transactional(propagation = Propagation.MANDATORY)
    public void updateRunTags(final PipelineRun run) {
        getNamedParameterJdbcTemplate().update(updateTagsQuery, PipelineRunParameters
                .getParameters(run, getConnection()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void updateRunsTags(final Collection<PipelineRun> runs) {
        if (CollectionUtils.isEmpty(runs)) {
            return;
        }
        final MapSqlParameterSource[] params = runs.stream()
                .map(run -> PipelineRunParameters.getParameters(run, getConnection()))
                .toArray(MapSqlParameterSource[]::new);
        getNamedParameterJdbcTemplate().batchUpdate(updateTagsQuery, params);
    }

    public int countFilteredPipelineRuns(PipelineRunFilterVO filter, PipelineRunFilterVO.ProjectFilter projectFilter) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String query = wherePattern.matcher(countFilteredPipelineRunsBaseQuery).replaceFirst(makeFilterCondition(filter,
                projectFilter, params, true));
        return getNamedParameterJdbcTemplate().queryForObject(query, params, Integer.class);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void createRunSids(Long runId, List<RunSid> runSids) {
        if (CollectionUtils.isEmpty(runSids)) {
            return;
        }

        getNamedParameterJdbcTemplate().batchUpdate(createPipelineRunSidsQuery,
                PipelineRunParameters.getRunSidsParameters(runId, runSids));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteRunSids(Long runId) {
        getJdbcTemplate().update(deleteRunSidsByRunIdQuery, runId);
    }

    private MapSqlParameterSource getPagingParameters(PagingRunFilterVO filter) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("LIMIT", filter.getPageSize());
        params.addValue("OFFSET", (filter.getPage() - 1) * filter.getPageSize());
        addTaskStatusParams(params);
        return params;
    }

    private int addOwnerClause(MapSqlParameterSource params, StringBuilder whereBuilder, int clausesCount,
                               List<String> owners) {
        if (!CollectionUtils.isEmpty(owners)) {
            appendAnd(whereBuilder, clausesCount);
            whereBuilder.append(" lower(r.owner) in (:")
                    .append(PipelineRunParameters.OWNER.name())
                    .append(')');
            params.addValue(PipelineRunParameters.OWNER.name(),
                    owners.stream().map(String::toLowerCase).collect(Collectors.toList()));
            clausesCount++;
        }
        return clausesCount;
    }


    private String makeFilterCondition(PipelineRunFilterVO filter,
                                       PipelineRunFilterVO.ProjectFilter projectFilter,
                                       MapSqlParameterSource params,
                                       boolean firstCondition) {
        if (filter.isEmpty()) {
            return "";
        }

        StringBuilder whereBuilder = new StringBuilder();
        int clausesCount = firstCondition ? 0 : 1;
        if (firstCondition) {
            whereBuilder.append(" WHERE ");
        }
        if (CollectionUtils.isNotEmpty(filter.getVersions())) {
            appendAnd(whereBuilder, clausesCount);
            whereBuilder.append(" r.version in (:")
                    .append(PipelineRunParameters.VERSION.name())
                    .append(')');
            params.addValue(PipelineRunParameters.VERSION.name(), filter.getVersions());
            clausesCount++;
        }

        clausesCount = addOwnerClause(params, whereBuilder, clausesCount, filter.getOwners());

        if (CollectionUtils.isNotEmpty(filter.getPipelineIds())) {
            appendAnd(whereBuilder, clausesCount);
            whereBuilder.append(" r.pipeline_id in (:")
                    .append(PipelineRunParameters.PIPELINE_ID.name())
                    .append(')');
            params.addValue(PipelineRunParameters.PIPELINE_ID.name(), filter.getPipelineIds());
            clausesCount++;
        }

        if (CollectionUtils.isNotEmpty(filter.getDockerImages())) {
            appendAnd(whereBuilder, clausesCount);
            whereBuilder.append(" r.docker_image like any (array[ :")
                    .append(PipelineRunParameters.DOCKER_IMAGE.name())
                    .append(" ])");
            List<String> dockerImages = new ArrayList<>();
            filter.getDockerImages().forEach(di -> {
                dockerImages.add(di);
                dockerImages.add(di + ":%");
            });
            params.addValue(PipelineRunParameters.DOCKER_IMAGE.name(), dockerImages);
            clausesCount++;
        }

        if (CollectionUtils.isNotEmpty(filter.getStatuses())) {
            appendAnd(whereBuilder, clausesCount);
            whereBuilder.append(" r.status in (:")
                    .append(PipelineRunParameters.STATUS.name())
                    .append(')');
            params.addValue(PipelineRunParameters.STATUS.name(), filter.getStatuses()
                    .stream().map(TaskStatus::getId).collect(Collectors.toList()));
            clausesCount++;
        }

        if (filter.getStartDateFrom() != null) {
            appendAnd(whereBuilder, clausesCount);
            whereBuilder.append(" r.start_date >= :").append(PipelineRunParameters.START_DATE_FROM.name());
            params.addValue(PipelineRunParameters.START_DATE_FROM.name(), filter.getStartDateFrom());
            clausesCount++;
        }

        if (filter.getEndDateTo() != null) {
            appendAnd(whereBuilder, clausesCount);
            whereBuilder.append(" r.end_date <= :").append(PipelineRunParameters.END_DATE_TO.name());
            params.addValue(PipelineRunParameters.END_DATE_TO.name(), filter.getEndDateTo());
            clausesCount++;
        }

        if (StringUtils.isNotBlank(filter.getPartialParameters())) {
            appendAnd(whereBuilder, clausesCount);
            whereBuilder.append(" r.parameters like :").append(PipelineRunParameters.PARAMETERS.name());
            params.addValue(PipelineRunParameters.PARAMETERS.name(),
                    String.format("%%%s%%", filter.getPartialParameters()));
            clausesCount++;
        }

        if (filter.getParentId() != null) {
            appendAnd(whereBuilder, clausesCount);

            whereBuilder.append(String.format(" (r.parent_id = %d OR r.parameters = 'parent-id=%d' "
                            + "OR r.parameters like "
                            + "any(array['%%|parent-id=%d|%%','%%|parent-id=%d','parent-id=%d|%%', " +
                            "'parent-id=%d=%%', '%%|parent-id=%d=%%']))",
                    filter.getParentId(),
                    filter.getParentId(),
                    filter.getParentId(),
                    filter.getParentId(),
                    filter.getParentId(),
                    filter.getParentId(),
                    filter.getParentId()));
            clausesCount++;
        }

        if (CollectionUtils.isNotEmpty(filter.getEntitiesIds())) {
            appendAnd(whereBuilder, clausesCount);
            whereBuilder.append(String.format(" r.entities_ids && '%s'",
                    mapListToSqlArray(filter.getEntitiesIds(), getConnection())));
            clausesCount++;
        }

        if (CollectionUtils.isNotEmpty(filter.getConfigurationIds())) {
            appendAnd(whereBuilder, clausesCount);
            whereBuilder.append(String.format(" r.configuration_id IN (:%s)",
                    PipelineRunParameters.CONFIGURATION_ID.name()));
            params.addValue(PipelineRunParameters.CONFIGURATION_ID.name(), filter.getConfigurationIds());
            clausesCount++;
        }
        appendProjectFilter(projectFilter, params, whereBuilder, clausesCount);
        appendAclFilters(filter, params, whereBuilder, clausesCount);

        return whereBuilder.toString();
    }

    private void appendAnd(StringBuilder whereBuilder, int clausesCount) {
        if (clausesCount > 0) {
            whereBuilder.append(AND);
        }
    }

    private void appendProjectFilter(PipelineRunFilterVO.ProjectFilter projectFilter, MapSqlParameterSource params,
                                     StringBuilder whereBuilder, int clausesCount) {
        if (projectFilter == null || projectFilter.isEmpty()) {
            return;
        }
        appendAnd(whereBuilder, clausesCount);
        whereBuilder.append('(');

        if (CollectionUtils.isNotEmpty(projectFilter.getPipelineIds())) {
            params.addValue(PipelineRunParameters.PROJECT_PIPELINES.name(), projectFilter.getPipelineIds());
            whereBuilder.append(
                    String.format(" r.pipeline_id in (:%s) ", PipelineRunParameters.PROJECT_PIPELINES.name()));
            if (CollectionUtils.isNotEmpty(projectFilter.getConfigurationIds())) {
                whereBuilder.append(" OR ");
            }
        }
        if (CollectionUtils.isNotEmpty(projectFilter.getConfigurationIds())) {
            params.addValue(PipelineRunParameters.PROJECT_CONFIGS.name(), projectFilter.getConfigurationIds());
            whereBuilder.append(String.format(" r.configuration_id IN (:%s) ",
                    PipelineRunParameters.PROJECT_CONFIGS.name()));
        }

        whereBuilder.append(')');
    }

    private void appendAclFilters(PipelineRunFilterVO filter, MapSqlParameterSource params,
            StringBuilder whereBuilder, int clausesCount) {
        //ownership filter indicates that acl filtering is applied
        if (StringUtils.isNotBlank(filter.getOwnershipFilter())) {
            appendAnd(whereBuilder, clausesCount);
            params.addValue(PipelineRunParameters.OWNERSHIP.name(), filter.getOwnershipFilter().toLowerCase());
            if (CollectionUtils.isNotEmpty(filter.getAllowedPipelines())) {
                whereBuilder.append(" (r.pipeline_id in (:")
                        .append(PipelineRunParameters.PIPELINE_ALLOWED.name())
                        .append(") OR lower(r.owner) = :")
                        .append(PipelineRunParameters.OWNERSHIP.name()).append(')');
                params.addValue(PipelineRunParameters.PIPELINE_ALLOWED.name(), filter.getAllowedPipelines());
            } else {
                whereBuilder.append(" lower(r.owner) = :").append(PipelineRunParameters.OWNERSHIP.name());
            }
        }
    }

    private String makeRunSidsCondition(PipelineUser user, MapSqlParameterSource params) {
        StringBuilder whereBuilder = new StringBuilder(STRING_BUFFER_SIZE);
        whereBuilder.append(" WHERE ");

        if (StringUtils.isNotBlank(user.getUserName())) {
            whereBuilder.append("((is_principal = TRUE AND name = :")
                    .append(PipelineRunParameters.NAME.name()).append(')');
            params.addValue(PipelineRunParameters.NAME.name(), user.getUserName());
        }

        if (CollectionUtils.isNotEmpty(user.getGroups())) {
            whereBuilder.append(" OR  (is_principal = FALSE AND name IN (:USER_GROUPS))");
            params.addValue("USER_GROUPS", user.getGroups());
        }
        whereBuilder.append(')');
        return whereBuilder.toString();
    }

    public void updateProlongIdleRunAndLastIdleNotificationTime(PipelineRun run) {
        getNamedParameterJdbcTemplate().update(updateProlongedAtTimeAndLastIdleNotificationTimeQuery,
                PipelineRunParameters.getParameters(run, getConnection()));
    }

    private void addTaskStatusParams(MapSqlParameterSource params) {
        params.addValue(PipelineRunParameters.TASK_NAME.name(), initTaskName);
        params.addValue(PipelineRunParameters.TASK_STATUS.name(), initTaskStatus.ordinal());
        params.addValue(PipelineRunParameters.NODEUP_TASK.name(), nodeUpTaskName);
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
        PIPELINE_ALLOWED,
        OWNERSHIP,
        POD_IP,
        SSH_PASSWORD,
        CONFIG_NAME,
        NODE_COUNT,
        START_DATE_FROM,
        END_DATE_TO,
        INITIALIZATION_FINISHED,
        TASK_NAME,
        TASK_STATUS,
        ENTITIES_IDS,
        IS_SPOT,
        CONFIGURATION_ID,
        NAME,
        IS_PRINCIPAL,
        POD_STATUS,
        ENV_VARS,
        LAST_NOTIFICATION_TIME,
        PROLONGED_AT_TIME,
        LAST_IDLE_NOTIFICATION_TIME,
        PROJECT_PIPELINES,
        PROJECT_CONFIGS,
        EXEC_PREFERENCES,
        PRETTY_URL,
        PRICE_PER_HOUR,
        STATE_REASON,
        NON_PAUSE,
        NODE_REAL_DISK,
        QUEUED,
        NODEUP_TASK,
        ACCESS_TYPE,
        TAGS;

        public static final RunAccessType DEFAULT_ACCESS_TYPE = RunAccessType.ENDPOINT;

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
            params.addValue(ENTITIES_IDS.name(), mapListToSqlArray(run.getEntitiesIds(), connection));
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
            params.addValue(TAGS.name(), JsonMapper.convertDataToJsonStringForQuery(run.getTags()));
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

        static ResultSetExtractor<Collection<PipelineRun>> getRunGroupExtractor() {
            return (rs) -> {
                Map<Long, PipelineRun> runs = new HashMap<>();
                Map<Long, List<PipelineRun>> childRuns = new HashMap<>();
                while (rs.next()) {
                    PipelineRun run = parsePipelineRun(rs);
                    run.setPipelineName(rs.getString(PIPELINE_NAME.name()));
                    run.setInitialized(rs.getBoolean(INITIALIZATION_FINISHED.name()));
                    if (run.getInstance() == null || StringUtils.isBlank(run.getInstance().getNodeName())) {
                        run.setQueued(rs.getBoolean(QUEUED.name()));
                    }
                    runs.put(run.getId(), run);
                    if (run.getParentRunId() != null) {
                        childRuns.putIfAbsent(run.getParentRunId(), new ArrayList<>());
                        childRuns.get(run.getParentRunId()).add(run);
                    }
                }
                runs.values().forEach(run -> {
                    if (childRuns.containsKey(run.getId())) {
                        List<PipelineRun> children = childRuns.get(run.getId());
                        children.sort(getPipelineRunComparator());
                        run.setChildRuns(children);
                    }
                });
                return runs.values();
            };
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
            run.setExecutionPreferences(JsonMapper.parseData(rs.getString(EXEC_PREFERENCES.name()),
                    new TypeReference<ExecutionPreferences>() {}));

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
            final String tagsJson = rs.getString(TAGS.name());
            if (!rs.wasNull()) {
                final Map<String, String> newTags =
                    JsonMapper.parseData(tagsJson, new TypeReference<Map<String, String>>() {});
                run.setTags(newTags);
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
                if (run.getInstance() == null || StringUtils.isBlank(run.getInstance().getNodeName())) {
                    run.setQueued(rs.getBoolean(QUEUED.name()));
                }
                if (loadEnvVars) {
                    run.setEnvVars(getEnvVarsRowMapper().mapRow(rs, rowNum));
                }
                return run;
            };
        }

        static RowMapper<String> getPasswordRowMapper() {
            return (rs, rowNum) -> rs.getString(SSH_PASSWORD.name());
        }

        static MapSqlParameterSource[] getRunSidsParameters(Long runId, List<RunSid> runSids) {
            MapSqlParameterSource[] sqlParameterSource = new MapSqlParameterSource[runSids.size()];
            for (int i = 0; i < runSids.size(); i++) {
                MapSqlParameterSource params = new MapSqlParameterSource();
                params.addValue(RUN_ID.name(), runId);
                final RunSid sid = runSids.get(i);
                params.addValue(NAME.name(), sid.getName());
                params.addValue(IS_PRINCIPAL.name(), sid.getIsPrincipal());
                params.addValue(ACCESS_TYPE.name(), Optional.ofNullable(sid.getAccessType())
                        .map(Enum::name)
                        .orElse(DEFAULT_ACCESS_TYPE.name()));
                sqlParameterSource[i] = params;
            }
            return sqlParameterSource;
        }

        static RowMapper<RunSid> getRunSidsRowMapper() {
            return (rs, rowNum) -> {
                RunSid runSid = new RunSid();
                runSid.setRunId(rs.getLong(RUN_ID.name()));
                runSid.setName(rs.getString(NAME.name()));
                runSid.setIsPrincipal(rs.getBoolean(IS_PRINCIPAL.name()));
                final String access = rs.getString(ACCESS_TYPE.name());
                if (StringUtils.isBlank(access)) {
                    runSid.setAccessType(DEFAULT_ACCESS_TYPE);
                } else {
                    runSid.setAccessType(RunAccessType.valueOf(access));
                }
                return runSid;
            };
        }

        static RowMapper<Map<String, String>> getEnvVarsRowMapper() {
            return (rs, rowNum) -> JsonMapper.parseData(rs.getString(ENV_VARS.name()),
                    new TypeReference<Map<String, String>>() {});
        }
    }
    private static Array mapListToSqlArray(List<Long> list, Connection connection) {
        Long[] emptyArray = new Long[0];
        Long[] javaArray = list != null ? list.toArray(emptyArray) : emptyArray;
        Array sqlArray;
        try {
            sqlArray = connection.createArrayOf(POSTGRE_TYPE_BIGINT, javaArray);
        } catch (SQLException e) {
            throw new IllegalArgumentException("Cannot convert data to SQL Array");
        }
        return sqlArray;
    }

    private static Comparator<BaseEntity> getPipelineRunComparator() {
        return Comparator.comparing(BaseEntity::getId).reversed();
    }

    @Required
    public void setPipelineRunSequence(String pipelineRunSequence) {
        this.pipelineRunSequence = pipelineRunSequence;
    }

    @Required
    public void setCreatePipelineRunQuery(String createPipelineRunQuery) {
        this.createPipelineRunQuery = createPipelineRunQuery;
    }

    @Required
    public void setLoadAllRunsByVersionIdQuery(String loadAllRunsByVersionIdQuery) {
        this.loadAllRunsByVersionIdQuery = loadAllRunsByVersionIdQuery;
    }

    @Required
    public void setUpdateRunStatusQuery(String updateRunStatusQuery) {
        this.updateRunStatusQuery = updateRunStatusQuery;
    }

    @Required
    public void setUpdateRunCommitStatusQuery(String updateRunCommitStatusQuery) {
        this.updateRunCommitStatusQuery = updateRunCommitStatusQuery;
    }

    @Required
    public void setLoadRunByIdQuery(String loadRunByIdQuery) {
        this.loadRunByIdQuery = loadRunByIdQuery;
    }

    @Required
    public void setLoadPipelineRunsWithPipelineByIdsQuery(String loadPipelineRunsWithPipelineByIdsQuery) {
        this.loadPipelineRunsWithPipelineByIdsQuery = loadPipelineRunsWithPipelineByIdsQuery;
    }

    @Required
    public void setLoadAllRunsByPipelineIdQuery(String loadAllRunsByPipelineIdQuery) {
        this.loadAllRunsByPipelineIdQuery = loadAllRunsByPipelineIdQuery;
    }

    @Required
    public void setLoadAllRunsByPipelineIdAndVersionQuery(String loadAllRunsByPipelineIdAndVersionQuery) {
        this.loadAllRunsByPipelineIdAndVersionQuery = loadAllRunsByPipelineIdAndVersionQuery;
    }

    @Required
    public void setLoadRunningAndTerminatedPipelineRunsQuery(String loadRunningAndTerminatedPipelineRunsQuery) {
        this.loadRunningAndTerminatedPipelineRunsQuery = loadRunningAndTerminatedPipelineRunsQuery;
    }

    @Required
    public void setLoadActiveServicesQuery(String loadActiveServicesQuery) {
        this.loadActiveServicesQuery = loadActiveServicesQuery;
    }

    @Required
    public void setCountActiveServicesQuery(String countActiveServicesQuery) {
        this.countActiveServicesQuery = countActiveServicesQuery;
    }

    @Required
    public void setLoadTerminatingPipelineRunsQuery(String loadTerminatingPipelineRunsQuery) {
        this.loadTerminatingPipelineRunsQuery = loadTerminatingPipelineRunsQuery;
    }

    @Required
    public void setSearchPipelineRunsBaseQuery(String searchPipelineRunsBaseQuery) {
        this.searchPipelineRunsBaseQuery = searchPipelineRunsBaseQuery;
    }

    @Required
    public void setCountFilteredPipelineRunsBaseQuery(String countFilteredPipelineRunsBaseQuery) {
        this.countFilteredPipelineRunsBaseQuery = countFilteredPipelineRunsBaseQuery;
    }

    @Required
    public void setUpdateRunInstanceQuery(String updateRunInstanceQuery) {
        this.updateRunInstanceQuery = updateRunInstanceQuery;
    }

    @Required
    public void setDeleteRunsByPipelineQuery(String deleteRunsByPipelineQuery) {
        this.deleteRunsByPipelineQuery = deleteRunsByPipelineQuery;
    }

    @Required
    public void setUpdateServiceUrlQuery(String updateServiceUrlQuery) {
        this.updateServiceUrlQuery = updateServiceUrlQuery;
    }

    @Required
    public void setUpdatePodIPQuery(String updatePodIPQuery) {
        this.updatePodIPQuery = updatePodIPQuery;
    }

    @Required
    public void setLoadSshPasswordQuery(String loadSshPasswordQuery) {
        this.loadSshPasswordQuery = loadSshPasswordQuery;
    }

    @Required
    public void setLoadRunsGroupingQuery(String loadRunsGroupingQuery) {
        this.loadRunsGroupingQuery = loadRunsGroupingQuery;
    }

    @Required
    public void setCreatePipelineRunSidsQuery(String createPipelineRunSidsQuery) {
        this.createPipelineRunSidsQuery = createPipelineRunSidsQuery;
    }

    @Required
    public void setDeleteRunSidsByRunIdQuery(String deleteRunSidsByRunIdQuery) {
        this.deleteRunSidsByRunIdQuery = deleteRunSidsByRunIdQuery;
    }

    @Required
    public void setLoadRunSidsQuery(String loadRunSidsQuery) {
        this.loadRunSidsQuery = loadRunSidsQuery;
    }

    @Required
    public void setCountRunGroupsQuery(String countRunGroupsQuery) {
        this.countRunGroupsQuery = countRunGroupsQuery;
    }

    @Required
    public void setUpdatePodStatusQuery(String updatePodStatusQuery) {
        this.updatePodStatusQuery = updatePodStatusQuery;
    }

    @Required
    public void setUpdateProlongedAtTimeAndLastIdleNotificationTimeQuery(
            String updateProlongedAtTimeAndLastIdleNotificationTimeQuery) {
        this.updateProlongedAtTimeAndLastIdleNotificationTimeQuery =
                updateProlongedAtTimeAndLastIdleNotificationTimeQuery;
    }

    @Required
    public void setLoadEnvVarsQuery(String loadEnvVarsQuery) {
        this.loadEnvVarsQuery = loadEnvVarsQuery;
    }

    @Required
    public void setUpdateLastNotificationQuery(String updateLastNotificationQuery) {
        this.updateLastNotificationQuery = updateLastNotificationQuery;
    }

    @Required
    public void setUpdateRunQuery(String updateRunQuery) {
        this.updateRunQuery = updateRunQuery;
    }

    @Required
    public void setLoadRunByPrettyUrlQuery(String loadRunByPrettyUrlQuery) {
        this.loadRunByPrettyUrlQuery = loadRunByPrettyUrlQuery;
    }

    @Required
    public void setLoadRunningPipelineRunsQuery(String loadRunningPipelineRunsQuery) {
        this.loadRunningPipelineRunsQuery = loadRunningPipelineRunsQuery;
    }

    @Required
    public void setLoadRunSidsQueryForList(final String loadRunSidsQueryForList) {
        this.loadRunSidsQueryForList = loadRunSidsQueryForList;
    }

    @Required
    public void setUpdateTagsQuery(final String updateTagsQuery) {
        this.updateTagsQuery = updateTagsQuery;
    }

    @Required
    public void setLoadAllRunsPossiblyActiveInPeriodQuery(final String loadAllRunsPossiblyActiveInPeriodQuery) {
        this.loadAllRunsPossiblyActiveInPeriodQuery = loadAllRunsPossiblyActiveInPeriodQuery;
    }
}
