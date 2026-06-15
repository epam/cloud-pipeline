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

package com.epam.pipeline.elasticsearch.model.v7.search;

import com.epam.pipeline.elasticsearch.model.SearchHit;
import lombok.RequiredArgsConstructor;
import org.opensearch.client.opensearch.core.search.Hit;

import java.util.Map;

@RequiredArgsConstructor
public class SearchHitV7 implements SearchHit {

    private final Hit<Map<String, Object>> inner;

    @Override
    public String getIndex() {
        return inner.index();
    }

    @Override
    public String getId() {
        return inner.id();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> getSourceAsMap() {
        return inner.source();
    }
}
