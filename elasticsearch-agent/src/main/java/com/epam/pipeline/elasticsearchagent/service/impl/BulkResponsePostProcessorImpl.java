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
package com.epam.pipeline.elasticsearchagent.service.impl;

import com.epam.pipeline.elasticsearchagent.dao.PipelineEventDao;
import com.epam.pipeline.elasticsearchagent.model.PipelineEvent;
import com.epam.pipeline.elasticsearchagent.service.BulkResponsePostProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
@Slf4j
public class BulkResponsePostProcessorImpl implements BulkResponsePostProcessor {

    private final PipelineEventDao pipelineEventDao;

    @Override
    public void postProcessResponse(final List<BulkItemResponse> items,
                                    final List<PipelineEvent.ObjectType> objectTypes,
                                    final Long entityId,
                                    final LocalDateTime syncStart) {
        final List<BulkItemResponse> failedItems = items.stream()
                .filter(BulkItemResponse::isFailed)
                .collect(Collectors.toList());
        if (CollectionUtils.isNotEmpty(failedItems)) {
            failedItems
                    .forEach(item ->
                            log.error("Failed to index item {}: {}", item.getId(), item.getFailureMessage()));
        } else {
            objectTypes.forEach(type -> pipelineEventDao.deleteEventByObjectId(entityId, type, syncStart));
        }
    }
}
