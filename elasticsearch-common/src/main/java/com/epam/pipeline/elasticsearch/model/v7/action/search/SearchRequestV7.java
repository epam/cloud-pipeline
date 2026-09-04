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
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchRequest;

import java.util.Arrays;
import java.util.List;

public class SearchRequestV7 implements SearchRequestInner {

    private static final String DEFAULT_SCROLL_KEEPALIVE = "1m";

    private final List<String> indices;
    private Boolean allowNoIndices;
    private Boolean ignoreUnavailable;
    private Query query;
    private Integer size;
    private String scrollKeepAlive;

    public SearchRequestV7(final String... indices) {
        this.indices = Arrays.asList(indices);
    }

    @Override
    public SearchRequestV7 indicesOptions(final IndicesOptionsInner indicesOptions) {
        final IndicesOptionsV7 opts = (IndicesOptionsV7) indicesOptions;
        this.allowNoIndices = opts.isAllowNoIndices();
        this.ignoreUnavailable = opts.isIgnoreUnavailable();
        return this;
    }

    @Override
    public SearchRequestV7 source(final SearchSourceBuilder sourceBuilder) {
        final SearchSourceBuilderV7 inner = (SearchSourceBuilderV7) sourceBuilder.getInner();
        this.query = inner.getQuery();
        this.size = inner.getSize();
        return this;
    }

    @Override
    public SearchRequestV7 scroll() {
        this.scrollKeepAlive = DEFAULT_SCROLL_KEEPALIVE;
        return this;
    }

    public SearchRequest build() {
        return SearchRequest.of(b -> {
            b.index(indices);
            if (allowNoIndices != null) {
                b.allowNoIndices(allowNoIndices);
            }
            if (ignoreUnavailable != null) {
                b.ignoreUnavailable(ignoreUnavailable);
            }
            if (query != null) {
                b.query(query);
            }
            if (size != null) {
                b.size(size);
            }
            if (scrollKeepAlive != null) {
                b.scroll(t -> t.time(scrollKeepAlive));
            }
            return b;
        });
    }
}
