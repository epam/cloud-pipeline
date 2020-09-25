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

package com.epam.pipeline.dts.transfer.service.impl;

import com.epam.pipeline.cmd.CmdExecutor;
import com.epam.pipeline.cmd.PipelineCLI;
import com.epam.pipeline.cmd.PipelineCLIImpl;
import com.epam.pipeline.dts.transfer.service.CmdExecutorsProvider;
import com.epam.pipeline.dts.transfer.service.PipelineCliProvider;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
public class PipelineCliProviderImpl implements PipelineCliProvider {

    private static final String API_ENV_VAR = "API";
    private static final String API_TOKEN_ENV_VAR = "API_TOKEN";

    private final CmdExecutorsProvider cmdExecutorsProvider;
    private final String pipelineCliExecutable;
    private final String pipeCpSuffix;
    private final String qsubTemplate;
    private final boolean isGridUploadEnabled;
    private final boolean forceUpload;
    private final int retryCount;

    @Override
    public PipelineCLI getPipelineCLI(final String api, final String apiToken) {
        final CmdExecutor cmdExecutor =
                authenticated(api, apiToken,
                        impersonating(
                                cmdExecutor()));
        return new PipelineCLIImpl(pipelineCliExecutable, pipeCpSuffix, forceUpload, retryCount, cmdExecutor);
    }

    private CmdExecutor authenticated(final String api,
                                      final String apiToken,
                                      final CmdExecutor cmdExecutor) {
        return cmdExecutorsProvider.getEnvironmentCmdExecutor(cmdExecutor, credentialsParameters(api, apiToken));
    }

    private CmdExecutor impersonating(final CmdExecutor cmdExecutor) {
        return cmdExecutorsProvider.getImpersonatingCmdExecutor(cmdExecutor);
    }

    private CmdExecutor cmdExecutor() {
        final CmdExecutor cmdExecutor = cmdExecutorsProvider.getCmdExecutor();
        return isGridUploadEnabled
            ? cmdExecutorsProvider.getQsubCmdExecutor(cmdExecutor, qsubTemplate)
            : cmdExecutor;
    }

    private Map<String, String> credentialsParameters(final String api,
                                                      final String apiToken) {
        final Map<String, String> map = new HashMap<>();
        map.put(API_ENV_VAR, api);
        map.put(API_TOKEN_ENV_VAR, apiToken);
        return map;
    }
}
