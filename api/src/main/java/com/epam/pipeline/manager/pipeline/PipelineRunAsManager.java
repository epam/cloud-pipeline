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

package com.epam.pipeline.manager.pipeline;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.configuration.PipeConfValueVO;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.pipeline.run.PipelineStart;
import com.epam.pipeline.entity.pipeline.run.parameter.RunAccessType;
import com.epam.pipeline.entity.pipeline.run.parameter.RunSid;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.RunnerSid;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.security.CheckPermissionHelper;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.manager.user.UserRunnersManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.springframework.security.concurrent.DelegatingSecurityContextCallable;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("PMD.AvoidCatchingGenericException")
public class PipelineRunAsManager {

    private static final String FALLBACK_ORIGINAL_OWNER_PARAMETER = "ORIGINAL_OWNER";

    private final PipelineRunManager pipelineRunManager;
    private final UserRunnersManager userRunnersManager;
    private final UserManager userManager;
    private final AuthManager authManager;
    private final PipelineConfigurationManager configurationManager;
    private final MessageHelper messageHelper;
    private final CheckPermissionHelper permissionHelper;
    private final Executor runAsExecutor;
    private final PreferenceManager preferenceManager;
    private final ToolManager toolManager;

    public boolean runAsAnotherUser(final PipelineStart runVO) {
        return !StringUtils.isEmpty(getRunAsUserName(runVO));
    }

    public PipelineRun runPipeline(final PipelineStart runVO) {
        return run(runVO, () -> pipelineRunManager.runPipeline(runVO));
    }

    public PipelineRun runTool(final PipelineStart runVO) {
        return run(runVO, () -> pipelineRunManager.runCmd(runVO));
    }

    public boolean hasCurrentUserAsRunner(final String runAsUserName) {
        final PipelineUser currentUser = userManager.loadUserByName(authManager.getAuthorizedUser());
        return userRunnersManager.hasUserAsRunner(currentUser, runAsUserName);
    }

    public String getRunAsUserName(final PipelineStart runVO) {
        final PipelineConfiguration currentUserConfiguration = configurationManager.getPipelineConfiguration(runVO);
        return StringUtils.isEmpty(currentUserConfiguration.getRunAs())
                ? runVO.getRunAs()
                : userManager.loadByNameOrId(currentUserConfiguration.getRunAs()).getUserName();
    }

    private PipelineRun run(final PipelineStart runVO, final Callable<PipelineRun> runCallable) {
        final String runAsUser = getRunAsUserName(runVO);
        configureRunnerSids(runVO, runAsUser);
        configureOriginalOwner(runVO);
        return supplyRunAsync(runAsUser, runCallable);
    }

    private void configureOriginalOwner(final PipelineStart runVO) {
        runVO.setParams(withOriginalOwnerParam(runVO.getParams()));
    }

    private Map<String, PipeConfValueVO> withOriginalOwnerParam(final Map<String, PipeConfValueVO> params) {
        return Optional.ofNullable(authManager.getAuthorizedUser())
                .map(owner -> withOriginalOwnerParam(params, owner))
                .orElse(params);
    }

    private Map<String, PipeConfValueVO> withOriginalOwnerParam(final Map<String, PipeConfValueVO> params,
                                                                final String owner) {
        final Map<String, PipeConfValueVO> updatedParams = new HashMap<>(MapUtils.emptyIfNull(params));
        updatedParams.put(getOriginalOwnerParamName(), new PipeConfValueVO(owner));
        return updatedParams;
    }

    private String getOriginalOwnerParamName() {
        return Optional.of(SystemPreferences.LAUNCH_ORIGINAL_OWNER_PARAMETER)
                .map(preferenceManager::getPreference)
                .orElse(FALLBACK_ORIGINAL_OWNER_PARAMETER);
    }

    private PipelineRun supplyRunAsync(final String runAsUser, final Callable<PipelineRun> runCallable) {
        try {
            return CompletableFuture.supplyAsync(() -> runWithUserContext(runAsUser, runCallable), runAsExecutor).get();
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new IllegalStateException(e);
        }
    }

