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
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class QsubExecution<T> implements Execution<T> {

    public static final String DEFAULT_QSUB_JOBS_KILL_COMMAND = "qdel %s";

    private final Execution<T> execution;
    private final CmdExecutor cmdExecutor;
    private final String jobName;
    private final String killQsubJobsByName;

    public QsubExecution(final Execution<T> execution, final CmdExecutor cmdExecutor, final String jobName) {
        this(execution, cmdExecutor, jobName, DEFAULT_QSUB_JOBS_KILL_COMMAND);
    }

    @Override
    public void cancel() {
        execution.cancel();
        try {
            cmdExecutor.executeCommand(String.format(killQsubJobsByName, jobName));
        } catch (CmdExecutionException e) {
            log.warn(String.format("Killing qsub jobs with name %s went bad. " +
                    "Probably where are no jobs with the specified name.", jobName), e);
        }
    }

    @Override
    public <U> Execution<U> with(final Function<CompletableFuture<T>, CompletableFuture<U>> func) {
        return new QsubExecution<>(execution.with(func), cmdExecutor, jobName);
    }

    @Override
    public T get() {
        return execution.get();
    }

    @Override
    public boolean isDone() {
        return execution.isDone();
    }
}
