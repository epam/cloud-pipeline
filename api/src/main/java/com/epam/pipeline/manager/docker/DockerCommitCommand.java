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

package com.epam.pipeline.manager.docker;

import lombok.Builder;

import java.util.ArrayList;
import java.util.List;

@Builder
public class DockerCommitCommand extends AbstractDockerCommand {
    private static final String COMMIT_COMMAND_TEMPLATE = "curl -k -s \"%s\" | sudo -E /bin/bash " +
            "--login /dev/stdin %s &> ~/commit_pipeline.log &";

    private final String api;
    private final String apiToken;
    private final String commitDistributionUrl;
    private final String distributionUrl;
    private final String runId;
    private final String containerId;
    private final String cleanUp;
    private final String stopPipeline;
    private final String timeout;
    private final String registryToPush;
    private final String registryToPushId;
    private final String toolGroupId;
    private final String newImageName;
    private final String dockerLogin;
    private final String dockerPassword;
    private final String isPipelineAuth;
    private final String preCommitCommand;
    private final String postCommitCommand;

    private final String runScriptUrl;

    @Override
    public String getCommand() {
        return getDockerCommand(COMMIT_COMMAND_TEMPLATE, runScriptUrl);
    }

    @Override
    protected List<String> buildCommandArguments() {
        final List<String> command = new ArrayList<>();
        command.add(api);
        command.add(apiToken);
        command.add(commitDistributionUrl);
        command.add(distributionUrl);
        command.add(runId);
        command.add(containerId);
        command.add(cleanUp);
        command.add(stopPipeline);
        command.add(timeout);
        command.add(registryToPush);
        command.add(registryToPushId);
        command.add(toolGroupId);
        command.add(newImageName);
        command.add(dockerLogin);
        command.add(dockerPassword);
        command.add(isPipelineAuth);
        command.add(preCommitCommand);
        command.add(postCommitCommand);
        return command;
    }
}
