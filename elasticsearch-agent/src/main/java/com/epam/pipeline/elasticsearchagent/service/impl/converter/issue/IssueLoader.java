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
package com.epam.pipeline.elasticsearchagent.service.impl.converter.issue;

import com.epam.pipeline.elasticsearchagent.model.PermissionsContainer;
import com.epam.pipeline.elasticsearchagent.service.impl.CloudPipelineAPIClient;
import com.epam.pipeline.elasticsearchagent.service.impl.converter.AbstractCloudPipelineEntityLoader;
import com.epam.pipeline.entity.issue.Issue;
import com.epam.pipeline.entity.security.acl.AclClass;
import org.springframework.stereotype.Component;


@Component
public class IssueLoader extends AbstractCloudPipelineEntityLoader<Issue> {

    public IssueLoader(final CloudPipelineAPIClient apiClient) {
        super(apiClient);
    }

    @Override
    public Issue fetchEntity(final Long id) {
        return getApiClient().loadIssue(id);
    }

    @Override
    protected String getOwner(final Issue entity) {
        return entity.getAuthor();
    }

    @Override
    protected AclClass getAclClass(final Issue entity) {
        return entity.getEntity().getEntityClass();
    }

    @Override
    protected PermissionsContainer loadPermissions(final Long id, final AclClass entityClass) {
        Issue issue = fetchEntity(id);
        return super.loadPermissions(issue.getEntity().getEntityId(), issue.getEntity().getEntityClass());
    }
}
