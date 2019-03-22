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

package com.epam.pipeline.manager.docker.scan;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.pipeline.run.PipelineStart;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.exception.ToolExecutionDeniedException;
import com.epam.pipeline.manager.pipeline.PipelineConfigurationManager;
import com.epam.pipeline.manager.pipeline.ToolManager;
import com.epam.pipeline.manager.security.AuthManager;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * An aspect to check that Tool's being executed, comply with security policies
 */
@Service
@Aspect
public class ToolSecurityPolicyAspect {

    @Autowired
    private PipelineConfigurationManager configurationManager;

    @Autowired
    private ToolManager toolManager;

    @Autowired
    private AuthManager authManager;

    @Autowired
    private MessageHelper messageHelper;

    @Autowired
    private ToolScanManager clairToolScanManager;

    @Before("@annotation(com.epam.pipeline.manager.docker.scan.ToolSecurityPolicyCheck) && args(runVO,..)")
    public void checkToolBySecurityPolicy(JoinPoint joinPoint, PipelineStart runVO) {
        if (runVO.isForce()) {
            PipelineUser user = authManager.getCurrentUser();
            if (user != null && user.isAdmin()) {
                return;
            }
        }

        PipelineConfiguration configuration = configurationManager.getPipelineConfiguration(runVO);

        String tag = toolManager.getTagFromImageName(configuration.getDockerImage());
        Tool tool = toolManager.loadByNameOrId(configuration.getDockerImage());
        if (!clairToolScanManager.checkTool(tool, tag)) {
            throw new ToolExecutionDeniedException(
                    messageHelper.getMessage(MessageConstants.ERROR_TOOL_SECURITY_POLICY_VIOLATION));
        }
    }

}
