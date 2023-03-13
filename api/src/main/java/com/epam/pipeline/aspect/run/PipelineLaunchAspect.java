/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.aspect.run;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.pipeline.run.PipelineStart;
import com.epam.pipeline.entity.pipeline.run.RunAssignPolicy;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.manager.cluster.KubernetesConstants;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.security.AuthManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@Aspect
@Slf4j
@RequiredArgsConstructor
public class PipelineLaunchAspect {
    private final PreferenceManager preferenceManager;
    private final AuthManager authManager;
    private final MessageHelper messageHelper;

    @Before("@annotation(com.epam.pipeline.aspect.run.PipelineLaunchCheck) && args(runVO)")
    public void checkRunLaunchIsNotForbidden(JoinPoint joinPoint, PipelineStart runVO) {
        final PipelineUser user = authManager.getCurrentUser();
        if (user.isAdmin()) {
            return;
        }
        final Boolean advancedRunAssignPolicy = Optional.ofNullable(runVO.getRunAssignPolicy())
                .map(RunAssignPolicy::getRules)
                .map(rules ->
                        rules.stream().anyMatch(rule -> !rule.getLabel().equals(KubernetesConstants.RUN_ID_LABEL)))
                .orElse(false);
        if (advancedRunAssignPolicy) {
            log.error(messageHelper.getMessage(MessageConstants.ERROR_RUN_ASSIGN_POLICY_FORBIDDEN, user.getUserName()));
        }
        final Boolean isSystemJobRun = Optional.ofNullable(runVO.getParams())
                .map(params ->
                    params.entrySet().stream()
                        .anyMatch(p -> {
                            final String systemJobFlag = preferenceManager.getPreference(
                                    SystemPreferences.SYSTEM_JOB_FLAG_PARAMETER
                            );
                            return p.getKey().equals(systemJobFlag) &&
                                    p.getValue().getValue().equalsIgnoreCase(Boolean.TRUE.toString());
                        })
                ).orElse(false);
        if (isSystemJobRun) {
            log.error(messageHelper.getMessage(MessageConstants.ERROR_RUN_SYSTEM_JOB_FORBIDDEN, user.getUserName()));
        }
    }
}
