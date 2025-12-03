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

package com.epam.pipeline.elasticsearch.model.v7.search.builder;

import com.epam.pipeline.elasticsearch.model.QueryBuilder;
import com.epam.pipeline.elasticsearch.model.SearchSourceBuilderInner;
import lombok.Getter;

@Getter
public class SearchSourceBuilderV7 implements SearchSourceBuilderInner {

    private final shaded.org.opensearch.search.builder.SearchSourceBuilder inner;

    public SearchSourceBuilderV7() {
        inner = new shaded.org.opensearch.search.builder.SearchSourceBuilder();
    }

    @Override
    public SearchSourceBuilderV7 query(final QueryBuilder queryBuilder) {
        inner.query((shaded.org.opensearch.index.query.QueryBuilder) queryBuilder.getInner());
        return this;
    }

    @Override
    public SearchSourceBuilderV7 size(final int size) {
        inner.size(size);
        return this;
    }
}
