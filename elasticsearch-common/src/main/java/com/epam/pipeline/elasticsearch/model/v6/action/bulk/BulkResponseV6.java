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

package com.epam.pipeline.elasticsearch.model.v6.action.bulk;

import com.epam.pipeline.elasticsearch.model.BulkResponse;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.jackson.Jacksonized;

import java.util.Arrays;

@RequiredArgsConstructor
@Builder
@Jacksonized
public class BulkResponseV6 implements BulkResponse {
    private final org.elasticsearch.action.bulk.BulkResponse response;

    @Override
    public BulkItemResponseV6[] getItems() {
        return Arrays.stream(response.getItems())
                .map(BulkItemResponseV6::new)
                .toArray(BulkItemResponseV6[]::new);
    }
}
