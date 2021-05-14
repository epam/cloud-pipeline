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
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.run.PipelineStart;
import com.epam.pipeline.entity.pipeline.run.parameter.RunAccessType;
import com.epam.pipeline.entity.pipeline.run.parameter.RunSid;
import com.epam.pipeline.entity.user.RunnerSid;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.security.CheckPermissionHelper;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.manager.user.UserRunnersManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.springframework.security.concurrent.DelegatingSecurityContextCallable;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
@RequiredArgsConstructor
@Slf4j
public class PipelineRunAsManager {
    private final PipelineRunManager pipelineRunManager;
    private final UserRunnersManager userRunnersManager;
    private final UserManager userManager;
    private final AuthManager authManager;
    private final PipelineConfigurationManager configurationManager;
    private final MessageHelper messageHelper;
    private final CheckPermissionHelper permissionHelper;
    private final Executor runAsExecutor;

    public PipelineRun runPipeline(final PipelineStart runVO) {
        configureRunnerUser(runVO);
        try {
            return CompletableFuture.supplyAsync(() -> run(runVO), runAsExecutor).get();
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new IllegalStateException(e);
        }
    }

    private PipelineRun run(final PipelineStart runVO) {
        try {
            return new DelegatingSecurityContextCallable<>(() -> pipelineRunManager.runPipeline(runVO),
                    permissionHelper.createContext(runVO.getRunAs())).call();
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new IllegalStateException(e);
        }
    }

    private void configureRunnerUser(final PipelineStart runVO) {
        if (StringUtils.isEmpty(runVO.getRunAs())) {
            return;
        }
        final PipelineConfiguration currentUserConfiguration = configurationManager.getPipelineConfiguration(runVO);
        final String runAsUser = StringUtils.isEmpty(currentUserConfiguration.getRunAs())
                ? runVO.getRunAs()
                : userManager.loadUserByNameOrId(currentUserConfiguration.getRunAs()).getUserName();
        final String currentUser = authManager.getAuthorizedUser();
        final RunnerSid allowedRunnerSid = userRunnersManager.findRunnerSid(currentUser, runAsUser);
        Assert.notNull(allowedRunnerSid, messageHelper.getMessage(
                MessageConstants.ERROR_RUN_ALLOWED_SID_NOT_FOUND, runAsUser));
        runVO.setRunSids(buildRunSids(runVO.getRunSids(), currentUser, allowedRunnerSid.getAccessType()));
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
}
