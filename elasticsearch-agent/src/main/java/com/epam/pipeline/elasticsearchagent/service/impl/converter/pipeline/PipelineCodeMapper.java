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
import org.springframework.stereotype.Component;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

import static com.epam.pipeline.elasticsearchagent.service.ElasticsearchSynchronizer.DOC_TYPE_FIELD;

@Component
public class PipelineCodeMapper {

    public Map<String, ?> pipelineCodeToDocument(final Pipeline pipeline,
                                                 final String pipelineVersion,
                                                 final String path,
                                                 final byte[] fileContent,
                                                 final PermissionsContainer permissions) {
        final Map<String, Object> jsonMap = new HashMap<>();

        jsonMap.put(DOC_TYPE_FIELD, SearchDocumentType.PIPELINE_CODE.name());
        jsonMap.put("pipelineId", pipeline.getId());
        jsonMap.put("parentId", pipeline.getId());
        jsonMap.put("pipelineName", pipeline.getName());
        jsonMap.put("pipelineVersion", pipelineVersion);
        jsonMap.put("description", pipelineVersion);
        jsonMap.put("path", path);
        jsonMap.put("name", path);
        jsonMap.put("id", path);
        jsonMap.put("content", buildDocContent(fileContent));

        jsonMap.put("allowed_users", permissions.getAllowedUsers().toArray());
        jsonMap.put("denied_users", permissions.getDeniedUsers().toArray());
        jsonMap.put("allowed_groups", permissions.getAllowedGroups().toArray());
        jsonMap.put("denied_groups", permissions.getDeniedGroups().toArray());

        return jsonMap;
    }

    private String buildDocContent(final byte[] fileContent) {
        if (FileContentUtils.isBinaryContent(fileContent)) {
            return null;
        } else {
            return new String(fileContent, Charset.defaultCharset());
        }
    }
}
