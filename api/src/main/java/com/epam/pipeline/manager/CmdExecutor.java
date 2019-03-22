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

package com.epam.pipeline.manager;

import com.epam.pipeline.exception.CmdExecutionException;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;

public class CmdExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(CmdExecutor.class);

    public String executeCommand(String command) {
        return executeCommand(command, false);
    }

    public String executeCommand(String command, boolean silent) {
        return executeCommand(command, null, null, silent);
    }

    public String executeCommandWithEnvVars(String command, Map<String, String> envVars) {
        if (MapUtils.isEmpty(envVars)) {
            return executeCommand(command);
        }

        Map<String, String> getenv = System.getenv();
        envVars.putAll(getenv);

        String[] env = envVars.entrySet().stream()
                .filter(e -> StringUtils.isNotEmpty(e.getKey()) && StringUtils.isNotEmpty(e.getValue()))
                .map(Object::toString)
                .toArray(String[]::new);
        return executeCommand(command, env, null, false);
    }

    public String executeCommand(String command, String[] envVars, File context, boolean silent) {
        StringBuilder output = new StringBuilder();
        StringBuilder errors = new StringBuilder();
        Process p;
        try {
            p = Runtime.getRuntime().exec(command, envVars, context);
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
                if (!silent) {
                    LOGGER.error("Command '{}' err output: {}.", command, errors.toString());
                }
                throw new CmdExecutionException(command, exitCode, errors.toString());
            }
        } catch (IOException e) {
            throw new CmdExecutionException(command, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CmdExecutionException(command, e);
        }
        return output.toString();
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
