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
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.manager.cluster.KubernetesConstants;
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
    private final AuthManager authManager;
    private final MessageHelper messageHelper;

    @Before("@annotation(com.epam.pipeline.aspect.run.PipelineLaunchCheck) && args(configuration)")
    public void checkRunLaunchIsNotForbidden(JoinPoint joinPoint, PipelineConfiguration configuration) {

        Optional.ofNullable(configuration.getPodAssignPolicy()).ifPresent(assignPolicy -> {
            if (!assignPolicy.isValid()) {
                throw new IllegalArgumentException(
                        messageHelper.getMessage(MessageConstants.ERROR_RUN_ASSIGN_POLICY_MALFORMED,
                                assignPolicy.toString()));
            }
        });

        final PipelineUser user = authManager.getCurrentUser();
        if (user.isAdmin()) {
            return;
        }
        final Boolean advancedRunAssignPolicy = Optional.ofNullable(configuration.getPodAssignPolicy())
                .map(policy -> !policy.getSelector().getLabel().equalsIgnoreCase(KubernetesConstants.RUN_ID_LABEL))
                .orElse(false);
        if (advancedRunAssignPolicy) {
            throw new IllegalArgumentException(
                    messageHelper.getMessage(MessageConstants.ERROR_RUN_ASSIGN_POLICY_FORBIDDEN, user.getUserName()));
        }
        if (configuration.getKubeServiceAccount() != null) {
            throw new IllegalArgumentException(
                    messageHelper.getMessage(
                            MessageConstants.ERROR_RUN_WITH_SERVICE_ACCOUNT_FORBIDDEN, user.getUserName())
            );
        }
    }
}
