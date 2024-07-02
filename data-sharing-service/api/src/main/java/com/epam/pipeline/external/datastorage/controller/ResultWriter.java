/*
 * Copyright 2022 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.external.datastorage.controller;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.util.function.Consumer;


@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class ResultWriter {

    @Getter
    private final String name;
    private final IOConsumer<OutputStream> consumer;

    public static ResultWriter checked(final String name, final IOConsumer<OutputStream> consumer) {
        return new ResultWriter(name, consumer);
    }

    public static ResultWriter unchecked(final String name, final Consumer<OutputStream> consumer) {
        return checked(name, consumer::accept);
    }

    public void write(final HttpServletResponse response) throws IOException {
        consumer.accept(response.getOutputStream());
    }

    public interface IOConsumer<T> {
        void accept(T t) throws IOException;
    }

}
