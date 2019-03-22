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
import java.lang.reflect.Field;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;

/**
 * Execution that kills the underlying process and tries to kill all processes in the same process group on cancel.
 *
 * It does not fail if process PID wasn't retrieved or processes in the process group wasn't killed.
 *
 * @param <T> Execution result type.
 */
@Slf4j
public class ProcessGroupExecution<T> extends SingleProcessExecution<T> {

    public static final String DEFAULT_PROCESSES_KILL_COMMAND = "kill -TERM -- -%s";

    private final CmdExecutor cmdExecutor;
    private final String killProcessesByPGID;

    public ProcessGroupExecution(final Process process,
                                 final CompletableFuture<T> result,
                                 final CmdExecutor cmdExecutor) {
        this(process, result, cmdExecutor, DEFAULT_PROCESSES_KILL_COMMAND);
    }

    public ProcessGroupExecution(final Process process,
                                 final CompletableFuture<T> result,
                                 final CmdExecutor cmdExecutor,
                                 final String killProcessesByPGID) {
        super(process, result);
        this.cmdExecutor = cmdExecutor;
        this.killProcessesByPGID = killProcessesByPGID;
    }

    @Override
    public void cancel() {
        super.cancel();
        final long pid;
        try {
            pid = getPid();
        } catch (CmdExecutionException e) {
            log.warn("Killing all processes by PGID went bad. PID wasn't received.", e);
            return;
        }
        try {
            cmdExecutor.executeCommand(String.format(killProcessesByPGID, pid));
        } catch (CmdExecutionException e) {
            log.warn(String.format("Killing all processes by PGID %s went bad. " +
                    "Probably where are no processes with the specified PGID.", pid), e);
        }
    }

    public long getPid() {
        try {
            return getProcessPid(process);
        } catch (ReflectiveOperationException e) {
            throw new CmdExecutionException("Cmd execution process pid wasn't retrieved", e);
        }
    }

    private long getProcessPid(final Process process) throws ReflectiveOperationException {
        final Field field = process.getClass().getDeclaredField("pid");
        synchronized (field) {
            field.setAccessible(true);
            final long pid = field.getLong(process);
            field.setAccessible(false);
            return pid;
        }
    }

    @Override
    public <U> ProcessGroupExecution<U> with(final Function<CompletableFuture<T>, CompletableFuture<U>> func) {
        return new ProcessGroupExecution<>(process, func.apply(result), cmdExecutor, killProcessesByPGID);
    }
}
