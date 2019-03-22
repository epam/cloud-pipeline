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

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Cancellable execution.
 *
 * @param <T> Execution result type.
 */
public interface Execution<T> {

    /**
     * Cancels the running execution.
     *
     * Do nothing if the execution has finished or was cancelled previously.
     */
    void cancel();

    /**
     * Returns new execution with the updated result type.
     *
     * @param func function that transforms current result type to a different one.
     * @param <U> New execution result type.
     * @return Execution with the updated result type.
     */
    <U> Execution<U> with(Function<CompletableFuture<T>, CompletableFuture<U>> func);

    /**
     * Waits for the execution to end.
     *
     * Method is blocking.
     *
     * @return execution result.
     */
    T get();

    /**
     * Checks if the current execution has finished.
     *
     * @return true if the execution has finished.
     */
    boolean isDone();
}
