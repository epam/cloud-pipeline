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

import com.epam.pipeline.elasticsearch.ElasticStackVersion;
import com.epam.pipeline.elasticsearch.model.v6.action.index.IndexRequestV6;
import com.epam.pipeline.elasticsearch.model.v7.action.index.IndexRequestV7;
import lombok.Getter;

import java.util.Map;

@SuppressWarnings("PMD.ShortMethodName")
@Getter
public class IndexRequest implements DocWriteRequest {

    private final IndexRequestInner inner;

    public IndexRequest(final String indexName, final String type, final ElasticStackVersion version) {
        if (ElasticStackVersion.V7.equals(version)) {
            inner = new IndexRequestV7(indexName);
        } else {
            inner = new IndexRequestV6(indexName, type);
        }
    }

    public IndexRequest(final String indexName, final String type, final String objectId,
                        final ElasticStackVersion version) {
        if (ElasticStackVersion.V7.equals(version)) {
            inner = new IndexRequestV7(indexName, objectId);
        } else {
            inner = new IndexRequestV6(indexName, type, objectId);
        }
    }

    public IndexRequest source(final Map<String, ?> content) {
        inner.source(content);
        return this;
    }

    public IndexRequest id(final String id) {
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
