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

package com.epam.pipeline.manager.pipeline;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.controller.PagedResult;
import com.epam.pipeline.controller.vo.FilterFieldVO;
import com.epam.pipeline.controller.vo.PagingRunFilterExpressionVO;
import com.epam.pipeline.controller.vo.PagingRunFilterVO;
import com.epam.pipeline.controller.vo.PipelineRunFilterVO;
import com.epam.pipeline.controller.vo.PipelineRunServiceUrlVO;
import com.epam.pipeline.controller.vo.configuration.RunConfigurationWithEntitiesVO;
import com.epam.pipeline.dao.filter.FilterRunParameters;
import com.epam.pipeline.entity.cluster.PipelineRunPrice;
import com.epam.pipeline.entity.pipeline.CommitStatus;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.PipelineTask;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.pipeline.RunLog;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.run.PipelineStart;
import com.epam.pipeline.entity.pipeline.run.parameter.RunSid;
import com.epam.pipeline.entity.utils.DefaultSystemParameter;
import com.epam.pipeline.manager.cluster.InstanceOfferManager;
import com.epam.pipeline.manager.filter.FilterManager;
import com.epam.pipeline.manager.filter.WrongFilterException;
import com.epam.pipeline.manager.pipeline.runner.ConfigurationRunner;
import com.epam.pipeline.manager.security.acl.AclFilter;
import com.epam.pipeline.manager.security.acl.AclMask;
import com.epam.pipeline.manager.security.acl.AclMaskList;
import com.epam.pipeline.manager.security.acl.AclMaskPage;
import com.epam.pipeline.manager.utils.UtilsManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.Date;
import java.util.List;

import static com.epam.pipeline.security.acl.AclExpressions.RUN_ID_EXECUTE;
import static com.epam.pipeline.security.acl.AclExpressions.RUN_ID_OWNER;
import static com.epam.pipeline.security.acl.AclExpressions.RUN_ID_READ;
import static com.epam.pipeline.security.acl.AclExpressions.RUN_ID_WRITE;

@Service
public class RunApiService {

    @Autowired
    private ToolApiService toolApiService;

    @Autowired
    private PipelineRunManager runManager;

    @Autowired
    private FilterManager filterManager;

    @Autowired
    private RunLogManager logManager;

    @Autowired
    private InstanceOfferManager offerManager;

    @Autowired
    private MessageHelper messageHelper;

    @Autowired
    private UtilsManager utilsManager;

    @Autowired
    private ConfigurationRunner configurationLauncher;

    @AclMask
    public PipelineRun runCmd(PipelineStart runVO) {
        //check that tool execution is allowed
        Assert.notNull(runVO.getDockerImage(),
                messageHelper.getMessage(MessageConstants.SETTING_IS_NOT_PROVIDED, "docker_image"));
        toolApiService.loadToolForExecution(runVO.getDockerImage());
        return runVO.getUseRunId() == null ? runManager.runCmd(runVO) : runManager.runPod(runVO);
    }

    @PreAuthorize("hasRole('ADMIN') OR "
            + "hasPermission(#runVO.pipelineId, 'com.epam.pipeline.entity.pipeline.Pipeline', 'EXECUTE')")
    @AclMask
    public PipelineRun runPipeline(PipelineStart runVO) {
        return runManager.runPipeline(runVO);
    }

    @PreAuthorize("hasRole('ADMIN') OR "
            + "@grantPermissionManager.hasConfigurationUpdatePermission(#configuration, 'EXECUTE')")
    @AclMaskList
    public List<PipelineRun> runConfiguration(String refreshToken,
                                              RunConfigurationWithEntitiesVO configuration,
                                              String expansionExpression) {
        return configurationLauncher.runConfiguration(refreshToken, configuration, expansionExpression);
    }

    @PreAuthorize("hasRole('ADMIN') OR @grantPermissionManager.runPermission(#runLog.runId, 'EXECUTE')")
    public RunLog saveLog(final RunLog runLog) {
        return logManager.saveLog(runLog);
    }

    @PreAuthorize(RUN_ID_READ)
    public List<RunLog> loadAllLogsByRunId(Long runId) {
        return logManager.loadAllLogsByRunId(runId);
    }

    @PreAuthorize(RUN_ID_READ)
    public PipelineRunPrice getPipelineRunEstimatedPrice(Long runId, Long regionId) {
        return offerManager.getPipelineRunEstimatedPrice(runId, regionId);
    }

    @PreAuthorize(RUN_ID_READ)
    public String downloadLogs(Long runId) {
        return logManager.downloadLogs(runManager.loadPipelineRun(runId));
    }

    @PreAuthorize(RUN_ID_READ)
    @AclMask
    public PipelineRun loadPipelineRun(Long runId) {
        return runManager.loadPipelineRun(runId);
    }

    @PreAuthorize(RUN_ID_READ)
    @AclMask
    public PipelineRun loadPipelineRunWithRestartedRuns(Long runId) {
        return runManager.loadPipelineRunWithRestartedRuns(runId);
    }

