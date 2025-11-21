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
import com.epam.pipeline.elasticsearch.model.v6.action.search.SearchRequestV6;
import com.epam.pipeline.elasticsearch.model.v7.action.search.SearchRequestV7;
import lombok.Getter;

@Getter
public class SearchRequest {

    private final SearchRequestInner inner;

    public SearchRequest(final ElasticStackVersion version, final String... indices) {
        if (ElasticStackVersion.V7.equals(version)) {
            this.inner = new SearchRequestV7(indices);
        } else {
            this.inner = new SearchRequestV6(indices);
        }
    }

    public SearchRequest indicesOptions() {
        inner.indicesOptions();
        return this;
    }

    public SearchRequest source(final SearchSourceBuilder sourceBuilder) {
        inner.source(sourceBuilder);
        return this;
    }

    public SearchRequest scroll() {
        inner.scroll();
        return this;
    }
}
