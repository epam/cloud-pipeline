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

package com.epam.pipeline.acl.run;

import com.epam.pipeline.aspect.run.QuotaLaunchCheck;
import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.controller.PagedResult;
import com.epam.pipeline.controller.vo.FilterFieldVO;
import com.epam.pipeline.controller.vo.PagingRunFilterExpressionVO;
import com.epam.pipeline.controller.vo.PagingRunFilterVO;
import com.epam.pipeline.controller.vo.PipelineRunFilterVO;
import com.epam.pipeline.controller.vo.PipelineRunServiceUrlVO;
import com.epam.pipeline.controller.vo.TagsVO;
import com.epam.pipeline.controller.vo.configuration.RunConfigurationWithEntitiesVO;
import com.epam.pipeline.dao.filter.FilterRunParameters;
import com.epam.pipeline.entity.cluster.PipelineRunPrice;
import com.epam.pipeline.entity.cluster.ServiceDescription;
import com.epam.pipeline.entity.pipeline.CommitStatus;
import com.epam.pipeline.entity.pipeline.DiskAttachRequest;
import com.epam.pipeline.entity.pipeline.KubernetesService;
import com.epam.pipeline.entity.pipeline.KubernetesServicePort;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.PipelineRunWithTool;
import com.epam.pipeline.entity.pipeline.PipelineTask;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.pipeline.RunLog;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.run.PipeRunCmdStartVO;
import com.epam.pipeline.entity.pipeline.run.PipelineStart;
import com.epam.pipeline.entity.pipeline.run.RunInfo;
import com.epam.pipeline.entity.pipeline.run.parameter.RunSid;
import com.epam.pipeline.entity.utils.DefaultSystemParameter;
import com.epam.pipeline.manager.cluster.EdgeServiceManager;
import com.epam.pipeline.manager.cluster.InstanceOfferManager;
import com.epam.pipeline.manager.filter.FilterManager;
import com.epam.pipeline.manager.filter.WrongFilterException;
import com.epam.pipeline.manager.pipeline.PipelineRunAsManager;
import com.epam.pipeline.manager.pipeline.PipelineRunCRUDService;
import com.epam.pipeline.manager.pipeline.PipelineRunDockerOperationManager;
import com.epam.pipeline.manager.pipeline.PipelineRunKubernetesManager;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.pipeline.RunLogManager;
import com.epam.pipeline.manager.pipeline.runner.ConfigurationRunner;
import com.epam.pipeline.manager.security.acl.AclFilter;
import com.epam.pipeline.manager.security.acl.AclMask;
import com.epam.pipeline.manager.security.acl.AclMaskList;
import com.epam.pipeline.manager.security.acl.AclMaskPage;
import com.epam.pipeline.manager.security.run.RunPermissionManager;
import com.epam.pipeline.manager.utils.UtilsManager;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.epam.pipeline.security.acl.AclExpressions.ADMIN_ONLY;
import static com.epam.pipeline.security.acl.AclExpressions.ADMIN_OR_GENERAL_USER;
import static com.epam.pipeline.security.acl.AclExpressions.RUN_ID_EXECUTE;
import static com.epam.pipeline.security.acl.AclExpressions.RUN_ID_OWNER;
import static com.epam.pipeline.security.acl.AclExpressions.RUN_ID_READ;
import static com.epam.pipeline.security.acl.AclExpressions.RUN_ID_SSH;
import static com.epam.pipeline.security.acl.AclExpressions.RUN_ID_WRITE;

@Service
@RequiredArgsConstructor
public class RunApiService {

    private final PipelineRunManager runManager;
    private final PipelineRunCRUDService runCRUDService;
    private final FilterManager filterManager;
    private final RunLogManager logManager;
    private final InstanceOfferManager offerManager;
    private final MessageHelper messageHelper;
    private final UtilsManager utilsManager;
    private final ConfigurationRunner configurationLauncher;
    private final PipelineRunDockerOperationManager pipelineRunDockerOperationManager;
    private final PipelineRunKubernetesManager pipelineRunKubernetesManager;
    private final PipelineRunAsManager pipelineRunAsManager;
    private final RunPermissionManager runPermissionManager;
    private final EdgeServiceManager edgeServiceManager;

