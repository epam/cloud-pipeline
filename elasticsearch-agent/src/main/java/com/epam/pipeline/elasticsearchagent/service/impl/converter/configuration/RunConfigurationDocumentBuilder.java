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
package com.epam.pipeline.elasticsearchagent.service.impl.converter.configuration;

import com.epam.pipeline.elasticsearchagent.model.ConfigurationEntryDoc;
import com.epam.pipeline.elasticsearchagent.model.EntityContainer;
import com.epam.pipeline.elasticsearchagent.model.RunConfigurationDoc;
import com.epam.pipeline.entity.configuration.AbstractRunConfigurationEntry;
import com.epam.pipeline.entity.configuration.RunConfiguration;
import com.epam.pipeline.entity.pipeline.Pipeline;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.DocWriteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.epam.pipeline.elasticsearchagent.service.EventToRequestConverter.INDEX_TYPE;

@Component
@RequiredArgsConstructor
public class RunConfigurationDocumentBuilder {

    public static final String ID_DELIMITER = "-";
    private final ConfigurationEntryMapper entryMapper;

    public List<DocWriteRequest> createDocsForConfiguration(final String indexName,
                                                            final EntityContainer<RunConfigurationDoc> configuration) {
        List<EntityContainer<ConfigurationEntryDoc>> entries = splitIntoEntryDocs(configuration);

        return entries.stream().map(entry ->
                new IndexRequest(indexName, INDEX_TYPE, entry.getEntity().getId())
                        .source(entryMapper.map(entry)))
                .collect(Collectors.toList());
    }

    private List<EntityContainer<ConfigurationEntryDoc>> splitIntoEntryDocs(
            final EntityContainer<RunConfigurationDoc> configurationDoc) {
        RunConfiguration configuration = configurationDoc.getEntity().getConfiguration();
        if (CollectionUtils.isEmpty(configuration.getEntries())) {
            return Collections.singletonList(
                    convertEntryToDoc(configurationDoc, null));
        }
        return configuration.getEntries().stream()
                .map(entry -> convertEntryToDoc(configurationDoc, entry))
                .collect(Collectors.toList());
    }

    private EntityContainer<ConfigurationEntryDoc> convertEntryToDoc(
            final EntityContainer<RunConfigurationDoc> configurationDoc,
            final AbstractRunConfigurationEntry entry) {
        ConfigurationEntryDoc doc = ConfigurationEntryDoc.builder()
                .id(buildConfigEntryId(configurationDoc, entry))
                .configuration(configurationDoc.getEntity().getConfiguration())
                .entry(entry)
                .pipeline(findPipeline(entry, configurationDoc.getEntity().getPipelines()))
                .build();
        return buildContainer(configurationDoc, doc);
    }

    private Pipeline findPipeline(final AbstractRunConfigurationEntry entry,
                                  final List<Pipeline> pipelines) {
        if (CollectionUtils.isEmpty(pipelines)) {
            return null;
        }
        return Optional.ofNullable(entry)
                .map(AbstractRunConfigurationEntry::toPipelineStart)
                .flatMap(start -> Optional.ofNullable(start.getPipelineId()))
                .flatMap(pipelineId -> pipelines.stream()
                        .filter(pipeline -> pipelineId.equals(pipeline.getId()))
                        .findFirst())
                .orElse(null);
    }

    private String buildConfigEntryId(final EntityContainer<RunConfigurationDoc> configurationDoc,
                                      final AbstractRunConfigurationEntry entry) {
        final String id = configurationDoc.getEntity().getConfiguration().getId() + ID_DELIMITER;
        if (Objects.isNull(entry) || StringUtils.isBlank(entry.getName())) {
            return id;
        }
        return id + entry.getName();
    }

    private EntityContainer<ConfigurationEntryDoc> buildContainer(
            final EntityContainer<RunConfigurationDoc> configurationDoc,
            final ConfigurationEntryDoc entryDoc) {
        return EntityContainer.<ConfigurationEntryDoc>builder()
                .entity(entryDoc)
                .owner(configurationDoc.getOwner())
                .metadata(configurationDoc.getMetadata())
                .permissions(configurationDoc.getPermissions())
                .build();
    }
}
