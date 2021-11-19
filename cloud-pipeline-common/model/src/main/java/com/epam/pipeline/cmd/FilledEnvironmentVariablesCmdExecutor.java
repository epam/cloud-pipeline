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
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.commons.text.StringSubstitutor;

/**
 * Cmd executor that replaces environment variables links in the executing or launching command.
 *
 * Environment variables are determined by the {@link FilledEnvironmentVariablesCmdExecutor#prefix}
 * and {@link FilledEnvironmentVariablesCmdExecutor#suffix}.
 *
 * By default suffix and prefix are {@link FilledEnvironmentVariablesCmdExecutor#DEFAULT_PREFIX}
 * and {@link FilledEnvironmentVariablesCmdExecutor#DEFAULT_SUFFIX} respectively.
 */
@RequiredArgsConstructor
public class FilledEnvironmentVariablesCmdExecutor implements CmdExecutor {

    private static final String DEFAULT_PREFIX = "{{";
    private static final String DEFAULT_SUFFIX = "}}";

    private final CmdExecutor cmdExecutor;
    private final String prefix;
    private final String suffix;

    public FilledEnvironmentVariablesCmdExecutor(final CmdExecutor cmdExecutor) {
        this.cmdExecutor = cmdExecutor;
        this.prefix = DEFAULT_PREFIX;
        this.suffix = DEFAULT_SUFFIX;
    }

    @Override
    public String executeCommand(final String command,
                                 final Map<String, String> environmentVariables,
                                 final File workDir,
                                 final String username) {
        return cmdExecutor.executeCommand(withFilledEnvironmentVariables(command, environmentVariables),
                environmentVariables, workDir, username);
    }

    @Override
    public Process launchCommand(final String command,
                                 final Map<String, String> environmentVariables,
                                 final File workDir,
                                 final String username) {
        return cmdExecutor.launchCommand(withFilledEnvironmentVariables(command, environmentVariables),
                                         environmentVariables, workDir, username);
    }

    private String withFilledEnvironmentVariables(final String command,
                                                  final Map<String, String> environmentVariables) {
        return new StringSubstitutor(environmentVariables, prefix, suffix).replace(command);
    }
}