    @PreAuthorize(RUN_ID_READ)
    public List<PipelineTask> loadTasksByRunId(Long runId) {
        return logManager.loadTasksByRunId(runId);
    }

    @PreAuthorize(RUN_ID_READ)
    public List<RunLog> loadAllLogsForTask(Long runId, String taskName, String parameters) {
        return logManager.loadAllLogsForTask(runId, taskName, parameters);
    }

    @PreAuthorize("hasRole('ADMIN') OR @grantPermissionManager.runStatusPermission(#runId, #status, 'EXECUTE')")
    @AclMask
    public PipelineRun updatePipelineStatusIfNotFinal(Long runId, TaskStatus status) {
        return runManager.updatePipelineStatusIfNotFinal(runId, status);
    }

    @PreAuthorize(RUN_ID_EXECUTE)
    @AclMask
    public PipelineRun updateRunInstance(Long runId, RunInstance instance) {
        return runManager.updateRunInstance(runId, instance);
    }

    @PreAuthorize(RUN_ID_EXECUTE)
    @AclMask
    public PipelineRun updateServiceUrl(Long runId, PipelineRunServiceUrlVO serviceUrl) {
        return runManager.updateServiceUrl(runId, serviceUrl);
    }

    @PreAuthorize(RUN_ID_EXECUTE)
    @AclMask
    public PipelineRun updatePrettyUrl(Long runId, String url) {
        return runManager.updatePrettyUrl(runId, url);
    }

    @AclFilter
    @AclMaskPage
    public PagedResult<List<PipelineRun>> searchPipelineRuns(PagingRunFilterVO filter, boolean loadStorageLinks) {
        return runManager.searchPipelineRuns(filter, loadStorageLinks);
    }

    @AclFilter
    @AclMaskPage
    public PagedResult<List<PipelineRun>> searchPipelineRunsByExpression(PagingRunFilterExpressionVO filter)
            throws WrongFilterException {
        return filterManager.filterRuns(filter);
    }

    public List<FilterFieldVO> getRunSearchQueryKeywords() {
        List<FilterFieldVO> keywords = filterManager.getAvailableFilterKeywords(FilterRunParameters.class);
        // Extended keywords:
        FilterFieldVO parameterKeyword = new FilterFieldVO();
        parameterKeyword.setFieldName("parameter.");
        parameterKeyword.setFieldDescription("Any run parameter");
        keywords.add(parameterKeyword);
        return keywords;
    }

    @AclFilter
    public Integer countPipelineRuns(PipelineRunFilterVO filter) {
        return runManager.countPipelineRuns(filter);
    }

    @PreAuthorize("@grantPermissionManager.isRunOwnerOrAdmin(#runId)")
    public String buildSshUrl(Long runId) {
        return utilsManager.buildSshUrl(runId);
    }

    @PreAuthorize("hasRole('ADMIN') OR (@grantPermissionManager.runPermission(#runId, 'EXECUTE')"
            + " AND @grantPermissionManager.commitPermission(#registryId, #imageName, 'WRITE'))")
    @AclMask
    public PipelineRun commitRun(Long runId, Long registryId, String imageName, boolean deleteFiles,
                                 boolean stopPipeline, boolean checkSize) {
        return runManager.commitRun(runId, registryId, imageName, deleteFiles, stopPipeline, checkSize);
    }

    @PreAuthorize(RUN_ID_WRITE)
    @AclMask
    public PipelineRun updateCommitRunStatus(Long runId, CommitStatus commitStatus) {
        return runManager.updateCommitRunStatus(runId, commitStatus);
    }

    public List<DefaultSystemParameter> getSystemParameters() {
        return utilsManager.getSystemParameters();
    }

    @PreAuthorize("hasRole('ADMIN') OR @grantPermissionManager.runPermission(#runId, 'EXECUTE')")
    @AclMask
    public PipelineRun pauseRun(Long runId, boolean checkSize) {
        return runManager.pauseRun(runId, checkSize);
    }

    @PreAuthorize("hasRole('ADMIN') OR @grantPermissionManager.runPermission(#runId, 'EXECUTE')")
    @AclMask
    public PipelineRun resumeRun(Long runId) {
        return runManager.resumeRun(runId);
    }

    public PagedResult<List<PipelineRun>> loadActiveServices(PagingRunFilterVO filterVO) {
        return runManager.loadActiveServices(filterVO);
    }

    @PreAuthorize(RUN_ID_EXECUTE)
    @AclMask
    public PipelineRun updateRunSids(Long runId, List<RunSid> runSids) {
        return runManager.updateRunSids(runId, runSids);
    }

    @PreAuthorize(RUN_ID_EXECUTE)
    @AclMask
    public void prolongIdleRun(Long runId) {
        runManager.prolongIdleRun(runId);
    }

    @PreAuthorize(RUN_ID_READ)
    public Boolean checkFreeSpaceAvailable(final Long runId) {
        return runManager.checkFreeSpaceAvailable(runId);
    }

    @PreAuthorize(RUN_ID_OWNER)
    public PipelineRun terminateRun(final Long runId) {
        return runManager.terminateRun(runId);
    }
}
