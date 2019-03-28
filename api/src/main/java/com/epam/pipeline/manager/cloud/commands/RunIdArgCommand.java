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

package com.epam.pipeline.manager.cloud.commands;

import lombok.Builder;

import java.util.ArrayList;
import java.util.List;

@Builder
public class RunIdArgCommand extends AbstractClusterCommand {

    private static final int MIN_LENGTH = 4;

    private final String executable;
    private final String script;
    private final String runId;
    private final String cloud;

    @Override
    protected List<String> buildCommandArguments() {
        final List<String> commands = new ArrayList<>(MIN_LENGTH);
        commands.add(executable);
        commands.add(script);
        commands.add(RUN_ID_PARAMETER);
        commands.add(runId);
        commands.add(CLOUD_PARAMETER);
        commands.add(cloud);
        return commands;
    }
}