    private PipelineRun runWithUserContext(final String runAsUserName, final Callable<PipelineRun> runCallable) {
        try {
            return new DelegatingSecurityContextCallable<>(runCallable, permissionHelper.createContext(runAsUserName))
                    .call();
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new IllegalStateException(e);
        }
    }

    private void configureRunnerSids(final PipelineStart runVO, final String runAsUser) {
        final String currentUser = authManager.getAuthorizedUser();
        final RunnerSid allowedRunnerSid = userRunnersManager.findRunnerSid(currentUser, runAsUser);
        validateRunner(runVO, runAsUser, allowedRunnerSid, currentUser);
        runVO.setRunSids(buildRunSids(runVO.getRunSids(), currentUser, allowedRunnerSid.getAccessType()));
    }

    private void validateRunner(final PipelineStart runVO,
                                final String runAsUser,
                                final RunnerSid allowedRunnerSid,
                                final String currentUser) {
        Assert.notNull(allowedRunnerSid, messageHelper.getMessage(
                MessageConstants.ERROR_RUN_ALLOWED_SID_NOT_FOUND, runAsUser));
        final Long pipelineId = runVO.getPipelineId();
        if (Objects.nonNull(pipelineId)) {
            validatePipeline(runVO, runAsUser, allowedRunnerSid, currentUser, pipelineId);
        } else {
            validateTool(runVO.getDockerImage(), runAsUser, allowedRunnerSid, currentUser);
        }
    }

    private void validatePipeline(final PipelineStart runVO,
                                  final String runAsUser,
                                  final RunnerSid allowedRunnerSid,
                                  final String currentUser,
                                  final Long pipelineId) {
        Assert.isTrue(isNullOrTrue(allowedRunnerSid.getPipelinesAllowed()),
                messageHelper.getMessage(
                        MessageConstants.ERROR_RUN_AS_PIPELINES_NOT_ALLOWED, currentUser, runAsUser));
        if (CollectionUtils.isNotEmpty(allowedRunnerSid.getPipelines())) {
            Assert.isTrue(allowedRunnerSid.getPipelines().contains(pipelineId),
                    messageHelper.getMessage(MessageConstants.ERROR_RUN_AS_PIPELINE_NOT_ALLOWED,
                            currentUser, pipelineId, runAsUser));
        }
        final PipelineConfiguration currentUserConfiguration = configurationManager.getPipelineConfiguration(runVO);
        validateTool(currentUserConfiguration.getDockerImage(), runAsUser, allowedRunnerSid, currentUser);
    }

    private void validateTool(final String dockerImage,
                              final String runAsUser,
                              final RunnerSid allowedRunnerSid,
                              final String currentUser) {
        Assert.isTrue(isNullOrTrue(allowedRunnerSid.getToolsAllowed()),
                messageHelper.getMessage(MessageConstants.ERROR_RUN_AS_TOOLS_NOT_ALLOWED, currentUser, runAsUser));
        if (CollectionUtils.isNotEmpty(allowedRunnerSid.getTools())) {
            final Tool tool = toolManager.loadByNameOrId(dockerImage);
            Assert.isTrue(allowedRunnerSid.getTools().contains(tool.getId()),
                    messageHelper.getMessage(MessageConstants.ERROR_RUN_AS_TOOL_NOT_ALLOWED,
                            currentUser, dockerImage, runAsUser));
        }
    }

    private List<RunSid> buildRunSids(final List<RunSid> runSidsFromVO, final String currentUser,
                                      final RunAccessType allowedAccessType) {
        final Set<RunSid> runSids = new HashSet<>(ListUtils.emptyIfNull(runSidsFromVO));

        final RunSid runSid = new RunSid();
        runSid.setName(currentUser.toUpperCase());
        runSid.setIsPrincipal(true);
        runSid.setAccessType(allowedAccessType);
        runSids.add(runSid);

        return new ArrayList<>(runSids);
    }

    private boolean isNullOrTrue(final Boolean value) {
        return Objects.isNull(value) || value;
    }
}
