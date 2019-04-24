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
public class DockerPauseCommand extends AbstractDockerCommand {
    private static final String PAUSE_COMMAND_TEMPLATE = "curl -k -s \"%s\" | sudo -E /bin/bash " +
            "--login /dev/stdin %s &> ~/pause_pipeline.log";

    private final String api;
    private final String apiToken;
    private final String pauseDistributionUrl;
    private final String distributionUrl;
    private final String runId;
    private final String containerId;
    private final String timeout;
    private final String newImageName;
    private final String defaultTaskName;
    private final String preCommitCommand;

    private final String runPauseScriptUrl;

    @Override
    public String getCommand() {
        return getDockerCommand(PAUSE_COMMAND_TEMPLATE, runPauseScriptUrl);
    }

    @Override
    protected List<String> buildCommandArguments() {
        final List<String> command = new ArrayList<>();
        command.add(api);
        command.add(apiToken);
        command.add(pauseDistributionUrl);
        command.add(distributionUrl);
        command.add(runId);
        command.add(containerId);
        command.add(timeout);
        command.add(newImageName);
        command.add(defaultTaskName);
        command.add(preCommitCommand);
        return command;
    }
}
