/*
 * Copyright 2022 EPAM Systems, Inc. (https://www.epam.com/)
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
public class DockerContainerLayersCommand extends AbstractDockerCommand {
    private static final String LAYERS_COMMAND_TEMPLATE = "curl -k -s \"%s\" | sudo -E /bin/bash " +
            "--login /dev/stdin %s";

    private final String containerId;

    private final String runScriptUrl;

    @Override
    public String getCommand() {
        return getDockerCommand(LAYERS_COMMAND_TEMPLATE, runScriptUrl);
    }

    @Override
    protected List<String> buildCommandArguments() {
        final List<String> command = new ArrayList<>();
        command.add(containerId);
        return command;
    }
}
