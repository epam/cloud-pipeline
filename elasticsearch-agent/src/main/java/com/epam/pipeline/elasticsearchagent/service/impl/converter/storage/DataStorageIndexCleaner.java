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
package com.epam.pipeline.elasticsearchagent.service.impl.converter.storage;

import com.epam.pipeline.elasticsearchagent.model.EventType;
import com.epam.pipeline.elasticsearchagent.model.PipelineEvent;
import com.epam.pipeline.elasticsearchagent.service.ElasticsearchServiceClient;
import com.epam.pipeline.elasticsearchagent.service.EventProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.ElasticsearchException;
import org.springframework.util.StringUtils;

@RequiredArgsConstructor
@Slf4j
public class DataStorageIndexCleaner implements EventProcessor {

    private final String indexPrefix;
    private final String storageFileIndexName;
    private final ElasticsearchServiceClient elasticsearchClient;

    @Override
    public void process(final PipelineEvent event) {
        if (event.getEventType() != EventType.DELETE) {
            return;
        }
        deleteCorrespondingStorageIndex(event.getObjectId());
    }

    private void deleteCorrespondingStorageIndex(final Long storageId) {
        String indexAlias = getIndexAlias(storageId);
        if (!StringUtils.hasText(indexAlias)) {
            return;
        }
        try {
            String indexNameByAlias = elasticsearchClient.getIndexNameByAlias(indexAlias);
            if (StringUtils.hasText(indexNameByAlias)) {
                elasticsearchClient.deleteIndex(indexNameByAlias);
            }
        } catch (ElasticsearchException e) {
            log.warn("Failed to delete index for storage {}", storageId);
        }
    }

    private String getIndexAlias(final Long storageId) {
        return indexPrefix + storageFileIndexName + String.format("-%d", storageId);
    }
}
