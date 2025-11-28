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
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.epam.pipeline.elasticsearchagent.service.ElasticsearchSynchronizer.DOC_TYPE_FIELD;

@Component
public class ConfigurationEntryMapper implements EntityMapper<ConfigurationEntryDoc> {

    @Override
    public Map<String, ?> map(final EntityContainer<ConfigurationEntryDoc> container) {
        return getContentBuilder(container);
    }

    private Map<String, ?> getContentBuilder(final EntityContainer<ConfigurationEntryDoc> container) {
        final RunConfiguration configuration = container.getEntity().getConfiguration();
        final Map<String, Object> jsonMap = new HashMap<>();
        final AbstractRunConfigurationEntry entry = container.getEntity().getEntry();

        jsonMap.put(DOC_TYPE_FIELD, SearchDocumentType.CONFIGURATION.name());
        jsonMap.put("id", container.getEntity().getId());
        jsonMap.put("name", Optional.ofNullable(entry)
                .map(AbstractRunConfigurationEntry::getName)
                .orElse(configuration.getName()));
        jsonMap.put("description", configuration.getName() + " " +
                StringUtils.defaultIfBlank(configuration.getDescription(), StringUtils.EMPTY));
        jsonMap.put("createdDate", parseDataToString(configuration.getCreatedDate()));
        jsonMap.put("parentId", Optional.ofNullable(configuration.getParent())
                .map(BaseEntity::getId)
                .orElse(null));

        buildUserContent(container.getOwner(), jsonMap);
        buildMetadata(container.getMetadata(), jsonMap);
        buildPermissions(container.getPermissions(), jsonMap);

        if (entry != null) {
            buildConfigurationEntry(entry, container.getEntity().getPipeline(), jsonMap);
        }
        return jsonMap;
    }

    private void buildConfigurationEntry(final AbstractRunConfigurationEntry entry,
                                         final Pipeline pipeline,
                                         final Map<String, Object> jsonMap) {
        if (entry == null) {
            return;
        }

        jsonMap.put("environment", entry.getExecutionEnvironment().name());
        jsonMap.put("entryName", entry.getName());
        jsonMap.put("rootEntityId", entry.getRootEntityId());
        jsonMap.put("configName", entry.getConfigName());
        jsonMap.put("defaultConfiguration", entry.isDefaultConfiguration());

        buildExecutionEnvironmentConfiguration(entry, pipeline, jsonMap);
    }

    private void buildExecutionEnvironmentConfiguration(final AbstractRunConfigurationEntry entry,
                                                        final Pipeline pipeline,
                                                        final Map<String, Object> jsonMap) {
        if (entry.getExecutionEnvironment() == ExecutionEnvironment.CLOUD_PLATFORM ||
                entry.getExecutionEnvironment() == ExecutionEnvironment.DTS) {
            final PipelineStart pipelineStart = entry.toPipelineStart();
            jsonMap.put("pipelineId", pipelineStart.getPipelineId());
            jsonMap.put("pipelineVersion", pipelineStart.getVersion());
            jsonMap.put("dockerImage", pipelineStart.getDockerImage());

            if (pipeline != null) {
                jsonMap.put("pipelineName", pipeline.getName());
            }
        } else if (entry.getExecutionEnvironment() == ExecutionEnvironment.FIRECLOUD) {
            final FirecloudRunConfigurationEntry firecloudEntry = (FirecloudRunConfigurationEntry) entry;
            jsonMap.put("methodName", firecloudEntry.getMethodName());
            jsonMap.put("methodSnapshot", firecloudEntry.getMethodSnapshot());
            jsonMap.put("methodConfigurationName", firecloudEntry.getMethodConfigurationName());
            jsonMap.put("methodConfigurationSnapshot", firecloudEntry.getMethodConfigurationSnapshot());
        }
    }
}

