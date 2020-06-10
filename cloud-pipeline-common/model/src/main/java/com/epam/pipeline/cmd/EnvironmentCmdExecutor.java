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

package com.epam.pipeline.cmd;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class EnvironmentCmdExecutor implements CmdExecutor {

    private final CmdExecutor executor;
    private final Map<String, String> envVars;

    @Override
    public String executeCommand(final String command) {
        return executor.executeCommand(command, envVars, null);
    }

    @Override
    public String executeCommand(final String command,
                                 final Map<String, String> environmentVariables) {
        return executeCommand(command, environmentVariables, null);
    }

    @Override
    public String executeCommand(final String command,
                                 final Map<String, String> environmentVariables,
                                 final File file) {
        return executor.executeCommand(command, withDefaults(environmentVariables), file);
    }

    @Override
    public String executeCommand(String command, Map<String, String> environmentVariables, File workDir,
                                 String username) {
        return executor.executeCommand(command, environmentVariables, workDir, username);
    }

    @Override
    public Process launchCommand(final String command,
                                 final Map<String, String> environmentVariables,
                                 final File workDir) {
        return executor.launchCommand(command, withDefaults(environmentVariables), workDir);
    }

    @Override
    public Process launchCommand(final String command,
                                 final Map<String, String> environmentVariables,
                                 final File workDir,
                                 final String username) {
        return executor.launchCommand(command, withDefaults(environmentVariables), workDir, username);
    }

    private Map<String, String> withDefaults(final Map<String, String> environmentVariables) {
        final Map<String, String> mergedEnvVars = new HashMap<>();
        mergedEnvVars.putAll(envVars);
        mergedEnvVars.putAll(environmentVariables);
        return mergedEnvVars;
    }
}
