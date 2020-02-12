/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import com.epam.pipeline.controller.vo.PagingRunFilterVO;
import com.epam.pipeline.controller.vo.PipelineRunFilterVO;
import com.epam.pipeline.controller.vo.PipelineRunServiceUrlVO;
import com.epam.pipeline.dao.pipeline.PipelineRunDao;
import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.cluster.InstancePrice;
import com.epam.pipeline.entity.cluster.PriceType;
import com.epam.pipeline.entity.configuration.ExecutionEnvironment;
import com.epam.pipeline.entity.configuration.PipeConfValueVO;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.contextual.ContextualPreferenceExternalResource;
import com.epam.pipeline.entity.contextual.ContextualPreferenceLevel;
import com.epam.pipeline.entity.pipeline.CommitStatus;
import com.epam.pipeline.entity.pipeline.DiskAttachRequest;
import com.epam.pipeline.entity.pipeline.DockerRegistry;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.pipeline.run.ExecutionPreferences;
import com.epam.pipeline.entity.pipeline.run.PipeRunCmdStartVO;
import com.epam.pipeline.entity.pipeline.run.PipelineStart;
import com.epam.pipeline.entity.pipeline.run.RestartRun;
import com.epam.pipeline.entity.pipeline.run.RunStatus;
import com.epam.pipeline.entity.pipeline.run.parameter.PipelineRunParameter;
import com.epam.pipeline.entity.pipeline.run.parameter.RunSid;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.exception.git.GitClientException;
import com.epam.pipeline.manager.cluster.InstanceOfferManager;
import com.epam.pipeline.manager.cluster.NodesManager;
import com.epam.pipeline.manager.cluster.performancemonitoring.CAdvisorMonitoringManager;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import com.epam.pipeline.manager.docker.DockerContainerOperationManager;
import com.epam.pipeline.manager.docker.DockerRegistryManager;
import com.epam.pipeline.manager.docker.scan.ToolSecurityPolicyCheck;
import com.epam.pipeline.manager.execution.PipelineLauncher;
import com.epam.pipeline.manager.git.GitManager;
import com.epam.pipeline.manager.pipeline.runner.ConfigurationProviderManager;
import com.epam.pipeline.manager.pipeline.runner.PipeRunCmdBuilder;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.region.CloudRegionManager;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.security.run.RunPermissionManager;
import com.epam.pipeline.utils.PasswordGenerator;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.epam.pipeline.entity.configuration.RunConfigurationUtils.getNodeCount;

