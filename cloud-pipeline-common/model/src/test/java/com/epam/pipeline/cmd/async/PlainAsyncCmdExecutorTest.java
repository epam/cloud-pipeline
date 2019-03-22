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

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.epam.pipeline.cmd.CmdExecutionException;
import com.epam.pipeline.cmd.CmdExecutor;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Function;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;

@SuppressWarnings("PMD.TooManyStaticImports")
public class PlainAsyncCmdExecutorTest {

    private static final int OK_EXIT_CODE = 0;
    private static final int BAD_EXIT_CODE = 1;
    private static final String COMMAND = "some cmd command";
    private static final String STD_OUT = "std out";
    private static final String STD_ERR = "std err";
    private static final String LINE_BREAK = "\n";
    private static final String SLEEP_5 = "sleep 5";
    private static final long SECOND = 1000L;

    private final Executor executor = Executors.newSingleThreadExecutor();
    private final Process process = mock(Process.class);
    private final CmdExecutor innerCmdExecutor = mock(CmdExecutor.class);
    private final AsyncCmdExecutor asyncCmdExecutor = new PlainAsyncCmdExecutor(executor, innerCmdExecutor);

    @Before
    public void setUp() throws InterruptedException {
        when(process.waitFor()).thenReturn(OK_EXIT_CODE);
        when(process.isAlive()).thenReturn(false);
        when(process.getInputStream()).thenReturn(IOUtils.toInputStream(STD_OUT));
        when(process.getErrorStream()).thenReturn(IOUtils.toInputStream(STD_ERR));
        when(innerCmdExecutor.launchCommand(eq(COMMAND), any(), any())).thenReturn(process);
    }

    @Test
    public void executorShouldDelegateCallToInnerExecutor() {
        asyncCmdExecutor.launchCommand(COMMAND).get();

        verify(innerCmdExecutor).launchCommand(eq(COMMAND), eq(Collections.emptyMap()), eq(null));
    }

    @Test
    public void executorShouldCollectProcessStdOutAsResult() {
        final String actualOut = asyncCmdExecutor.launchCommand(COMMAND).get();

        assertThat(actualOut, is(STD_OUT + LINE_BREAK));
    }

    @Test
    public void executorShouldCollectProcessStdErrAsExceptionContentOnFailure() throws InterruptedException {
        when(process.waitFor()).thenReturn(BAD_EXIT_CODE);
        final Throwable throwable = asyncCmdExecutor.launchCommand(COMMAND).with(exceptionallyResult()).get();
        assertThat(throwable.getMessage(), containsString(STD_ERR + LINE_BREAK));
    }

    @Test
    public void cancelShouldKillUnderlyingProcess() throws InterruptedException, IOException {
        final Process process = Runtime.getRuntime().exec(SLEEP_5);
        when(innerCmdExecutor.launchCommand(eq(COMMAND), any(), any()))
                .thenReturn(process);
        final Execution<String> execution = asyncCmdExecutor.launchCommand(COMMAND);
        Thread.sleep(SECOND);
        assertTrue(process.isAlive());
        assertFalse(execution.isDone());
        execution.cancel();
        Thread.sleep(SECOND);
        assertFalse(process.isAlive());
        assertTrue(execution.isDone());
    }

    private Function<CompletableFuture<String>, CompletableFuture<Throwable>> exceptionallyResult() {
        return result -> result.handle((s, error) ->
                error != null ? error : new CmdExecutionException("Execution should fail on bad exit code"));
    }
}
