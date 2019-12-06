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

import com.epam.pipeline.elasticsearchagent.model.PermissionsContainer;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.search.SearchDocumentType;
import com.epam.pipeline.utils.FileContentUtils;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.Charset;

import static com.epam.pipeline.elasticsearchagent.service.ElasticsearchSynchronizer.DOC_TYPE_FIELD;

@Component
public class PipelineCodeMapper {

    public XContentBuilder pipelineCodeToDocument(final Pipeline pipeline,
                                                  final String pipelineVersion,
                                                  final String path,
                                                  final byte[] fileContent,
                                                  final PermissionsContainer permissions) {
        try (XContentBuilder jsonBuilder = XContentFactory.jsonBuilder()) {
            jsonBuilder
                    .startObject()
                    .field(DOC_TYPE_FIELD, SearchDocumentType.PIPELINE_CODE.name())
                    .field("pipelineId", pipeline.getId())
                    .field("pipelineName", pipeline.getName())
                    .field("pipelineVersion", pipelineVersion)
                    .field("path", path)
                    .field("content", buildDocContent(fileContent));

            jsonBuilder.array("allowed_users", permissions.getAllowedUsers().toArray());
            jsonBuilder.array("denied_users", permissions.getDeniedUsers().toArray());
            jsonBuilder.array("allowed_groups", permissions.getAllowedGroups().toArray());
            jsonBuilder.array("denied_groups", permissions.getDeniedGroups().toArray());

            jsonBuilder.endObject();
            return jsonBuilder;
        } catch (IOException e) {
            throw new IllegalArgumentException("An error occurred while creating document: ", e);
        }
    }

    private String buildDocContent(final byte[] fileContent) {
        if (FileContentUtils.isBinaryContent(fileContent)) {
            return null;
        } else {
            return new String(fileContent, Charset.defaultCharset());
        }
    }
}
