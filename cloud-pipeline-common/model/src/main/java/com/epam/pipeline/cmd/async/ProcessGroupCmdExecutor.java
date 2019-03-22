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

import com.epam.pipeline.cmd.CmdExecutor;
import java.io.File;
import java.util.Map;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;

/**
 * Async cmd executor that launches executions that will kill not only the process
 * but its whole process group on cancel.
 */
@Slf4j
public class ProcessGroupCmdExecutor extends PlainAsyncCmdExecutor {

    private final CmdExecutor plainCmdExecutor;

    public ProcessGroupCmdExecutor(final Executor executor,
                                   final CmdExecutor modelCmdExecutor,
                                   final CmdExecutor plainCmdExecutor) {
        super(executor, modelCmdExecutor);
        this.plainCmdExecutor = plainCmdExecutor;
    }

    @Override
    public ProcessGroupExecution<String> launchCommand(final String command,
                                                       final Map<String, String> environmentVariables,
                                                       final File workDir) {
        final SingleProcessExecution<String> execution = super.launchCommand(command, environmentVariables, workDir);
        return new ProcessGroupExecution<>(execution.process, execution.result, plainCmdExecutor);
    }
}
