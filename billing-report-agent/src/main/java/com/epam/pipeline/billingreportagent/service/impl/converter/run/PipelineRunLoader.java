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
package com.epam.pipeline.billingreportagent.service.impl.converter.run;

import com.epam.pipeline.billingreportagent.model.PermissionsContainer;
import com.epam.pipeline.billingreportagent.service.impl.CloudPipelineAPIClient;
import com.epam.pipeline.billingreportagent.service.impl.converter.AbstractCloudPipelineEntityLoader;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.security.acl.AclClass;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
public class PipelineRunLoader extends AbstractCloudPipelineEntityLoader<PipelineRun> {

    public PipelineRunLoader(final CloudPipelineAPIClient apiClient) {
        super(apiClient);
    }

    @Override
    protected PipelineRun fetchEntity(final Long id) {
        return getApiClient().loadPipelineRun(id);
    }

    @Override
    protected String getOwner(final PipelineRun entity) {
        return entity.getOwner();
    }

    @Override
    protected AclClass getAclClass(final PipelineRun entity) {
        return entity.getAclClass();
    }

    @Override
    protected PermissionsContainer loadPermissions(final Long id, final AclClass entityClass) {
        PipelineRun run = getApiClient().loadPipelineRun(id);
        Long pipelineId = run.getPipelineId();
        if (pipelineId == null) {
            PermissionsContainer permissionsContainer = new PermissionsContainer();
            permissionsContainer.add(Collections.emptyList(), run.getOwner());
            return permissionsContainer;
        }
        return super.loadPermissions(pipelineId, AclClass.PIPELINE);
    }
}
