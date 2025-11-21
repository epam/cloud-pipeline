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
package com.epam.pipeline.elasticsearchagent.service.impl.converter.metadata;

import com.epam.pipeline.elasticsearch.client.ElasticsearchServiceClient;
import com.epam.pipeline.elasticsearch.model.XContentBuilder;
import com.epam.pipeline.elasticsearch.model.XContentFactory;
import com.epam.pipeline.elasticsearchagent.model.EntityContainer;
import com.epam.pipeline.elasticsearchagent.service.EntityMapper;
import com.epam.pipeline.entity.BaseEntity;
import com.epam.pipeline.entity.metadata.MetadataClass;
import com.epam.pipeline.entity.metadata.MetadataEntity;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import com.epam.pipeline.entity.search.SearchDocumentType;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.epam.pipeline.elasticsearchagent.service.ElasticsearchSynchronizer.DOC_TYPE_FIELD;

@Component
@RequiredArgsConstructor
public class MetadataEntityMapper implements EntityMapper<MetadataEntity> {

    private final ElasticsearchServiceClient client;

    @Override
    public XContentBuilder map(final EntityContainer<MetadataEntity> container) {
        MetadataEntity entity = container.getEntity();
        try (XContentBuilder jsonBuilder = XContentFactory.getBuilder(client.getVersion())) {
            jsonBuilder.startObject();
            jsonBuilder
                    .field(DOC_TYPE_FIELD, SearchDocumentType.METADATA_ENTITY.name())
                    .field("id", entity.getId())
                    .field("externalId", entity.getExternalId())
                    .field("parentId", Optional.ofNullable(entity.getParent())
                            .map(BaseEntity::getId).orElse(null))
                    .field("name", StringUtils.defaultString(entity.getName(), entity.getExternalId()));

            buildEntityClass(entity.getClassEntity(), jsonBuilder);
            buildPermissions(container.getPermissions(), jsonBuilder);

            buildMap(prepareEntityMetadata(entity.getData()), jsonBuilder, "fields");
            jsonBuilder.endObject();
            return jsonBuilder;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to create elasticsearch document for metadata: ", e);
        }
    }

    private void buildEntityClass(final MetadataClass metadataClass,
                                  final XContentBuilder jsonBuilder) throws IOException {
        if (metadataClass == null) {
            return;
        }
        jsonBuilder
                .field("className", metadataClass.getName())
                .field("classId", metadataClass.getId());
    }

    private Map<String, String> prepareEntityMetadata(final Map<String, PipeConfValue> data) {
        if (CollectionUtils.isEmpty(data)) {
            return Collections.emptyMap();
        }
        return data.entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getValue()));
    }
}
