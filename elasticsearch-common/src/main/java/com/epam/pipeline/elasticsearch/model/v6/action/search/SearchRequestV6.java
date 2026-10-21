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

package com.epam.pipeline.elasticsearch.model.v6.action.search;

import com.epam.pipeline.elasticsearch.model.IndicesOptionsInner;
import com.epam.pipeline.elasticsearch.model.SearchRequestInner;
import com.epam.pipeline.elasticsearch.model.SearchSourceBuilder;
import com.epam.pipeline.elasticsearch.model.v6.action.support.IndicesOptionsV6;
import com.epam.pipeline.elasticsearch.model.v6.search.builder.SearchSourceBuilderV6;
import lombok.Getter;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.search.Scroll;

@Getter
public class SearchRequestV6 implements SearchRequestInner {
    private static final Scroll TIME_SCROLL = new Scroll(new TimeValue(60000));

    private final org.elasticsearch.action.search.SearchRequest inner;

    public SearchRequestV6(final String... indices) {
        inner = new org.elasticsearch.action.search.SearchRequest(indices);
    }

    @Override
    public SearchRequestV6 indicesOptions(final IndicesOptionsInner indicesOptions) {
        inner.indicesOptions(((IndicesOptionsV6) indicesOptions).getInner());
        return this;
    }

    @Override
    public SearchRequestV6 source(final SearchSourceBuilder sourceBuilder) {
        inner.source(((SearchSourceBuilderV6) sourceBuilder.getInner()).getInner());
        return this;
    }

    @Override
    public SearchRequestInner scroll() {
        inner.scroll(TIME_SCROLL);
        return this;
    }
}
