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
package com.epam.pipeline.elasticsearchagent.service.impl.converter;

import com.epam.pipeline.elasticsearchagent.service.impl.CloudPipelineAPIClient;
import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.security.acl.AclClass;

public abstract class AbstractSecuredEntityLoaderImpl<T extends AbstractSecuredEntity>
        extends AbstractCloudPipelineEntityLoader<T> {

    public AbstractSecuredEntityLoaderImpl(final CloudPipelineAPIClient apiClient) {
        super(apiClient);
    }

    @Override
    protected String getOwner(final T entity) {
        return entity.getOwner();
    }

    @Override
    protected AclClass getAclClass(final T entity) {
        return entity.getAclClass();
    }
}
