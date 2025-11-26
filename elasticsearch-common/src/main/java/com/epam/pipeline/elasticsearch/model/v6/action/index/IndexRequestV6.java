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

package com.epam.pipeline.elasticsearch.model.v6.action.index;

import com.epam.pipeline.elasticsearch.model.IndexRequestInner;
import com.epam.pipeline.elasticsearch.model.XContentBuilder;
import com.epam.pipeline.elasticsearch.model.v6.common.xcontent.XContentBuilderV6;
import lombok.Getter;
import org.elasticsearch.action.index.IndexRequest;

@Getter
@SuppressWarnings("PMD.ShortMethodName")
public class IndexRequestV6 implements IndexRequestInner {

    private final IndexRequest inner;

    public IndexRequestV6(final String indexName, final String type) {
        this.inner = new IndexRequest(indexName, type);
    }

    public IndexRequestV6(final String indexName, final String type, final String objectId) {
        this.inner = new IndexRequest(indexName, type, objectId);
    }

    @Override
    public IndexRequestInner source(final XContentBuilder content) {
        inner.source(((XContentBuilderV6) content).getInner()); // TODO: cast graceful?
        return this;
    }

    @Override
    public IndexRequestInner id(final String id) {
        inner.id(id);
        return this;
    }

    @Override
    public String id() {
        return inner.id();
    }

    @Override
    public String index() {
        return inner.index();
    }

    @Override
    public String type() {
        return inner.type();
    }
}