@Service
public class PipelineRunManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(PipelineRunManager.class);

    private static final int POD_ID_LENGTH = 63;
    private static final String EMPTY_STING = "";
    private static final Long EMPTY_PIPELINE_ID = null;
    private static final int MILLS_IN_SEC = 1000;
    private static final int DIVIDER_TO_GB = 1024 * 1024 * 1024;

    @Autowired
    private PipelineRunDao pipelineRunDao;

    @Autowired
    private DockerContainerOperationManager dockerContainerOperationManager;

    @Autowired
    private DockerRegistryManager dockerRegistryManager;

    @Autowired
    private PipelineManager pipelineManager;

    @Autowired
    private PipelineVersionManager versionManager;

    @Autowired
    private MessageHelper messageHelper;

    @Autowired
    private PipelineLauncher pipelineLauncher;

    @Autowired
    private GitManager gitManager;

    @Autowired
    private DataStorageManager dataStorageManager;

    @Autowired
    private InstanceOfferManager instanceOfferManager;

    @Autowired
    private AuthManager securityManager;

    @Autowired
    private ToolManager toolManager;

    @Autowired
    private RunPermissionManager permissionManager;

    @Autowired
    private PipelineConfigurationManager configurationManager;

    @Autowired
    private ToolApiService toolApiService;

    @Autowired
    private FolderApiService folderApiService;

    @Autowired
    private PreferenceManager preferenceManager;

    @Autowired
    private CloudRegionManager cloudRegionManager;

    @Autowired
    private AuthManager authManager;

    @Autowired
    private ConfigurationProviderManager configurationProviderManager;

    @Autowired
    private RestartRunManager restartRunManager;

    @Autowired
    private RunStatusManager runStatusManager;

    @Autowired
    private CAdvisorMonitoringManager cAdvisorMonitoringManager;

    @Autowired
    private NodesManager nodesManager;

    /**
     * Launches cmd command execution, uses Tool as ACL identity
     * @param runVO
     * @return
     */
    @ToolSecurityPolicyCheck
    public PipelineRun runCmd(PipelineStart runVO) {
        Assert.notNull(runVO.getInstanceType(),
                messageHelper.getMessage(MessageConstants.SETTING_IS_NOT_PROVIDED, "instance_type"));
        Assert.notNull(runVO.getHddSize(),
                messageHelper.getMessage(MessageConstants.SETTING_IS_NOT_PROVIDED, "instance_disk"));

        int maxRunsNumber = preferenceManager.getPreference(SystemPreferences.LAUNCH_MAX_SCHEDULED_NUMBER);

        LOGGER.debug("Allowed runs count - {}, actual - {}", maxRunsNumber, getNodeCount(runVO.getNodeCount(), 1));
        Assert.isTrue(getNodeCount(runVO.getNodeCount(), 1) <= maxRunsNumber, messageHelper.getMessage(
                MessageConstants.ERROR_EXCEED_MAX_RUNS_COUNT, maxRunsNumber, getNodeCount(runVO.getNodeCount(), 1)));

        Tool tool = toolManager.loadByNameOrId(runVO.getDockerImage());
        PipelineConfiguration configuration = configurationManager.getPipelineConfiguration(runVO, tool);
        boolean clusterRun = configurationManager.initClusterConfiguration(configuration, true);

        PipelineRun run = launchPipeline(configuration, null, null,
                runVO.getInstanceType(), runVO.getParentNodeId(), runVO.getConfigurationName(), null,
                runVO.getParentRunId(), null, null, runVO.getRunSids());
        run.setParent(tool);
        run.setAclClass(AclClass.TOOL);

        if (clusterRun) {
            runClusterWorkers(run, runVO, null, null, configuration);
        }
        return run;
    }

    /**
     * Creates a new pod with a given run_id, doesn't create a new pipeline run
     * @param runVO
     * @return
     */
    //TODO: refactoring
    @ToolSecurityPolicyCheck
    @Transactional(propagation = Propagation.REQUIRED)
    public PipelineRun runPod(PipelineStart runVO) {
        Assert.notNull(runVO.getCmdTemplate(),
                messageHelper.getMessage(MessageConstants.SETTING_IS_NOT_PROVIDED, "cmd_template"));
        PipelineRun parentRun = loadPipelineRun(runVO.getUseRunId());
        Assert.state(parentRun.getStatus() == TaskStatus.RUNNING,
                messageHelper.getMessage(MessageConstants.ERROR_PIPELINE_RUN_NOT_RUNNING, runVO.getUseRunId()));
        PipelineConfiguration configuration = configurationManager.getPipelineConfiguration(runVO);
        Tool tool = getToolForRun(configuration);
        configuration.setSecretName(tool.getSecretName());
        List<String> endpoints = tool.getEndpoints();
        PipelineRun run = new PipelineRun();
        run.setInstance(parentRun.getInstance());
        run.setId(runVO.getUseRunId());
        run.setStartDate(DateUtils.now());
        run.setStatus(TaskStatus.RUNNING);
        run.setPipelineName(PipelineRun.DEFAULT_PIPELINE_NAME);
        run.setPodId(getRootPodIDFromTool(tool.getImage(), run.getId()));
        run.setDockerImage(configuration.getDockerImage());
        run.setCmdTemplate(determinateCmdTemplateForRun(configuration));
        run.setTimeout(runVO.getTimeout());
        run.setCommitStatus(CommitStatus.NOT_COMMITTED);
        run.setLastChangeCommitTime(DateUtils.now());
        run.setRunSids(runVO.getRunSids());
        run.setOwner(parentRun.getOwner());
        String launchedCommand = pipelineLauncher.launch(run, configuration,
                endpoints, runVO.getUseRunId().toString(), false, parentRun.getPodId(), null);
        run.setActualCmd(launchedCommand);
        return run;
    }

    /**
     * Runs specified pipeline version, uses Pipeline as ACL identity
     *
     * @param runVO
     * @return
     */
    @ToolSecurityPolicyCheck
    public PipelineRun runPipeline(PipelineStart runVO) {
        Long pipelineId = runVO.getPipelineId();
        String version = runVO.getVersion();
        int maxRunsNumber = preferenceManager.getPreference(SystemPreferences.LAUNCH_MAX_SCHEDULED_NUMBER);

        LOGGER.debug("Allowed runs count - {}, actual - {}", maxRunsNumber, getNodeCount(runVO.getNodeCount(), 1));
        Assert.isTrue(getNodeCount(runVO.getNodeCount(), 1) <= maxRunsNumber, messageHelper.getMessage(
                MessageConstants.ERROR_EXCEED_MAX_RUNS_COUNT, maxRunsNumber, getNodeCount(runVO.getNodeCount(), 1)));

        Pipeline pipeline = pipelineManager.load(pipelineId);
        PipelineConfiguration configuration = configurationManager.getPipelineConfiguration(runVO);
        boolean isClusterRun = configurationManager.initClusterConfiguration(configuration, true);

        //check that tool execution is allowed
        toolApiService.loadToolForExecution(configuration.getDockerImage());
        PipelineRun run = launchPipeline(configuration, pipeline, version,
                runVO.getInstanceType(), runVO.getParentNodeId(), runVO.getConfigurationName(), null,
                runVO.getParentRunId(), null, null, runVO.getRunSids());
        run.setParent(pipeline);

        if (isClusterRun) {
            runClusterWorkers(run, runVO, version, pipeline, configuration);
        }
        return run;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void prolongIdleRun(Long runId) {
        PipelineRun run = loadPipelineRun(runId);
        run.setLastIdleNotificationTime(null);
        run.setProlongedAtTime(DateUtils.nowUTC());
        updateProlongIdleRunAndLastIdleNotificationTime(run);
    }


    /**
     * Internal method for creating a pipeline run,
     * it assumes that ACL filtering was already applied to input arguments
     * @param configuration
     * @param pipeline - null in case of CMD launch
     * @param version - null in case of CMD launch
     * @param instanceType
     * @param configurationName
     * @param runSids - a list of identities (user names or groups) that have access to run
     * @return
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public PipelineRun launchPipeline(PipelineConfiguration configuration, Pipeline pipeline, String version,
            String instanceType, Long parentNodeId, String configurationName, String clusterId,
            Long parentRunId, List<Long> entityIds, Long configurationId, List<RunSid> runSids) {
        Optional<PipelineRun> parentRun = resolveParentRun(parentRunId, configuration);
        AbstractCloudRegion region = resolveCloudRegion(parentRun.orElse(null), configuration);
        validateInstanceAndPriceTypes(configuration, pipeline, region, instanceType);
        String instanceDisk = configuration.getInstanceDisk();
        if (StringUtils.hasText(instanceDisk)) {
            Assert.isTrue(NumberUtils.isNumber(instanceDisk) &&
                Integer.parseInt(instanceDisk) > 0,
                    messageHelper.getMessage(MessageConstants.ERROR_INSTANCE_DISK_IS_INVALID, instanceDisk));
        }
        Tool tool = getToolForRun(configuration);

        adjustInstanceDisk(configuration);

        List<String> endpoints = configuration.isEraseRunEndpoints() ? Collections.emptyList() : tool.getEndpoints();
        configuration.setSecretName(tool.getSecretName());
        PipelineRun run = createPipelineRun(version, configuration, pipeline, region, parentRun.orElse(null), entityIds,
                configurationId);
        if (parentNodeId != null && !parentNodeId.equals(run.getId())) {
            setParentInstance(run, parentNodeId);
        }
        String useNodeLabel = parentNodeId != null ? parentNodeId.toString() : run.getId().toString();
        run.setConfigName(configurationName);
        run.setRunSids(runSids);
        String launchedCommand = pipelineLauncher.launch(run, configuration, endpoints, useNodeLabel, clusterId);
        //update instance info according to evaluated command
        run.setActualCmd(launchedCommand);
        save(run);
        dataStorageManager.analyzePipelineRunsParameters(Collections.singletonList(run));
        return run;
    }

    private AbstractCloudRegion resolveCloudRegion(final PipelineRun parentRun,
                                                   final PipelineConfiguration configuration) {
        final Optional<Long> configurationRegionId = Optional.ofNullable(configuration)
                .map(PipelineConfiguration::getCloudRegionId);
        final Optional<Long> parentRunRegionId = Optional.ofNullable(parentRun)
                .map(PipelineRun::getInstance)
                .map(RunInstance::getCloudRegionId);
        return configurationRegionId.map(Optional::of)
                .orElse(parentRunRegionId)
                .map(cloudRegionManager::load)
                .orElseGet(cloudRegionManager::loadDefaultRegion);
    }

    private void validateInstanceAndPriceTypes(final PipelineConfiguration configuration,
                                               final Pipeline pipeline,
                                               final AbstractCloudRegion region,
                                               final String instanceType) {
        final PriceType priceType = configuration.getIsSpot() != null && configuration.getIsSpot()
                ? PriceType.SPOT
                : PriceType.ON_DEMAND;
        if (pipeline != null) {
            validatePipelineInstanceAndPriceTypes(instanceType, priceType, region.getId());
        } else {
            validateToolInstanceAndPriceTypes(instanceType, priceType,  region.getId(), configuration.getDockerImage());
        }
    }

    private void validatePipelineInstanceAndPriceTypes(final String instanceType,
                                                       final PriceType priceType,
                                                       final Long regionId) {
        Assert.isTrue(!StringUtils.hasText(instanceType)
                        || instanceOfferManager.isInstanceAllowed(instanceType, regionId),
                messageHelper.getMessage(MessageConstants.ERROR_INSTANCE_TYPE_IS_NOT_ALLOWED, instanceType));
        Assert.isTrue(instanceOfferManager.isPriceTypeAllowed(priceType.getLiteral(), null),
                messageHelper.getMessage(MessageConstants.ERROR_PRICE_TYPE_IS_NOT_ALLOWED, priceType));
    }

    private void validateToolInstanceAndPriceTypes(final String instanceType,
                                                   final PriceType priceType,
                                                   final Long regionId,
                                                   final String dockerImage) {
        final Tool tool = toolManager.loadByNameOrId(dockerImage);
        final ContextualPreferenceExternalResource toolResource =
                new ContextualPreferenceExternalResource(ContextualPreferenceLevel.TOOL, tool.getId().toString());
        Assert.isTrue(!StringUtils.hasText(instanceType)
                        || instanceOfferManager.isToolInstanceAllowed(instanceType, toolResource, regionId),
                messageHelper.getMessage(MessageConstants.ERROR_INSTANCE_TYPE_IS_NOT_ALLOWED, instanceType));
        Assert.isTrue(instanceOfferManager.isPriceTypeAllowed(priceType.getLiteral(), toolResource),
                messageHelper.getMessage(MessageConstants.ERROR_PRICE_TYPE_IS_NOT_ALLOWED, priceType));
    }

    private Optional<PipelineRun> resolveParentRun(final Long parentRunId, final PipelineConfiguration configuration) {
        return resolveParentRunId(parentRunId, configuration).map(this::loadPipelineRun);
    }

    private Optional<Long> resolveParentRunId(final Long parentRunId, final PipelineConfiguration configuration) {
        return Optional.ofNullable(parentRunId).map(Optional::of)
                .orElseGet(() -> resolveParentRunIdFromConfiguration(configuration));
    }

    private Optional<Long> resolveParentRunIdFromConfiguration(final PipelineConfiguration configuration) {
        return Optional.ofNullable(configuration)
                .map(PipelineConfiguration::getParameters)
                .filter(MapUtils::isNotEmpty)
                .map(map -> map.get(PipelineRun.PARENT_ID_PARAM))
                .map(PipeConfValueVO::getValue)
                .filter(NumberUtils::isDigits)
                .map(Long::parseLong);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public PipelineRun loadPipelineRun(Long id) {
        PipelineRun pipelineRun = pipelineRunDao.loadPipelineRun(id);
        Assert.notNull(pipelineRun,
                messageHelper.getMessage(MessageConstants.ERROR_PIPELINE_NOT_FOUND, id));
        setParent(pipelineRun);
        checkCommitRunStatus(pipelineRun);
        if (permissionManager.isRunSshAllowed(pipelineRun)) {
            pipelineRun.setSshPassword(pipelineRunDao.loadSshPassword(id));
        }
        pipelineRun.setPipelineRunParameters(
                replaceParametersWithEnvVars(pipelineRun.getPipelineRunParameters(), pipelineRun.getEnvVars()));
        dataStorageManager.analyzePipelineRunsParameters(Collections.singletonList(pipelineRun));
        return pipelineRun;
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    public AbstractSecuredEntity loadRunParent(Long runId) {
        PipelineRun run = loadPipelineRun(runId);
        return loadRunParent(run);
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    public AbstractSecuredEntity loadRunParent(PipelineRun run) {
        if (run.getPipelineId() != null && run.getPipelineId() != 0) {
            return pipelineManager.load(run.getPipelineId());
        } else {
            return null;
        }
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    public List<PipelineRun> loadPipelineRuns(List<Long> ids) {
        List<PipelineRun> pipelineRuns = pipelineRunDao.loadPipelineRuns(ids);
        Assert.notNull(pipelineRuns,
                messageHelper.getMessage(MessageConstants.ERROR_RUN_PIPELINES_NOT_FOUND,
                        ids.stream().map(Object::toString).collect(Collectors.joining(", "))));
        return pipelineRuns;
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    public PipelineConfiguration loadRunConfiguration(Long id) throws GitClientException {
        PipelineRun pipelineRun = loadPipelineRun(id);
        return versionManager
                .loadParametersFromScript(pipelineRun.getPipelineId(),
                        pipelineRun.getVersion(), pipelineRun.getConfigName());
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public PipelineRun updateRunInstance(Long id, RunInstance instance) {
        PipelineRun pipelineRun = pipelineRunDao.loadPipelineRun(id);
        if (!instance.isEmpty()) {
            pipelineRun.setInstance(instance);
            pipelineRunDao.updateRunInstance(pipelineRun);
        }
        return pipelineRun;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public PipelineRun updatePipelineStatusIfNotFinalExternal(Long runId, TaskStatus status) {
        return updatePipelineStatusIfNotFinal(runId, status);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public PipelineRun updatePipelineStatusIfNotFinal(Long runId, TaskStatus status) {
        PipelineRun pipelineRun = pipelineRunDao.loadPipelineRun(runId);
        if (pipelineRun.getStatus().isFinal()) {
            LOGGER.debug("Pipeline run {} is already in the final status: {}.",
                    pipelineRun.getId(), pipelineRun.getStatus());
            return pipelineRun;
        }
        if (pipelineRun.getExecutionPreferences().getEnvironment() == ExecutionEnvironment.DTS
                && status == TaskStatus.STOPPED) {
            configurationProviderManager.stop(runId, pipelineRun.getExecutionPreferences());
        }
        return updatePipelineStatus(runId, status, pipelineRun);
    }

    /**
     * A shorthand method to stop a Pipeline Run
     * @param runId ID of a Pipeline Run
     * @return stopped run
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public PipelineRun stop(Long runId) {
        return updatePipelineStatusIfNotFinal(runId, TaskStatus.STOPPED);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public PipelineRun updatePipelineStatus(PipelineRun run) {
        updatePrettyUrlForFinishedRun(run);
        pipelineRunDao.updateRunStatus(run);
        return run;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public PipelineRun updatePipelineCommitStatus(PipelineRun run) {
        pipelineRunDao.updateRunCommitStatus(run);
        return run;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public PipelineRun updatePodIP(PipelineRun run) {
        pipelineRunDao.updatePodIP(run);
        return run;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public PipelineRun updatePipelineRunLastNotification(PipelineRun run) {
        pipelineRunDao.updateRunLastNotification(run);
        return run;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void updatePipelineRunsLastNotification(Collection<PipelineRun> runs) {
        pipelineRunDao.updateRunsLastNotification(runs);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public PipelineRun updateProlongIdleRunAndLastIdleNotificationTime(PipelineRun run) {
        pipelineRunDao.updateProlongIdleRunAndLastIdleNotificationTime(run);
        return run;
    }

    public List<PipelineRun> loadAllRunsByPipeline(Long id) {
        pipelineManager.load(id);
        return pipelineRunDao.loadAllRunsForPipeline(id);
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    public List<PipelineRun> loadAllRunsByPipeline(Long pipelineId, String version) {
        return pipelineRunDao.loadAllRunsForPipeline(pipelineId, version);
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    public List<PipelineRun> loadRunningAndTerminatedPipelineRuns() {
        return pipelineRunDao.loadRunningAndTerminatedPipelineRuns();
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    public List<PipelineRun> loadRunningPipelineRuns() {
        return pipelineRunDao.loadRunningPipelineRuns();
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public PipelineRun loadPipelineRunWithRestartedRuns(Long id) {
        PipelineRun run = loadPipelineRun(id);
        List<RestartRun> restartedRuns = restartRunManager.loadRestartedRunsForInitialRun(id);
        run.setRestartedRuns(restartedRuns);
        run.setRunStatuses(runStatusManager.loadRunStatus(id));
        return run;
    }

    /**
     * Method that will return all active runs for which current users is owner or is listed in run sids - a list of
     * identities (user names or groups) that have access to run
     * @param filter - filter containing a page and page size
     * @return list of active runs which available for current user
     */
    @Transactional(propagation = Propagation.SUPPORTS)
    public PagedResult<List<PipelineRun>> loadActiveSharedRuns(PagingRunFilterVO filter) {
        Assert.isTrue(filter.getPage() > 0,
                messageHelper.getMessage(MessageConstants.ERROR_PAGE_INDEX));
        Assert.isTrue(filter.getPageSize() > 0,
                messageHelper.getMessage(MessageConstants.ERROR_PAGE_SIZE));
        PipelineUser user = authManager.getCurrentUser();
        List<PipelineRun> runs = pipelineRunDao.loadActiveSharedRuns(filter, user);
        int count = pipelineRunDao.countActiveSharedRuns(user);
        return new PagedResult<>(runs, count);
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    public PagedResult<List<PipelineRun>> searchPipelineRuns(PagingRunFilterVO filter, boolean loadStorageLinks) {
        Assert.isTrue(filter.getPage() > 0,
                messageHelper.getMessage(MessageConstants.ERROR_PAGE_INDEX));
        Assert.isTrue(filter.getPageSize() > 0,
                messageHelper.getMessage(MessageConstants.ERROR_PAGE_SIZE));
        PipelineRunFilterVO.ProjectFilter projectFilter = resolveProjectFiltering(filter);
        if (projectFilter!= null && projectFilter.isEmpty()) {
            return new PagedResult<>(Collections.emptyList(), 0);
        }
        PagedResult<List<PipelineRun>> result;
        if (filter.useGrouping()) {
            result = searchRunsGrouping(filter, projectFilter);
        } else {
            List<PipelineRun> runs = pipelineRunDao.searchPipelineRuns(filter, projectFilter);
            int count = pipelineRunDao.countFilteredPipelineRuns(filter, projectFilter);
            result = new PagedResult<>(runs, count);
        }
        if (loadStorageLinks && CollectionUtils.isNotEmpty(result.getElements())) {
            dataStorageManager.analyzePipelineRunsParameters(result.getElements());
        }
        if (CollectionUtils.isNotEmpty(result.getElements())) {
            Map<Long, List<RunStatus>> runStatuses = runStatusManager.loadRunStatus(result.getElements().stream()
                    .map(PipelineRun::getId).collect(Collectors.toList()));
            result.getElements().forEach(run -> run.setRunStatuses(runStatuses.get(run.getId())));
        }
        return result;
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    public Integer countPipelineRuns(PipelineRunFilterVO filter) {
        PipelineRunFilterVO.ProjectFilter projectFilter = resolveProjectFiltering(filter);
        if (projectFilter!= null && projectFilter.isEmpty()) {
            return 0;
        }
        return filter.useGrouping() ?
                pipelineRunDao.countRootRuns(filter, projectFilter) :
                pipelineRunDao.countFilteredPipelineRuns(filter, projectFilter);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public PipelineRun updateServiceUrl(Long runId, PipelineRunServiceUrlVO serviceUrl) {
        PipelineRun pipelineRun = pipelineRunDao.loadPipelineRun(runId);
        Assert.notNull(pipelineRun,
                messageHelper.getMessage(MessageConstants.ERROR_PIPELINE_NOT_FOUND, runId));
        pipelineRun.setServiceUrl(serviceUrl.getServiceUrl());
        pipelineRunDao.updateServiceUrl(pipelineRun);
        //TODO: check if we need it
        setParent(pipelineRun);
        return pipelineRun;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public PipelineRun updatePrettyUrl(Long runId, String url) {
        PipelineRun pipelineRun = pipelineRunDao.loadPipelineRun(runId);
        Assert.notNull(pipelineRun,
                messageHelper.getMessage(MessageConstants.ERROR_PIPELINE_NOT_FOUND, runId));
        if (Objects.equals(pipelineRun.getPrettyUrl(), url)) {
            LOGGER.debug("Url '{}' is already assigned to run {}", url, runId);
            return pipelineRun;
        }
        validatePrettyUrlFree(url);
        pipelineRun.setPrettyUrl(url);
        pipelineRunDao.updateRun(pipelineRun);
        //TODO: check if we need it
        setParent(pipelineRun);
        return pipelineRun;
    }

    /**
     * Commits docker image and push it to a docker registry from specified run
     * @param id {@link PipelineRun} id for pipeline run to be committed
     * @param registryId {@link DockerRegistry} id where new image will be pushed
     * @param deleteFiles if true files from pipeline working directory will be cleaned
     * @param stopPipeline if true pipeline will be stopped after commit
     * @param checkSize if true method will check if free disk space is enough for commit operation
     * @return  {@link PipelineRun} to be committed
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public PipelineRun commitRun(Long id, Long registryId, String newImageName, boolean deleteFiles,
                                 boolean stopPipeline, boolean checkSize) {
        if (checkSize) {
            Assert.state(checkFreeSpaceAvailable(id),
                    messageHelper.getMessage(MessageConstants.ERROR_INSTANCE_DISK_NOT_ENOUGH));
        }

        PipelineRun pipelineRun = pipelineRunDao.loadPipelineRun(id);
        DockerRegistry dockerRegistry = dockerRegistryManager.load(registryId);
        Assert.notNull(pipelineRun,
                messageHelper.getMessage(MessageConstants.ERROR_RUN_PIPELINES_NOT_FOUND, id));
        Assert.state(pipelineRun.getStatus() == TaskStatus.RUNNING,
                messageHelper.getMessage(MessageConstants.ERROR_PIPELINE_RUN_FINISHED, id));
        Assert.notNull(dockerRegistry,
                messageHelper.getMessage(MessageConstants.ERROR_REGISTRY_NOT_FOUND, registryId));
        String dockerImageFromRun = retrieveImageName(pipelineRun);
        String resolvedImageName = StringUtils.isEmpty(newImageName) ? dockerImageFromRun : newImageName;

        //check that there is no tool with this name in another registry
        toolManager.assertThatToolUniqueAcrossRegistries(resolvedImageName, dockerRegistry.getPath());

        return dockerContainerOperationManager.commitContainer(
                pipelineRun,
                dockerRegistry,
                resolvedImageName,
                deleteFiles,
                stopPipeline
        );
    }

    /**
     * Commits docker image and push it to a docker registry from specified run
     * @param id {@link PipelineRun} id for pipeline run which commit status should be updated
     * @param commitStatus new {@link CommitStatus} of the pipeline run
     * @return updated {@link PipelineRun}
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public PipelineRun updateCommitRunStatus(Long id, CommitStatus commitStatus) {
        PipelineRun pipelineRun = pipelineRunDao.loadPipelineRun(id);
        Assert.notNull(pipelineRun,
                messageHelper.getMessage(MessageConstants.ERROR_PIPELINE_NOT_FOUND, id));
        pipelineRun.setCommitStatus(commitStatus);
        pipelineRun.setLastChangeCommitTime(DateUtils.now());
        return updatePipelineCommitStatus(pipelineRun);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public PipelineRun updatePodStatus(Long id, String status) {
        PipelineRun pipelineRun = pipelineRunDao.loadPipelineRun(id);
        Assert.notNull(pipelineRun,
                messageHelper.getMessage(MessageConstants.ERROR_PIPELINE_NOT_FOUND, id));
        pipelineRun.setPodStatus(status);
        pipelineRunDao.updatePodStatus(pipelineRun);
        return pipelineRun;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public PipelineRun createPipelineRun(String version, PipelineConfiguration configuration, Pipeline pipeline,
                                         AbstractCloudRegion region, PipelineRun parentRun, List<Long> entityIds,
                                         Long configurationId) {
        validateRunParameters(configuration, pipeline);

        RunInstance instance = configureRunInstance(configuration, region);

        PipelineRun run = new PipelineRun();
        Long runId = pipelineRunDao.createRunId();
        run.setId(runId);
        run.setStartDate(DateUtils.now());
        run.setProlongedAtTime(DateUtils.nowUTC());
        if (pipeline == null || version == null) {
            fillMissingPipelineFields(run);
        } else {
            run.setPipelineName(pipeline.getName());
            run.setRepository(pipeline.getRepository());
            run.setPipelineId(pipeline.getId());
            run.setVersion(version);
            run.setRevisionName(gitManager.getRevisionName(version));
        }
        run.setStatus(TaskStatus.RUNNING);
        run.setCommitStatus(CommitStatus.NOT_COMMITTED);
        run.setLastChangeCommitTime(DateUtils.now());
        run.setPodId(getRootPodIDFromPipeline(run));
        Optional.ofNullable(parentRun).map(PipelineRun::getId).ifPresent(run::setParentRunId);
        run.convertParamsToString(configuration.getParameters());
        run.setTimeout(configuration.getTimeout());
        run.setDockerImage(configuration.getDockerImage());
        run.setCmdTemplate(determinateCmdTemplateForRun(configuration));
        run.setNodeCount(configuration.getNodeCount());
        setRunPrice(instance, run);
        run.setSshPassword(PasswordGenerator.generatePassword());
        run.setOwner(securityManager.getAuthorizedUser());
        if (CollectionUtils.isNotEmpty(entityIds)) {
            run.setEntitiesIds(entityIds);
        }
        run.setConfigurationId(configurationId);
        run.setExecutionPreferences(Optional.ofNullable(configuration.getExecutionPreferences())
                .orElse(ExecutionPreferences.getDefault()));
        if (StringUtils.hasText(configuration.getPrettyUrl())) {
            validatePrettyUrlFree(configuration.getPrettyUrl());
            run.setPrettyUrl(configuration.getPrettyUrl());
        }
        if (instance.getSpot() != null &&!instance.getSpot()) {
            run.setNonPause(configuration.isNonPause());
        }
        return run;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public PipelineRun save(PipelineRun pipelineRun) {
        pipelineRunDao.createPipelineRun(pipelineRun);
        return pipelineRun;
    }

    /**
     * Pauses pipeline run for specified {@code runId}.
     * @param runId {@link PipelineRun} id for pipeline run to be paused
     * @param checkSize if true method will check if free disk space is enough for commit operation
     * @return paused {@link PipelineRun}
     */
    public PipelineRun pauseRun(Long runId, boolean checkSize) {
        if (checkSize) {
            Assert.state(checkFreeSpaceAvailable(runId), MessageConstants.ERROR_INSTANCE_DISK_NOT_ENOUGH);
        }
        PipelineRun pipelineRun = pipelineRunDao.loadPipelineRun(runId);
        verifyPipelineRunForPauseResume(pipelineRun, runId);

        Assert.isTrue(pipelineRun.getInitialized(),
                messageHelper.getMessage(MessageConstants.ERROR_PIPELINE_RUN_NOT_INITIALIZED, runId));
        Assert.notNull(pipelineRun.getDockerImage(),
                messageHelper.getMessage(MessageConstants.ERROR_DOCKER_IMAGE_NOT_FOUND, runId));
        Assert.state(pipelineRun.getStatus() == TaskStatus.RUNNING,
                messageHelper.getMessage(MessageConstants.ERROR_PIPELINE_RUN_FINISHED, runId));
        pipelineRun.setStatus(TaskStatus.PAUSING);
        updatePipelineStatus(pipelineRun);
        dockerContainerOperationManager.pauseRun(pipelineRun);
        return pipelineRun;
    }

    /**
     * Resumes pipeline run for specified {@code runId}.
     * @param runId {@link PipelineRun} id for pipeline run to be resumed
     * @return resumed {@link PipelineRun}
     */
    public PipelineRun resumeRun(Long runId) {
        PipelineRun pipelineRun = pipelineRunDao.loadPipelineRun(runId);
        verifyPipelineRunForPauseResume(pipelineRun, runId);
        pipelineRun.setSshPassword(pipelineRunDao.loadSshPassword(runId));
        Assert.state(pipelineRun.getStatus() == TaskStatus.PAUSED,
                messageHelper.getMessage(MessageConstants.ERROR_PIPELINE_RUN_NOT_STOPPED, runId));
        if (StringUtils.isEmpty(pipelineRun.getActualCmd())) {
            throw new IllegalArgumentException(messageHelper.getMessage(
                    MessageConstants.ERROR_ACTUAL_CMD_NOT_FOUND, runId));
        }
        Tool tool = toolManager.loadByNameOrId(pipelineRun.getDockerImage());
        pipelineRun.setStatus(TaskStatus.RESUMING);
        updatePipelineStatus(pipelineRun);
        dockerContainerOperationManager.resumeRun(pipelineRun, tool.getEndpoints());
        return pipelineRun;
    }

    public Tool getToolForRun(PipelineConfiguration configuration) {
        return toolManager.loadByNameOrId(configuration.getDockerImage());
    }

    /**
     * Updates pipeline run sids
     * @param runId {@link PipelineRun} id for pipeline run which run sids should be updated
     * @param runSids - a list of identities (user names or groups) that have access to run
     * @return updated pipeline {@link RunSid}
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public PipelineRun updateRunSids(Long runId, List<RunSid> runSids) {
        PipelineRun pipelineRun = pipelineRunDao.loadPipelineRun(runId);
        Assert.notNull(pipelineRun,
                messageHelper.getMessage(MessageConstants.ERROR_PIPELINE_NOT_FOUND, runId));
        pipelineRunDao.deleteRunSids(runId);
        pipelineRunDao.createRunSids(runId, runSids);
        pipelineRun.setRunSids(runSids);
        return pipelineRun;
    }

    /**
     * Updates run state reason message which was retrieved from instance {@StateReason}
     * @param run {@link PipelineRun} for pipeline run which state reason message should be updated
     * @param stateReasonMessage message that should be updated
     * @return
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public PipelineRun updateStateReasonMessage(PipelineRun run, String stateReasonMessage) {
        run.setStateReasonMessage(stateReasonMessage);
        pipelineRunDao.updateRun(run);
        return run;
    }

    /**
     * Restarts spot run
     * @param run {@link PipelineRun} which will be restart
     * @return Restarted pipeline run
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public PipelineRun restartRun(final PipelineRun run) {
        final PipelineConfiguration configuration = configurationManager.getConfigurationFromRun(run);
        final PipelineRun restartedRun = createRestartRun(run);
        final Tool tool = getToolForRun(configuration);
        final List<String> endpoints = configuration.isEraseRunEndpoints() ?
                Collections.emptyList() : tool.getEndpoints();
        configuration.setSecretName(tool.getSecretName());
        final String launchedCommand = pipelineLauncher.launch(restartedRun, configuration, endpoints,
                restartedRun.getId().toString(), null);
        restartedRun.setActualCmd(launchedCommand);
        save(restartedRun);

        final RestartRun restartRun = new RestartRun();
        restartRun.setParentRunId(run.getId());
        restartRun.setRestartedRunId(restartedRun.getId());
        restartRun.setDate(DateUtils.now());
        restartRunManager.createRestartRun(restartRun);
        return run;
    }

    /**
     * Checks that free node space is enough. Calls before commit/pause operations.
     * @param runId {@link PipelineRun} id for pipeline run
     * @return true if free space is enough
     */
    public Boolean checkFreeSpaceAvailable(final Long runId) {
        final PipelineRun pipelineRun = pipelineRunDao.loadPipelineRun(runId);
        Assert.notNull(pipelineRun,
                messageHelper.getMessage(MessageConstants.ERROR_RUN_PIPELINES_NOT_FOUND, runId));
        final long availableDisk = cAdvisorMonitoringManager.getDiskAvailableForDocker(
                pipelineRun.getInstance().getNodeName(), pipelineRun.getPodId(), pipelineRun.getDockerImage());
        final long requiredImageSize = (long)Math.ceil(
                (double)toolManager.getCurrentImageSize(pipelineRun.getDockerImage())
                        * preferenceManager.getPreference(SystemPreferences.CLUSTER_DOCKER_EXTRA_MULTI) / 2);
        LOGGER.debug("Run {} available disk: {} required for image size: {}", runId, availableDisk, requiredImageSize);
        if (availableDisk < requiredImageSize) {
            LOGGER.warn("Free disk space is not enough");
            return false;
        }
        return true;
    }

    /**
     * Terminates paused run.
     *
     * It terminates the run cloud instance if it exists and stops the run.
     *
     * @param runId {@link PipelineRun} id for pipeline run.
     * @return Terminated pipeline run.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public PipelineRun terminateRun(final Long runId) {
        final PipelineRun pipelineRun = pipelineRunDao.loadPipelineRun(runId);
        Assert.notNull(pipelineRun,
                messageHelper.getMessage(MessageConstants.ERROR_RUN_PIPELINES_NOT_FOUND, runId));
        Assert.state(pipelineRun.getStatus() == TaskStatus.PAUSED,
                messageHelper.getMessage(MessageConstants.ERROR_RUN_TERMINATION_WRONG_STATUS, runId,
                        pipelineRun.getStatus()));
        pipelineRun.setStatus(TaskStatus.STOPPED);
        pipelineRun.setEndDate(DateUtils.now());
        updatePipelineStatus(pipelineRun);
        nodesManager.terminateRun(pipelineRun);
        return pipelineRun;
    }

    /**
     * Creates and attaches new disk by the given request to the run.
     *
     * @param runId {@link PipelineRun} id for pipeline run.
     * @param request Attaching disk request.
     * @return Updated pipeline run.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public PipelineRun attachDisk(final Long runId, final DiskAttachRequest request) {
        final PipelineRun pipelineRun = pipelineRunDao.loadPipelineRun(runId);
        Assert.notNull(pipelineRun,
                messageHelper.getMessage(MessageConstants.ERROR_RUN_PIPELINES_NOT_FOUND, runId));
        Assert.state(pipelineRun.getStatus() == TaskStatus.RUNNING || pipelineRun.getStatus().isPause(),
                messageHelper.getMessage(MessageConstants.ERROR_RUN_DISK_ATTACHING_WRONG_STATUS, runId,
                        pipelineRun.getStatus()));
        Assert.notNull(request.getSize(),
                messageHelper.getMessage(MessageConstants.ERROR_RUN_DISK_SIZE_NOT_FOUND));
        Assert.isTrue(request.getSize() > 0,
                messageHelper.getMessage(MessageConstants.ERROR_INSTANCE_DISK_IS_INVALID, request.getSize()));
        nodesManager.attachDisk(pipelineRun, request);
        return pipelineRun;
    }

    /**
     * Generates launch command for 'pipe run' CLI method.
     *
     * @param runVO {@link PipeRunCmdStartVO} contains input arguments for launch command
     * @return launch command
     */
    public String generateLaunchCommand(final PipeRunCmdStartVO runVO) {
        Assert.notNull(runVO.getPipelineStart(), "Pipeline start must be specified");
        return new PipeRunCmdBuilder(runVO)
                .name()
                .config()
                .runParameters()
                .parameters()
                .yes()
                .instanceDisk()
                .instanceType()
                .dockerImage()
                .cmdTemplate()
                .timeout()
                .quite()
                .instanceCount()
                .sync()
                .priceType()
                .regionId()
                .parentNode()
                .nonPause()
                .build();
    }

    private void adjustInstanceDisk(final PipelineConfiguration configuration) {
        long imageSizeBytes = toolManager.getCurrentImageSize(configuration.getDockerImage());
        long requiredDiskForImageBytes = imageSizeBytes
                * preferenceManager.getPreference(SystemPreferences.CLUSTER_DOCKER_EXTRA_MULTI)
                * preferenceManager.getPreference(SystemPreferences.CLUSTER_INSTANCE_HDD_EXTRA_MULTI);
        long requiredDiskSizeGb = (long)Math.ceil((double)requiredDiskForImageBytes / DIVIDER_TO_GB);
        long requestedDiskSize = NumberUtils.createLong(configuration.getInstanceDisk());
        LOGGER.debug("Retrieved image size: {} b, image size on disk: {} Gb, total disk size: {} Gb",
                imageSizeBytes, requiredDiskSizeGb, requiredDiskSizeGb + requestedDiskSize);
        configuration.setEffectiveDiskSize(Math.toIntExact(requiredDiskSizeGb + requestedDiskSize));
    }

    private PagedResult<List<PipelineRun>> searchRunsGrouping(PagingRunFilterVO filter,
                                                              PipelineRunFilterVO.ProjectFilter projectFilter) {
        List<PipelineRun> groupedRuns = pipelineRunDao.searchPipelineGroups(filter, projectFilter);
        return new PagedResult<>(groupedRuns, pipelineRunDao.countRootRuns(filter, projectFilter));
    }

    private void setParentInstance(PipelineRun run, Long parentNodeId) {
        PipelineRun parentRun = loadPipelineRun(parentNodeId);
        run.setInstance(parentRun.getInstance());
    }

    private void runClusterWorkers(PipelineRun run, PipelineStart runVO, String version, Pipeline pipeline,
                                   PipelineConfiguration configuration) {
        String parentId = Long.toString(run.getId());
        Integer nodeCount = configuration.getNodeCount();
        configurationManager.updateWorkerConfiguration(parentId, runVO, configuration, false, true);
        for (int i = 0; i < nodeCount; i++) {
            launchPipeline(configuration, pipeline, version,
                    runVO.getInstanceType(), runVO.getParentNodeId(),
                    runVO.getConfigurationName(), parentId, run.getId(), null, null, runVO.getRunSids());
        }
    }

    private String determinateCmdTemplateForRun(PipelineConfiguration configuration) {
        if (StringUtils.isEmpty(configuration.getCmdTemplate())) {
            String defaultToolCommand = getToolForRun(configuration).getDefaultCommand();
            Assert.isTrue(!StringUtils.isEmpty(defaultToolCommand),
                    messageHelper.getMessage(MessageConstants.SETTING_IS_NOT_PROVIDED, "cmd_template"));
            return defaultToolCommand;
        } else {
            return configuration.getCmdTemplate();
        }
    }


    private void validateRunParameters(PipelineConfiguration configuration, Pipeline pipeline) {
        for (Map.Entry<String, PipeConfValueVO> param : configuration.getParameters().entrySet()) {
            boolean ifRequiredThenNotEmpty = !param.getValue().isRequired()
                    || !StringUtils.isEmpty(param.getValue().getValue());
            String pipelineName = pipeline == null ? "CMD" : pipeline.getName();
            Assert.isTrue(
                    ifRequiredThenNotEmpty,
                    messageHelper.getMessage(
                            MessageConstants.ERROR_RUN_PARAMETER_IS_REQUIRED,
                            param.getKey(),
                            pipelineName
                    )
            );
        }
    }

    private String getRootPodIDFromPipeline(PipelineRun run) {
        String runId = String.valueOf(run.getId());
        return normalizePodName(run.getPipelineName(), runId);
    }

    private String getRootPodIDFromTool(String image, Long id) {
        String runId = String.valueOf(id);
        int suffixLength = POD_ID_LENGTH - (image.length() + runId.length() + 1);
        String name = image;
        if (suffixLength > 1) {
            String suffix = PasswordGenerator.generateRandomString(suffixLength);
            name = String.format("%s-%s", image, suffix);
        }
        return normalizePodName(name, runId);
    }

    // PodId should contain only a-z 0-9 and -, it also should be smaller then 63 characters
    private String normalizePodName(String name, String runId) {
        String podName = name.trim().toLowerCase();
        podName = podName.replaceAll("[^a-z0-9\\-]+", "-");
        if (podName.length() + runId.length() + 1 > POD_ID_LENGTH) {
            podName = podName.substring(0, POD_ID_LENGTH - runId.length() - 1);
        }
        return String.format("%s-%s", podName, runId);
    }

    private RunInstance configureRunInstance(PipelineConfiguration configuration, AbstractCloudRegion region) {
        RunInstance instance = new RunInstance();
        instance.setNodeDisk(Optional.ofNullable(configuration.getInstanceDisk())
                .map(disk -> Integer.parseInt(configuration.getInstanceDisk()))
                .orElse(null));
        instance.setEffectiveNodeDisk(Optional.ofNullable(configuration.getEffectiveDiskSize())
                .orElse(instance.getNodeDisk()));
        instance.setNodeType(configuration.getInstanceType());
        instance.setNodeImage(configuration.getInstanceImage());
        Optional.ofNullable(region).map(AbstractCloudRegion::getId).ifPresent(instance::setCloudRegionId);
        Optional.ofNullable(region).map(AbstractCloudRegion::getProvider).ifPresent(instance::setCloudProvider);
        boolean defaultUseSpot = preferenceManager.getPreference(SystemPreferences.CLUSTER_SPOT);
        instance.setSpot(Optional.ofNullable(configuration.getIsSpot()).orElse(defaultUseSpot));
        return instance;
    }

    private PipelineRun updatePipelineStatus(Long id, TaskStatus status, PipelineRun pipelineRun) {
        Assert.notNull(pipelineRun,
                messageHelper.getMessage(MessageConstants.ERROR_PIPELINE_NOT_FOUND, id));
        Assert.notNull(status, messageHelper
                .getMessage(MessageConstants.ERROR_PARAMETER_REQUIRED, "status",
                        PipelineRun.class.getSimpleName()));
        Assert.isTrue(status != TaskStatus.RUNNING,
                messageHelper.getMessage(MessageConstants.ERROR_WRONG_RUN_STATUS_UPDATE, status));
        if (status.isFinal()) {
            pipelineRun.setEndDate(DateUtils.now());
        }
        pipelineRun.setStatus(status);
        pipelineRun.setTerminating(status.isFinal());

        dataStorageManager.analyzePipelineRunsParameters(Arrays.asList(pipelineRun));
        updatePrettyUrlForFinishedRun(pipelineRun);
        pipelineRunDao.updateRunStatus(pipelineRun);
        setParent(pipelineRun);
        return pipelineRun;
    }

    private void setParent(PipelineRun run) {
        if (run.getPipelineId() != null && run.getPipelineId() != 0L) {
            run.setParent(pipelineManager.load(run.getPipelineId()));
        }
    }

    private String retrieveImageName(PipelineRun pipelineRun) {
        String[] registryAndDockerImageFromRun = pipelineRun.getDockerImage().split("/");
        return registryAndDockerImageFromRun.length == 1
            ? registryAndDockerImageFromRun[0]
            : registryAndDockerImageFromRun[1];
    }

    private void checkCommitRunStatus(PipelineRun run) {
        Date currentTime = DateUtils.now();
        long secsFromLastChange = (currentTime.getTime() - run.getLastChangeCommitTime().getTime()) / MILLS_IN_SEC;
        int commitTimeout = preferenceManager.getPreference(SystemPreferences.COMMIT_TIMEOUT);
        if (run.getCommitStatus() == CommitStatus.COMMITTING && secsFromLastChange > commitTimeout) {
            LOGGER.warn(messageHelper.getMessage(
                    MessageConstants.WARN_COMMIT_TIMEOUT_HAS_EXPIRED,
                    run.getCommitStatus(),
                    secsFromLastChange,
                    CommitStatus.FAILURE));
            run.setCommitStatus(CommitStatus.FAILURE);
            run.setLastChangeCommitTime(currentTime);
            updatePipelineCommitStatus(run);
        }
    }

    List<PipelineRunParameter> replaceParametersWithEnvVars(List<PipelineRunParameter> params,
                                                            Map<String, String> envVars) {
        if (CollectionUtils.isEmpty(params) || MapUtils.isEmpty(envVars)) {
            return params;
        }
        params.forEach(p -> p.setResolvedValue(p.getValue()));
        envVars.forEach((key, value) -> {
            Pattern pattern = Pattern.compile(String.format("[$]%s[^a-zA-Z0-9_]", key));
            params.forEach(param ->
                    param.setResolvedValue(
                        replaceEnvVarInParameter(pattern, key, value, param.getResolvedValue())));
        });
        return params;
    }

    private String replaceEnvVarInParameter(Pattern pattern, String envVarName,
            String envVarValue, String parameter) {
        if (!StringUtils.hasText(parameter) || !StringUtils.hasText(envVarName)
                || !StringUtils.hasText(envVarValue)) {
            return parameter;
        }
        try {
            String resolvedParameter = parameter;
            Matcher matcher = pattern.matcher(resolvedParameter);
            while (matcher.find()) {
                char lastSymbol = matcher.group().toCharArray()[matcher.group().length() - 1];
                resolvedParameter = matcher.replaceAll(envVarValue + lastSymbol);
            }
            return resolvedParameter.replaceAll(String.format("[$]%s$|[$][{]%s[}]",
                    envVarName, envVarName), envVarValue);
        } catch (IllegalArgumentException e) {
            LOGGER.trace(e.getMessage(), e);
            return parameter;
        }
    }
    
    PipelineRunFilterVO.ProjectFilter resolveProjectFiltering(PipelineRunFilterVO filter) {
        if (CollectionUtils.isEmpty(filter.getProjectIds())) {
            return null;
        }
        List<Long> pipelineIds = new ArrayList<>();
        List<Long> configurationIds = new ArrayList<>();
        filter.getProjectIds().stream()
                .map(id -> folderApiService.load(id))
                .forEach(folder -> collectChildren(folder, pipelineIds, configurationIds));
        return new PipelineRunFilterVO.ProjectFilter(pipelineIds, configurationIds);
    }

    private void collectChildren(Folder folder, List<Long> pipelineIds, List<Long> configurationIds) {
        Map<AclClass, List<Long>> projectChildren = folder.collectChildren();
        if (projectChildren.containsKey(AclClass.PIPELINE)) {
            pipelineIds.addAll(projectChildren.get(AclClass.PIPELINE));
        }
        if (projectChildren.containsKey(AclClass.CONFIGURATION)) {
            configurationIds.addAll(projectChildren.get(AclClass.CONFIGURATION));
        }
    }

    private void verifyPipelineRunForPauseResume(PipelineRun pipelineRun, Long runId) {
        Assert.notNull(pipelineRun,
                messageHelper.getMessage(MessageConstants.ERROR_RUN_PIPELINES_NOT_FOUND, runId));
        Assert.notNull(pipelineRun.getId(),
                messageHelper.getMessage(MessageConstants.ERROR_PIPELINE_RUN_ID_NOT_FOUND, runId));
        Assert.notNull(pipelineRun.getInstance(), messageHelper.getMessage(
                MessageConstants.ERROR_INSTANCE_NOT_FOUND, runId));
        RunInstance instance = pipelineRun.getInstance();
        Assert.notNull(instance.getNodeId(), messageHelper.getMessage(
                MessageConstants.ERROR_INSTANCE_ID_NOT_FOUND, runId));
        Assert.notNull(instance.getNodeIP(), messageHelper.getMessage(
                MessageConstants.ERROR_INSTANCE_IP_NOT_FOUND, runId));
        Assert.isTrue(!instance.getSpot(), messageHelper.getMessage(MessageConstants.ERROR_ON_DEMAND_REQUIRED));
        Assert.notNull(pipelineRun.getPodId(),
                messageHelper.getMessage(MessageConstants.ERROR_POD_ID_NOT_FOUND, runId));
    }

    private void validatePrettyUrlFree(String url) {
        pipelineRunDao.loadRunByPrettyUrl(url).ifPresent(r -> {
            throw new IllegalArgumentException(
                    messageHelper.getMessage(MessageConstants.ERROR_RUN_PRETTY_URL_IN_USE, url, r.getId()));
        });
    }

    private void updatePrettyUrlForFinishedRun(PipelineRun run) {
        if (run.getStatus().isFinal() && StringUtils.hasText(run.getPrettyUrl())) {
            run.setPrettyUrl(null);
            pipelineRunDao.updateRun(run);
        }
    }

    private void setRunPrice(final RunInstance instance, final PipelineRun run) {
        if (!instance.isEmpty()) {
            run.setInstance(instance);
            InstancePrice runPrice = instanceOfferManager.getInstanceEstimatedPrice(
                    instance.getNodeType(), instance.getNodeDisk(), instance.getSpot(), instance.getCloudRegionId());
            LOGGER.debug("Expected price per hour: {}", runPrice.getPricePerHour());
            run.setPricePerHour(BigDecimal.valueOf(runPrice.getPricePerHour()).setScale(2, RoundingMode.HALF_EVEN));
        }
    }

    private PipelineRun createRestartRun(final PipelineRun run) {
        PipelineRun restartedRun = new PipelineRun();
        Long runId = pipelineRunDao.createRunId();
        restartedRun.setId(runId);
        restartedRun.setStartDate(DateUtils.now());

        Optional<Pipeline> pipeline = Optional.ofNullable(run.getPipelineId())
                .map(pipelineId -> pipelineManager.load(pipelineId));

        pipeline.ifPresent(p -> {
            restartedRun.setPipelineName(p.getName());
            restartedRun.setRepository(p.getRepository());
            restartedRun.setPipelineId(p.getId());
            restartedRun.setVersion(run.getVersion());
            restartedRun.setRevisionName(gitManager.getRevisionName(run.getVersion()));
        });

        if (!pipeline.isPresent()) {
            fillMissingPipelineFields(restartedRun);
        }

        restartedRun.setStatus(TaskStatus.RUNNING);
        restartedRun.setCommitStatus(CommitStatus.NOT_COMMITTED);
        restartedRun.setLastChangeCommitTime(DateUtils.now());
        restartedRun.setPodId(getRootPodIDFromPipeline(restartedRun));
        restartedRun.setParams(run.getParams());
        restartedRun.parseParameters();
        restartedRun.setTimeout(run.getTimeout());
        restartedRun.setDockerImage(run.getDockerImage());
        restartedRun.setCmdTemplate(run.getCmdTemplate());
        restartedRun.setNodeCount(run.getNodeCount());
        RunInstance instance = copyInstance(run.getInstance());
        restartedRun.setInstance(instance);
        setRunPrice(instance, restartedRun);
        restartedRun.setSshPassword(PasswordGenerator.generatePassword());
        restartedRun.setOwner(run.getOwner());
        restartedRun.setEntitiesIds(run.getEntitiesIds());
        restartedRun.setConfigurationId(run.getConfigurationId());
        restartedRun.setExecutionPreferences(run.getExecutionPreferences());
        restartedRun.setRunSids(run.getRunSids());
        return restartedRun;
    }

    private void fillMissingPipelineFields(final PipelineRun restartedRun) {
        restartedRun.setPipelineName(PipelineRun.DEFAULT_PIPELINE_NAME);
        restartedRun.setRepository(EMPTY_STING);
        restartedRun.setPipelineId(EMPTY_PIPELINE_ID);
        restartedRun.setVersion(EMPTY_STING);
        restartedRun.setRevisionName(EMPTY_STING);
    }

    private RunInstance copyInstance(final RunInstance instance) {
        return Optional.ofNullable(instance).map(i -> {
            RunInstance runInstance = new RunInstance();
            runInstance.setCloudRegionId(i.getCloudRegionId());
            runInstance.setNodeDisk(i.getNodeDisk());
            runInstance.setEffectiveNodeDisk(i.getEffectiveNodeDisk());
            runInstance.setNodeImage(i.getNodeImage());
            runInstance.setNodeType(i.getNodeType());
            runInstance.setSpot(i.getSpot());
            return runInstance;
        }).orElse(new RunInstance());
    }
}
