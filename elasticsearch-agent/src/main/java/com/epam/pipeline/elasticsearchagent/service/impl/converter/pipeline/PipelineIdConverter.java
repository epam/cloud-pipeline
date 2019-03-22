/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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
package com.epam.pipeline.elasticsearchagent.service.impl.converter.pipeline;

import com.epam.pipeline.elasticsearchagent.service.ResponseIdConverter;
import com.epam.pipeline.elasticsearchagent.utils.Utils;
import lombok.RequiredArgsConstructor;
import org.elasticsearch.action.bulk.BulkItemResponse;

import java.util.Arrays;

@RequiredArgsConstructor
public class PipelineIdConverter implements ResponseIdConverter {

    private final String pipelineIndexName;
    private final String pipelineCodeIndexPrefix;

    @Override
    public Long getId(final BulkItemResponse response) {
        final String index = response.getIndex();
        if (pipelineIndexName.equals(index)) {
            return ResponseIdConverter.super.getId(response);
        }
        if (index.startsWith(pipelineCodeIndexPrefix)) {
            final String id = Utils.last(Arrays.asList(index.split("-")));
            return Long.parseLong(id);
        }
        throw new IllegalArgumentException(
                String.format("Cannot parse id for item {} index {}", response.getId(), index));
    }
}
