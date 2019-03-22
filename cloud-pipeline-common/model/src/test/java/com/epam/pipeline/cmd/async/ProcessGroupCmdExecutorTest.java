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

import static com.epam.pipeline.cmd.async.ProcessGroupExecution.DEFAULT_PROCESSES_KILL_COMMAND;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.epam.pipeline.cmd.CmdExecutor;
import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.junit.Test;

@SuppressWarnings("PMD.TooManyStaticImports")
public class ProcessGroupCmdExecutorTest {

    private static final String COMMAND = "some cmd command";
    private static final String SLEEP_5 = "sleep 5";
    private static final long SECOND = 1000L;

    private final Executor executor = Executors.newSingleThreadExecutor();
    private final CmdExecutor innerCmdExecutor = mock(CmdExecutor.class);
    private final AsyncCmdExecutor asyncCmdExecutor = new ProcessGroupCmdExecutor(executor, innerCmdExecutor,
            innerCmdExecutor);

    @Test
    public void cancelShouldTryToKillAllProcessesWithinRootProcessGroup() throws InterruptedException, IOException {
        final Process process = Runtime.getRuntime().exec(SLEEP_5);
        when(innerCmdExecutor.launchCommand(eq(COMMAND), any(), any()))
                .thenReturn(process);
        final Execution<String> execution = asyncCmdExecutor.launchCommand(COMMAND);
        Thread.sleep(SECOND);
        assertTrue(process.isAlive());
        assertFalse(execution.isDone());
        execution.cancel();
        Thread.sleep(SECOND);
        verify(innerCmdExecutor).executeCommand(startsWith(DEFAULT_PROCESSES_KILL_COMMAND.replaceAll("\\%s", "")));
    }
}
