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

package com.epam.pipeline.elasticsearch.model.v6.search;

import com.epam.pipeline.elasticsearch.model.SearchHit;
import com.epam.pipeline.elasticsearch.model.SearchHits;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@RequiredArgsConstructor
public class SearchHitsV6 implements SearchHits {

    private final org.elasticsearch.search.SearchHits inner;

    @Override
    public long getTotalHits() {
        return inner.getTotalHits();
    }

    @Override
    public SearchHit getAt(final int position) {
        return new SearchHitV6(inner.getAt(position));
    }

    @Override
    public SearchHit[] getHits() {
        return Arrays.stream(inner.getHits())
                .map(SearchHitV6::new)
                .toArray(SearchHit[]::new);
    }
}
