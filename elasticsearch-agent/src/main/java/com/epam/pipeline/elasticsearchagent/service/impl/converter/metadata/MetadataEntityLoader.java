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

import com.epam.pipeline.elasticsearchagent.model.PermissionsContainer;
import com.epam.pipeline.elasticsearchagent.service.impl.CloudPipelineAPIClient;
import com.epam.pipeline.elasticsearchagent.service.impl.converter.AbstractCloudPipelineEntityLoader;
import com.epam.pipeline.entity.metadata.MetadataEntity;
import com.epam.pipeline.entity.security.acl.AclClass;
import org.springframework.stereotype.Component;

@Component
public class MetadataEntityLoader extends AbstractCloudPipelineEntityLoader<MetadataEntity> {

    public MetadataEntityLoader(CloudPipelineAPIClient apiClient) {
        super(apiClient);
    }

    @Override
    protected MetadataEntity fetchEntity(final Long id) {
        return getApiClient().loadMetadataEntity(id);
    }

    @Override
    protected String getOwner(final MetadataEntity entity) {
        return null;
    }

    @Override
    protected AclClass getAclClass(final MetadataEntity entity) {
        return entity.getAclClass();
    }

    @Override
    protected PermissionsContainer loadPermissions(final Long id, final AclClass entityClass) {
        MetadataEntity metadataEntity = fetchEntity(id);
        return super.loadPermissions(metadataEntity.getParent().getId(), metadataEntity.getParent().getAclClass());
    }
}
