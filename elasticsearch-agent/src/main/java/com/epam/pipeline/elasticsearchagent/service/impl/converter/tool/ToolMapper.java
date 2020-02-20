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
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import static com.epam.pipeline.elasticsearchagent.service.ElasticsearchSynchronizer.DOC_TYPE_FIELD;

@Component
public class ToolMapper implements EntityMapper<ToolWithDescription> {

    @Override
    public XContentBuilder map(final EntityContainer<ToolWithDescription> doc) {
        try (XContentBuilder jsonBuilder = XContentFactory.jsonBuilder()) {
            final Tool tool = doc.getEntity().getTool();
            final ToolDescription toolDescription = doc.getEntity().getToolDescription();
            jsonBuilder
                    .startObject()
                    .field(DOC_TYPE_FIELD, SearchDocumentType.TOOL.name())
                    .field("id", tool.getId())
                    .field("registry", tool.getRegistry())
                    .field("registryId", tool.getRegistryId())
                    .field("image", tool.getImage())
                    .field("createdDate", parseDataToString(tool.getCreatedDate()))
                    .field("description", tool.getDescription())
                    .field("shortDescription", tool.getShortDescription())
                    .field("defaultCommand", tool.getDefaultCommand())
                    .field("toolGroupId", tool.getToolGroupId());

            buildLabels(tool.getLabels(), jsonBuilder);
            buildVersions(toolDescription, jsonBuilder);
            buildPackages(toolDescription, jsonBuilder);

            buildUserContent(doc.getOwner(), jsonBuilder);
            buildMetadata(doc.getMetadata(), jsonBuilder);
            buildPermissions(doc.getPermissions(), jsonBuilder);

            jsonBuilder.endObject();
            return jsonBuilder;
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to create elasticsearch document for tool: ", e);
        }
    }

    private void buildLabels(final List<String> labels, final XContentBuilder jsonBuilder) throws IOException {
        if (!CollectionUtils.isEmpty(labels)) {
            jsonBuilder.array("labels", labels.toArray());
        }
    }

    private void buildVersions(final ToolDescription toolDescription, final XContentBuilder jsonBuilder)
        throws IOException {
        final List<ToolVersionAttributes> versions = toolDescription.getVersions();
        if (CollectionUtils.isNotEmpty(versions)) {
            final String[] versionArray = versions.stream()
                .map(ToolVersionAttributes::getVersion)
                .toArray(String[]::new);
            jsonBuilder.array("version", versionArray);
        }
    }

    private void buildPackages(final ToolDescription toolDescription, final XContentBuilder jsonBuilder)
        throws IOException {
        final List<ToolVersionAttributes> versions = toolDescription.getVersions();
        if (CollectionUtils.isNotEmpty(versions)) {
            final String[] toolPackagesNames = versions.stream()
                .map(ToolVersionAttributes::getScanResult)
                .filter(Objects::nonNull)
                .map(ToolVersionScanResult::getDependencies)
                .flatMap(List::stream)
                .map(ToolDependency::getName)
                .distinct()
                .toArray(String[]::new);
            jsonBuilder.array("packages", toolPackagesNames);
        }
    }
}
