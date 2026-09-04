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

import com.epam.pipeline.elasticsearch.model.MultiSearchRequestInner;
import com.epam.pipeline.elasticsearch.model.SearchRequest;
import org.opensearch.client.opensearch.core.MsearchRequest;
import org.opensearch.client.opensearch.core.msearch.RequestItem;

import java.util.ArrayList;
import java.util.List;

public class MultiSearchRequestV7 implements MultiSearchRequestInner {

    private final List<RequestItem> searches = new ArrayList<>();

    @Override
    public MultiSearchRequestInner add(final SearchRequest request) {
        final SearchRequestV7 inner = (SearchRequestV7) request.getInner();
        final org.opensearch.client.opensearch.core.SearchRequest sr = inner.build();
        searches.add(RequestItem.of(ri -> ri
                .header(h -> h.index(sr.index()))
                .body(b -> {
                    if (sr.query() != null) {
                        b.query(sr.query());
                    }
                    if (sr.size() != null) {
                        b.size(sr.size());
                    }
                    return b;
                })
        ));
        return this;
    }

    public MsearchRequest buildMsearchRequest() {
        return MsearchRequest.of(r -> r.searches(searches));
    }
}
