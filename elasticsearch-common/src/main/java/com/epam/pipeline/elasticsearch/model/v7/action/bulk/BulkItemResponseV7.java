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

import com.epam.pipeline.elasticsearch.model.BulkItemResponse;
import lombok.RequiredArgsConstructor;
import org.opensearch.client.opensearch.core.bulk.BulkResponseItem;

@RequiredArgsConstructor
public class BulkItemResponseV7 implements BulkItemResponse {

    private final BulkResponseItem response;

    @Override
    public boolean isFailed() {
        return response.error() != null;
    }

    @Override
    public String getId() {
        return response.id();
    }

    @Override
    public String getFailureMessage() {
        return response.error() != null ? response.error().reason() : null;
    }

    @Override
    public String getIndex() {
        return response.index();
    }
}
