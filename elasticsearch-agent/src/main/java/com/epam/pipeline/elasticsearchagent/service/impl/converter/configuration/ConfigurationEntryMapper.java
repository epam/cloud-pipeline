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
import com.epam.pipeline.elasticsearchagent.service.EntityMapper;
import com.epam.pipeline.entity.BaseEntity;
import com.epam.pipeline.entity.configuration.AbstractRunConfigurationEntry;
import com.epam.pipeline.entity.configuration.ExecutionEnvironment;
import com.epam.pipeline.entity.configuration.FirecloudRunConfigurationEntry;
import com.epam.pipeline.entity.configuration.RunConfiguration;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.run.PipelineStart;
import com.epam.pipeline.entity.search.SearchDocumentType;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

import static com.epam.pipeline.elasticsearchagent.service.ElasticsearchSynchronizer.DOC_TYPE_FIELD;

@Component
@RequiredArgsConstructor
public class ConfigurationEntryMapper implements EntityMapper<ConfigurationEntryDoc> {

    @Override
    public XContentBuilder map(final EntityContainer<ConfigurationEntryDoc> container) {
        return getContentBuilder(container);
    }

    private XContentBuilder getContentBuilder(final EntityContainer<ConfigurationEntryDoc> container) {
        RunConfiguration configuration = container.getEntity().getConfiguration();
        try (XContentBuilder jsonBuilder = XContentFactory.jsonBuilder()) {
            jsonBuilder.startObject();
            AbstractRunConfigurationEntry entry = container.getEntity().getEntry();
            jsonBuilder
                    .field(DOC_TYPE_FIELD, SearchDocumentType.CONFIGURATION.name())
                    .field("id", container.getEntity().getId())
                    .field("name", Optional.ofNullable(entry)
                            .map(AbstractRunConfigurationEntry::getName)
                            .orElse(configuration.getName()))
                    .field("description", configuration.getName() + " " +
                            StringUtils.defaultIfBlank(configuration.getDescription(), StringUtils.EMPTY))
                    .field("createdDate", parseDataToString(configuration.getCreatedDate()))
                    .field("parentId", Optional.ofNullable(configuration.getParent())
                            .map(BaseEntity::getId)
                            .orElse(null));

            buildUserContent(container.getOwner(), jsonBuilder);
            buildMetadata(container.getMetadata(), jsonBuilder);
            buildOntologies(container.getOntologies(), jsonBuilder);
            buildPermissions(container.getPermissions(), jsonBuilder);

            if (entry != null) {
                buildConfigurationEntry(entry,
                        container.getEntity().getPipeline(), jsonBuilder);
            }

            jsonBuilder.endObject();
            return jsonBuilder;
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to create elasticsearch document for run configuration: ", e);
        }
    }

    private void buildConfigurationEntry(final AbstractRunConfigurationEntry entry,
                                         final Pipeline pipeline,
                                         final XContentBuilder jsonBuilder) throws IOException {
        if (entry == null) {
            return;
        }

        jsonBuilder
                .field("environment", entry.getExecutionEnvironment().name())
                .field("entryName", entry.getName())
                .field("rootEntityId", entry.getRootEntityId())
                .field("configName", entry.getConfigName())
                .field("defaultConfiguration", entry.isDefaultConfiguration());

        buildExecutionEnvironmentConfiguration(entry, pipeline, jsonBuilder);
    }

    private void buildExecutionEnvironmentConfiguration(final AbstractRunConfigurationEntry entry,
                                                        final Pipeline pipeline,
                                                        final XContentBuilder jsonBuilder) throws IOException {
        if (entry.getExecutionEnvironment() == ExecutionEnvironment.CLOUD_PLATFORM ||
                entry.getExecutionEnvironment() == ExecutionEnvironment.DTS) {
            PipelineStart pipelineStart = entry.toPipelineStart();
            jsonBuilder
                    .field("pipelineId", pipelineStart.getPipelineId())
                    .field("pipelineVersion", pipelineStart.getVersion())
                    .field("dockerImage", pipelineStart.getDockerImage());

            if (pipeline != null) {
                jsonBuilder.field("pipelineName", pipeline.getName());
            }
        } else if (entry.getExecutionEnvironment() == ExecutionEnvironment.FIRECLOUD) {
            FirecloudRunConfigurationEntry firecloudEntry = (FirecloudRunConfigurationEntry) entry;
            jsonBuilder
                    .field("methodName", firecloudEntry.getMethodName())
                    .field("methodSnapshot", firecloudEntry.getMethodSnapshot())
                    .field("methodConfigurationName", firecloudEntry.getMethodConfigurationName())
                    .field("methodConfigurationSnapshot", firecloudEntry.getMethodConfigurationSnapshot());
        }
    }
}

