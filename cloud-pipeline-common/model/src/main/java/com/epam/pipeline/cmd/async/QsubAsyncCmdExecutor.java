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
import java.io.File;
import java.util.Map;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class QsubAsyncCmdExecutor implements AsyncCmdExecutor {

    private final AsyncCmdExecutor asyncCmdExecutor;
    private final CmdExecutor cmdExecutor;
    private final String jobNameVariable;

    @Override
    public Execution<String> launchCommand(final String command,
                                           final Map<String, String> environmentVariables,
                                           final File workDir) {
        final String jobName = environmentVariables.get(jobNameVariable);
        if (jobName == null) {
            throw new CmdExecutionException(String.format("Qsub command execution requires job name environment " +
                    "variable '%s'", jobNameVariable));
        }
        final Execution<String> execution = asyncCmdExecutor.launchCommand(command, environmentVariables, workDir);
        return new QsubExecution<>(execution, cmdExecutor, jobName);
    }
}
