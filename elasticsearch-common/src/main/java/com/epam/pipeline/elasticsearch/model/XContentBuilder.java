/*
 * Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.elasticsearch.model;

import java.io.IOException;

public interface XContentBuilder extends AutoCloseable {

    XContentBuilder field(String name) throws IOException;

    XContentBuilder field(String name, String value) throws IOException;

    XContentBuilder field(String name, Long value) throws IOException;

    XContentBuilder field(String name, boolean value) throws IOException;

    XContentBuilder field(String name, Integer value) throws IOException;

    XContentBuilder field(String name, Iterable<?> values) throws IOException;

    XContentBuilder field(String name, Object value) throws IOException;

    XContentBuilder array(String name, String... values) throws IOException;

    XContentBuilder array(String name, Object... values) throws IOException;

    XContentBuilder startObject() throws IOException;

    XContentBuilder endObject() throws IOException;

    XContentBuilder startArray(String name) throws IOException;

    XContentBuilder endArray() throws IOException;
}