    @AclMask
    @QuotaLaunchCheck
    public PipelineRun runCmd(PipelineStart runVO) {
        Assert.notNull(runVO.getDockerImage(),
                messageHelper.getMessage(MessageConstants.SETTING_IS_NOT_PROVIDED, "docker_image"));
        runPermissionManager.checkToolRunPermission(runVO.getDockerImage());

        if (Objects.nonNull(runVO.getUseRunId())) {
            return runManager.runPod(runVO);
        }

        final String runAsUserName = pipelineRunAsManager.getRunAsUserName(runVO);
        if (StringUtils.isEmpty(runAsUserName)) {
            return runManager.runCmd(runVO);
        }

        runPermissionManager.checkToolRunPermissionToRunAs(runVO.getDockerImage(), runAsUserName);
        return pipelineRunAsManager.runTool(runVO);
    }

    @PreAuthorize("(hasRole('ADMIN') OR "
            + "hasPermission(#runVO.pipelineId, 'com.epam.pipeline.entity.pipeline.Pipeline', 'EXECUTE'))"
            + " AND @grantPermissionManager.hasPipelinePermissionToRunAs(#runVO, 'EXECUTE')")
    @AclMask
    @QuotaLaunchCheck
    public PipelineRun runPipeline(final PipelineStart runVO) {
        return pipelineRunAsManager.runAsAnotherUser(runVO)
                ? pipelineRunAsManager.runPipeline(runVO)
                : runManager.runPipeline(runVO);
    }

    @PreAuthorize("hasRole('ADMIN') OR "
            + "@grantPermissionManager.hasConfigurationUpdatePermission(#configuration, 'EXECUTE')")
    @AclMaskList
    @QuotaLaunchCheck
    public List<PipelineRun> runConfiguration(String refreshToken,
                                              RunConfigurationWithEntitiesVO configuration,
                                              String expansionExpression) {
        return configurationLauncher.runConfiguration(refreshToken, configuration, expansionExpression);
    }

    @PreAuthorize("hasRole('ADMIN') OR @runPermissionManager.runPermission(#runLog.runId, 'EXECUTE')")
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
        return logManager.downloadLogs(runCRUDService.loadRunById(runId));
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

    @PreAuthorize("hasRole('ADMIN') OR @runPermissionManager.runStatusPermission(#runId, #status, 'EXECUTE')")
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
    public PipelineRun updateServiceUrl(final Long runId, final String region,
                                        final PipelineRunServiceUrlVO serviceUrl) {
        return runManager.updateServiceUrl(runId, region, serviceUrl);
    }

    @PreAuthorize(RUN_ID_EXECUTE)
    @AclMask
    public PipelineRun updatePrettyUrl(Long runId, String url) {
        return runManager.updatePrettyUrl(runId, url);
    }

    @PostAuthorize("hasRole('ADMIN') OR @runPermissionManager.runPermission(#returnObject.id, 'READ')")
    @AclMask
    public PipelineRun getRunByPrettyUrl(final String url) {
        return runManager.loadRunByPrettyUrl(url);
    }

    @PreAuthorize(RUN_ID_EXECUTE)
    @AclMask
    public PipelineRun updateTags(final Long runId, final TagsVO tagsVO, final boolean overwrite) {
        return runManager.updateTags(runId, tagsVO, overwrite);
    }

