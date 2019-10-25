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

package com.epam.pipeline.acl.run;

import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.security.AuthManager;
import lombok.RequiredArgsConstructor;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;

@RequiredArgsConstructor
public class RunAclFactory {

    public static final Long TEST_RUN_ID = 1L;

    private final AuthManager authManager;
    private final PipelineRunManager mockRunManager;

    public PipelineRun initToolPipelineRunForCurrentUser() {
        return initToolPipelineRunForOwner(authManager.getAuthorizedUser());
    }

    public PipelineRun initToolPipelineRunForOwner(final String owner) {
        final PipelineRun pipelineRun = new PipelineRun();
        pipelineRun.setId(TEST_RUN_ID);
        pipelineRun.setOwner(owner);
        doReturn(pipelineRun).when(mockRunManager).loadPipelineRun(eq(TEST_RUN_ID));
        return pipelineRun;
    }

    public PipelineRun initPipelineRunForCurrentUser(final Pipeline parent) {
        return initPipelineRunForOwner(parent, authManager.getAuthorizedUser());
    }

    public PipelineRun initPipelineRunForOwner(final Pipeline parent, final String owner) {
        final PipelineRun pipelineRun = new PipelineRun();
        pipelineRun.setId(TEST_RUN_ID);
        pipelineRun.setOwner(owner);
        pipelineRun.setPipelineId(parent.getId());
        doReturn(pipelineRun).when(mockRunManager).loadPipelineRun(eq(TEST_RUN_ID));
        doReturn(parent).when(mockRunManager).loadRunParent(eq(pipelineRun));
        return pipelineRun;
    }
}
