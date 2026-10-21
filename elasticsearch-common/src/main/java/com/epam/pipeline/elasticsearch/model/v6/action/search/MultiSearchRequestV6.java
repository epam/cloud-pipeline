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

import com.epam.pipeline.elasticsearch.model.MultiSearchRequestInner;
import com.epam.pipeline.elasticsearch.model.SearchRequest;
import lombok.Getter;

@Getter
public class MultiSearchRequestV6 implements MultiSearchRequestInner {

    private final org.elasticsearch.action.search.MultiSearchRequest inner;

    public MultiSearchRequestV6() {
        inner = new org.elasticsearch.action.search.MultiSearchRequest();
    }

    @Override
    public MultiSearchRequestInner add(final SearchRequest request) {
        inner.add(((SearchRequestV6) request.getInner()).getInner());
        return this;
    }
}
