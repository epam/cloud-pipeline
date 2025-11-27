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

package com.epam.pipeline.elasticsearch.model.v7.common.xcontent;

import com.epam.pipeline.elasticsearch.model.XContentBuilder;
import lombok.Getter;
import shaded.org.elasticsearch7.common.Strings;
import shaded.org.elasticsearch7.common.xcontent.XContentFactory;

import java.io.IOException;

@Getter
public class XContentBuilderV7 implements XContentBuilder {

    private final shaded.org.elasticsearch7.common.xcontent.XContentBuilder inner;

    public XContentBuilderV7() throws IOException {
        inner = XContentFactory.jsonBuilder();
    }

    @Override
    public XContentBuilder startObject() throws IOException {
        inner.startObject();
        return this;
    }

    @Override
    public XContentBuilder endObject() throws IOException {
        inner.endObject();
        return this;
    }

    @Override
    public XContentBuilder startArray(final String name) throws IOException {
        inner.startArray(name);
        return this;
    }

    @Override
    public XContentBuilder endArray() throws IOException {
        inner.endArray();
        return this;
    }

    @Override
    public XContentBuilder field(final String name) throws IOException {
        inner.field(name);
        return this;
    }

    @Override
    public XContentBuilder field(final String name, final String value) throws IOException {
        inner.field(name, value);
        return this;
    }

    @Override
    public XContentBuilder field(final String name, final Long value) throws IOException {
        inner.field(name, value);
        return this;
    }

    @Override
    public XContentBuilder field(final String name, final boolean value) throws IOException {
        inner.field(name, value);
        return this;
    }

    @Override
    public XContentBuilder field(final String name, final Integer value) throws IOException {
        inner.field(name, value);
        return this;
    }

    @Override
    public XContentBuilder field(final String name, final Iterable<?> values) throws IOException {
        inner.field(name, values);
        return this;
    }

    @Override
    public XContentBuilder field(final String name, final Object value) throws IOException {
        inner.field(name, value);
        return this;
    }

    @Override
    public XContentBuilder array(final String name, final String... values) throws IOException {
        inner.array(name, values);
        return this;
    }

    @Override
    public XContentBuilder array(final String name, final Object... values) throws IOException {
        inner.array(name, values);
        return this;
    }

    @Override
    public void close() {
        inner.close();
    }

    @Override
    public String toString() {
        return Strings.toString(inner);
    }
}
