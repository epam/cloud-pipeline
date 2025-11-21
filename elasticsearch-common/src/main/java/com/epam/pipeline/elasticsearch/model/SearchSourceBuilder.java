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
import com.epam.pipeline.elasticsearch.model.v6.search.builder.SearchSourceBuilderV6;
import com.epam.pipeline.elasticsearch.model.v7.search.builder.SearchSourceBuilderV7;
import lombok.Getter;

@Getter
public class SearchSourceBuilder {

    private final SearchSourceBuilderInner inner;

    public SearchSourceBuilder(final ElasticStackVersion version) {
        if (ElasticStackVersion.V7.equals(version)) {
            inner = new SearchSourceBuilderV7();
        } else {
            inner = new SearchSourceBuilderV6();
        }
    }

    public SearchSourceBuilder query(final QueryBuilder queryBuilder) {
        inner.query(queryBuilder);
        return this;
    }

    public SearchSourceBuilder size(final int size) {
        inner.size(size);
        return this;
    }
}
