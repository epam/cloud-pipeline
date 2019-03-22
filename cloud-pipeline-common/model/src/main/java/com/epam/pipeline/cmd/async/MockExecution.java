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
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class MockExecution<T> implements Execution<T> {

    private final CompletableFuture<T> result;

    @SuppressWarnings("PMD.ShortMethodName")
    public static <T> MockExecution<T> of(final T result) {
        return new MockExecution<>(CompletableFuture.completedFuture(result));
    }

    @Override
    public void cancel() {
    }

    @Override
    public <U> Execution<U> with(final Function<CompletableFuture<T>, CompletableFuture<U>> func) {
        return new MockExecution<>(func.apply(result));
    }

    @Override
    @SneakyThrows
    public T get() {
        return result.get();
    }

    @Override
    public boolean isDone() {
        return true;
    }
}
