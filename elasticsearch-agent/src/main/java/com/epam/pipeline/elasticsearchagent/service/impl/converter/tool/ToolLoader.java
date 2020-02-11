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

import com.epam.pipeline.elasticsearchagent.model.ToolWithDescription;
import com.epam.pipeline.elasticsearchagent.service.impl.CloudPipelineAPIClient;
import com.epam.pipeline.elasticsearchagent.service.impl.converter.AbstractCloudPipelineEntityLoader;
import com.epam.pipeline.entity.docker.ToolDescription;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.security.acl.AclClass;
import org.springframework.stereotype.Component;

@Component
public class ToolLoader extends AbstractCloudPipelineEntityLoader<ToolWithDescription> {

    public ToolLoader(final CloudPipelineAPIClient apiClient) {
        super(apiClient);
    }

    @Override
    protected ToolWithDescription fetchEntity(final Long id) {
        final Tool tool = getApiClient().loadTool(String.valueOf(id));
        final ToolDescription toolDescription = getApiClient().loadToolDescription(tool.getId());
        return new ToolWithDescription(tool, toolDescription);
    }

    @Override
    protected String getOwner(final ToolWithDescription entity) {
        return entity.getTool().getOwner();
    }

    @Override
    protected AclClass getAclClass(final ToolWithDescription entity) {
        return entity.getTool().getAclClass();
    }
}
