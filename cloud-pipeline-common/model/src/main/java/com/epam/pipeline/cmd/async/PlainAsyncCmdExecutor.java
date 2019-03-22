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

package com.epam.pipeline.cmd.async;

import com.epam.pipeline.cmd.CmdExecutionException;
import com.epam.pipeline.cmd.CmdExecutor;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Async cmd executor basic implementation.
 *
 * Async version of {@link com.epam.pipeline.cmd.PlainCmdExecutor}.
 */
@Slf4j
@RequiredArgsConstructor
public class PlainAsyncCmdExecutor implements AsyncCmdExecutor {

    protected final Executor executor;
    protected final CmdExecutor cmdExecutor;

    @Override
    public SingleProcessExecution<String> launchCommand(final String command,
                                                        final Map<String, String> environmentVariables,
                                                        final File workDir) {
        final Process process = cmdExecutor.launchCommand(command, environmentVariables, workDir);
        final CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            final StringBuilder output = new StringBuilder();
            final StringBuilder errors = new StringBuilder();
            try {
                final Thread stdReader = new Thread(() -> readOutputStream(command, output,
                        new InputStreamReader(process.getInputStream())));
                final Thread errReader = new Thread(() -> readOutputStream(command, errors,
                        new InputStreamReader(process.getErrorStream())));
                stdReader.start();
                errReader.start();
                final int exitCode = process.waitFor();
                stdReader.join();
                errReader.join();
                if (exitCode == 0) {
                    return output.toString();
                } else {
                    log.error("Command '{}' err output: {}.", command, errors.toString());
                    throw new CmdExecutionException(command, errors.toString());
                }
            } catch (InterruptedException e) {
                throw new CmdExecutionException(command, e);
            }
        }, executor);
        return new SingleProcessExecution<>(process, future);
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
