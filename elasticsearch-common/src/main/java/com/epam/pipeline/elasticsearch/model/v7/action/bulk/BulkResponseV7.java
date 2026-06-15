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

package com.epam.pipeline.elasticsearch.model.v7.action.bulk;

import com.epam.pipeline.elasticsearch.model.BulkResponse;
import lombok.Builder;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Builder
public class BulkResponseV7 implements BulkResponse {

    private final org.opensearch.client.opensearch.core.BulkResponse response;

    public BulkItemResponseV7[] getItems() {
        return response.items().stream()
                .map(BulkItemResponseV7::new)
                .toArray(BulkItemResponseV7[]::new);
    }
}
