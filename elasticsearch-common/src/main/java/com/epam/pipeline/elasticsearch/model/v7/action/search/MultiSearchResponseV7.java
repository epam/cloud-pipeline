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

import com.epam.pipeline.elasticsearch.model.MultiSearchResponseInner;
import com.epam.pipeline.elasticsearch.model.SearchResponse;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@RequiredArgsConstructor
public class MultiSearchResponseV7 implements MultiSearchResponseInner {

    @RequiredArgsConstructor
    public static class Item implements MultiSearchResponseInner.Item {

        private final shaded.org.elasticsearch.v7.action.search.MultiSearchResponse.Item inner;

        @Override
        public SearchResponse getResponse() {
            return new SearchResponseV7(inner.getResponse());
        }
    }

    private final shaded.org.elasticsearch.v7.action.search.MultiSearchResponse inner;

    @Override
    public MultiSearchResponseInner.Item[] getResponses() {
        return Arrays.stream(inner.getResponses())
                .map(MultiSearchResponseV7.Item::new)
                .toArray(MultiSearchResponseInner.Item[]::new);
    }
}
