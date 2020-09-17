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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PlainCmdExecutor implements CmdExecutor {

    private static final String DEFAULT_SHELL = "bash";

    @Override
    public String executeCommand(final String command,
                                 final Map<String, String> environmentVariables,
                                 final File workDir,
                                 final String username) {
        StringBuilder output = new StringBuilder();
        StringBuilder errors = new StringBuilder();
        try {
            Process p = launchCommand(command, environmentVariables, workDir, username);
            Thread stdReader = new Thread(() -> readOutputStream(command, output,
                    new InputStreamReader(p.getInputStream())));
            Thread errReader = new Thread(() -> readOutputStream(command, errors,
                    new InputStreamReader(p.getErrorStream())));
            stdReader.start();
            errReader.start();
            int exitCode = p.waitFor();
            stdReader.join();
            errReader.join();
            if (exitCode != 0) {
                final String errorMessage = errors.toString();
                log.error("Command '{}' err output: {}.", command, errorMessage);
                throw new CmdExecutionException(command, errorMessage);
            }
        } catch (InterruptedException e) {
            throw new CmdExecutionException(command, e);
        }
        return output.toString();
    }

    @Override
    public Process launchCommand(final String command, final Map<String, String> environmentVariables,
                                 final File workDir, final String username) {
        try {
            final String[] cmd = {DEFAULT_SHELL, "-c", command};
            final Map<String, String> mergedEnvVars = new HashMap<>(System.getenv());
            if (!environmentVariables.isEmpty()) {
                mergedEnvVars.putAll(environmentVariables);
            }
            final String[] envp = mergedEnvVars.entrySet().stream()
                    .map(entry -> String.format("%s=%s", entry.getKey(), entry.getValue()))
                    .toArray(String[]::new);
            return Runtime.getRuntime().exec(cmd, envp, workDir);
        } catch (IOException e) {
            throw new CmdExecutionException(command, e);
        }
    }

    private void readOutputStream(String command, StringBuilder content, InputStreamReader in) {
        try (BufferedReader reader = new BufferedReader(in)) {
            appendReaderContent(content, reader);
        } catch (IOException e) {
            throw new CmdExecutionException(command, e);
        }
    }

    private void appendReaderContent(StringBuilder output, BufferedReader reader)
            throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            output.append(line).append('\n');
        }
    }
}
