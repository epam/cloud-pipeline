/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
package com.epam.pipeline.elasticsearchagent.service.impl.converter.tool;

import com.epam.pipeline.elasticsearchagent.model.EntityContainer;
import com.epam.pipeline.elasticsearchagent.model.ToolWithDescription;
import com.epam.pipeline.elasticsearchagent.service.EntityMapper;
import com.epam.pipeline.entity.docker.ToolDescription;
import com.epam.pipeline.entity.docker.ToolVersionAttributes;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.scan.ToolDependency;
import com.epam.pipeline.entity.scan.ToolVersionScanResult;
import com.epam.pipeline.entity.search.SearchDocumentType;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.epam.pipeline.elasticsearchagent.service.ElasticsearchSynchronizer.DOC_TYPE_FIELD;

@Component
public class ToolMapper implements EntityMapper<ToolWithDescription> {

    @Override
    public Map<String, ?> map(final EntityContainer<ToolWithDescription> doc) {
        final Tool tool = doc.getEntity().getTool();
        final ToolDescription toolDescription = doc.getEntity().getToolDescription();
        final Map<String, Object> jsonMap = new HashMap<>();
        jsonMap.put(DOC_TYPE_FIELD, SearchDocumentType.TOOL.name());
        jsonMap.put("id", tool.getId());
        jsonMap.put("registry", tool.getRegistry());
        jsonMap.put("registryId", tool.getRegistryId());
        jsonMap.put("image", tool.getImage());
        jsonMap.put("name", tool.getImage());
        jsonMap.put("createdDate", parseDataToString(tool.getCreatedDate()));
        jsonMap.put("description", tool.getDescription());
        jsonMap.put("shortDescription", tool.getShortDescription());
        jsonMap.put("defaultCommand", tool.getDefaultCommand());
        jsonMap.put("toolGroupId", tool.getToolGroupId());
        jsonMap.put("parentId", tool.getToolGroupId());

        buildLabels(tool.getLabels(), jsonMap);
        buildVersions(toolDescription, jsonMap);
        buildPackages(toolDescription, jsonMap);

        buildUserContent(doc.getOwner(), jsonMap);
        buildMetadata(doc.getMetadata(), jsonMap);
        buildPermissions(doc.getPermissions(), jsonMap);

        return jsonMap;
    }

    private void buildLabels(final List<String> labels, final Map<String, Object> jsonMap) {
        if (!CollectionUtils.isEmpty(labels)) {
            jsonMap.put("labels", labels.toArray());
        }
    }

    private void buildVersions(final ToolDescription toolDescription, final Map<String, Object> jsonMap) {
        final List<ToolVersionAttributes> versions = toolDescription.getVersions();
        if (CollectionUtils.isNotEmpty(versions)) {
            final String[] versionArray = versions.stream()
                .map(ToolVersionAttributes::getVersion)
                .toArray(String[]::new);
            jsonMap.put("version", versionArray);
        }
    }

    private void buildPackages(final ToolDescription toolDescription, final Map<String, Object> jsonMap) {
        final List<ToolVersionAttributes> versions = toolDescription.getVersions();
        if (CollectionUtils.isNotEmpty(versions)) {
            final String[] toolPackagesNames = versions.stream()
                .map(ToolVersionAttributes::getScanResult)
                .filter(Objects::nonNull)
                .map(ToolVersionScanResult::getDependencies)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .map(ToolDependency::getName)
                .filter(Objects::nonNull)
                .distinct()
                .toArray(String[]::new);
            jsonMap.put("packages", toolPackagesNames);
        }
    }
}
