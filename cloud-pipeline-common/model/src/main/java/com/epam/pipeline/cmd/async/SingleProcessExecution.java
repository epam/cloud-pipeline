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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Execution that kills the underlying process on cancel.
 *
 * @param <T> Execution result type.
 */
@Slf4j
@RequiredArgsConstructor
public class SingleProcessExecution<T> implements Execution<T> {

    protected final Process process;
    protected final CompletableFuture<T> result;

    @Override
    public void cancel() {
        if (process.isAlive()) {
            process.destroy();
        }
        try {
            final boolean finished = process.waitFor(30L, TimeUnit.SECONDS);
            if (!finished) {
                throw new CmdExecutionException("Cmd execution didn't stop after process destroying");
            }
        } catch (InterruptedException e) {
            throw new CmdExecutionException("Cmd execution didn't stop after process destroying");
        }
    }

    @Override
    public <U> Execution<U> with(Function<CompletableFuture<T>, CompletableFuture<U>> func) {
        return new SingleProcessExecution<>(process, func.apply(result));
    }

    @Override
    public T get() {
        try {
            return result.get();
        } catch (InterruptedException e) {
            throw new CmdExecutionException("Thread was interrupted while waiting for result to appear", e);
        } catch (ExecutionException e) {
            throw new CmdExecutionException("Current result execution was aborted", e);
        }
    }

    @Override
    public boolean isDone() {
        return result.isDone();
    }
}
