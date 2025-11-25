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

package com.epam.pipeline.elasticsearch.model.v7.action.search;

import com.epam.pipeline.elasticsearch.model.IndicesOptionsInner;
import com.epam.pipeline.elasticsearch.model.SearchRequestInner;
import com.epam.pipeline.elasticsearch.model.SearchSourceBuilder;
import com.epam.pipeline.elasticsearch.model.v7.action.support.IndicesOptionsV7;
import com.epam.pipeline.elasticsearch.model.v7.search.builder.SearchSourceBuilderV7;
import lombok.Getter;
import shaded.org.elasticsearch.v7.common.unit.TimeValue;
import shaded.org.elasticsearch.v7.search.Scroll;

@Getter
public class SearchRequestV7 implements SearchRequestInner {

    private static final Scroll TIME_SCROLL = new Scroll(new TimeValue(60000));

    private final shaded.org.elasticsearch.v7.action.search.SearchRequest inner;

    public SearchRequestV7(final String... indices) {
        inner = new shaded.org.elasticsearch.v7.action.search.SearchRequest(indices);
    }

    @Override
    public SearchRequestV7 indicesOptions(final IndicesOptionsInner indicesOptions) {
        inner.indicesOptions(((IndicesOptionsV7) indicesOptions).getInner());
        return this;
    }

    @Override
    public SearchRequestV7 source(final SearchSourceBuilder sourceBuilder) {
        inner.source(((SearchSourceBuilderV7) sourceBuilder.getInner()).getInner());
        return this;
    }

    @Override
    public SearchRequestV7 scroll() {
        inner.scroll(TIME_SCROLL);
        return this;
    }
}
