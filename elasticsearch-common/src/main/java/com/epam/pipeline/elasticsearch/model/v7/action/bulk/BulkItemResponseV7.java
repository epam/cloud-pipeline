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

@RequiredArgsConstructor
public class BulkItemResponseV7 implements BulkItemResponse {

    private final shaded.org.elasticsearch.v7.action.bulk.BulkItemResponse response;

    @Override
    public boolean isFailed() {
        return response.isFailed();
    }

    @Override
    public String getId() {
        return response.getId();
    }

    @Override
    public String getFailureMessage() {
        return response.getFailureMessage();
    }

    @Override
    public String getIndex() {
        return response.getIndex();
    }
}