    @PreAuthorize(ADMIN_ONLY)
    @AclMask
    public List<PipelineRun> loadRunsActivityStats(final LocalDateTime start, final LocalDateTime end) {
        return runManager.loadRunsActivityStats(start, end);
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

    @PreAuthorize(RUN_ID_SSH)
    public Map<String, String> buildSshUrl(final Long runId) {
        return edgeServiceManager.buildSshUrl(runId);
    }

    @PreAuthorize(RUN_ID_SSH)
    public Map<String, String> buildFSBrowserUrl(final Long runId) {
        return edgeServiceManager.buildFSBrowserUrl(runId);
    }

    @PreAuthorize("hasRole('ADMIN') OR (@runPermissionManager.runPermission(#runId, 'EXECUTE')"
            + " AND @runPermissionManager.commitPermission(#registryId, #imageName, 'WRITE'))")
    @AclMask
    public PipelineRun commitRun(Long runId, Long registryId, String imageName, boolean deleteFiles,
                                 boolean stopPipeline, boolean checkSize) {
        return pipelineRunDockerOperationManager
                .commitRun(runId, registryId, imageName, deleteFiles, stopPipeline, checkSize);
    }

    @PreAuthorize("@runPermissionManager.runPermission(#runId, 'EXECUTE')")
    @AclMask
    public Long getContainerLayersCount(final Long runId) {
        return pipelineRunDockerOperationManager.getContainerLayers(runId);
    }

    @PreAuthorize(RUN_ID_WRITE)
    @AclMask
    public PipelineRun updateCommitRunStatus(Long runId, CommitStatus commitStatus) {
        return runManager.updateCommitRunStatus(runId, commitStatus);
    }

    public List<DefaultSystemParameter> getSystemParameters() {
        return utilsManager.getSystemParameters();
    }

    @PreAuthorize(RUN_ID_EXECUTE)
    @AclMask
    public PipelineRun pauseRun(Long runId, boolean checkSize) {
        return pipelineRunDockerOperationManager.pauseRun(runId, checkSize);
    }

    @QuotaLaunchCheck
    @PreAuthorize(RUN_ID_EXECUTE)
    @AclMask
    public PipelineRun resumeRun(Long runId) {
        return pipelineRunDockerOperationManager.resumeRun(runId);
    }

    @AclMaskPage
    public PagedResult<List<PipelineRun>> loadActiveSharedRuns(PagingRunFilterVO filterVO) {
        return runManager.loadActiveSharedRuns(filterVO);
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
        return pipelineRunDockerOperationManager.checkFreeSpaceAvailable(runId);
    }

    @PreAuthorize(RUN_ID_OWNER)
    @AclMask
    public PipelineRun terminateRun(final Long runId) {
        return runManager.terminateRun(runId);
    }

    @PreAuthorize(RUN_ID_OWNER)
    @AclMask
    public PipelineRun attachDisk(final Long runId, final DiskAttachRequest request) {
        return runManager.attachDisk(runId, request);
    }

    @PreAuthorize("hasRole('ADMIN') OR @grantPermissionManager.hasPermissionToRun(#runVO.pipelineStart, 'EXECUTE')")
    public String generateLaunchCommand(final PipeRunCmdStartVO runVO) {
        return runManager.generateLaunchCommand(runVO);
    }

    @PreAuthorize(ADMIN_ONLY)
    public List<PipelineRunWithTool> getRunsWithTools(final List<Long> runIds) {
        return runManager.loadRunsWithTools(runIds);
    }

    @PreAuthorize(RUN_ID_EXECUTE)
    public KubernetesService createKubernetesService(final String serviceName, final Long runId,
                                                     final List<KubernetesServicePort> ports) {
        return pipelineRunKubernetesManager.createKubernetesService(serviceName, runId, ports);
    }

    @PreAuthorize(RUN_ID_EXECUTE)
    public KubernetesService getKubernetesService(final Long runId) {
        return pipelineRunKubernetesManager.getKubernetesService(runId);
    }

    @PreAuthorize(ADMIN_OR_GENERAL_USER)
    public List<ServiceDescription> loadEdgeServices() {
        return edgeServiceManager.getEdgeServices();
    }

    @PreAuthorize(ADMIN_ONLY)
    public List<PipelineRun> loadRunsByPoolId(final Long poolId) {
        return runManager.loadRunsByPoolId(poolId);
    }

    @PreAuthorize(RUN_ID_READ)
    public List<RunInfo> loadRunsByParentId(final Long runId) {
        return runManager.loadRunsByParentId(runId);
    }
}
