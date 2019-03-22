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

import com.epam.pipeline.elasticsearchagent.model.RunConfigurationDoc;
import com.epam.pipeline.elasticsearchagent.service.impl.CloudPipelineAPIClient;
import com.epam.pipeline.elasticsearchagent.service.impl.converter.AbstractCloudPipelineEntityLoader;
import com.epam.pipeline.entity.configuration.RunConfiguration;
import com.epam.pipeline.entity.configuration.RunConfigurationEntry;
import com.epam.pipeline.entity.security.acl.AclClass;
import org.apache.commons.collections4.ListUtils;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class RunConfigurationLoader extends AbstractCloudPipelineEntityLoader<RunConfigurationDoc> {

    public RunConfigurationLoader(final CloudPipelineAPIClient apiClient) {
        super(apiClient);
    }

    @Override
    protected RunConfigurationDoc fetchEntity(final Long id) {
        final RunConfiguration configuration = getApiClient().loadRunConfiguration(id);
        return RunConfigurationDoc.builder()
                .configuration(configuration)
                .pipelines(ListUtils.emptyIfNull(configuration.getEntries())
                        .stream()
                        .map(entry -> {
                            if (entry instanceof RunConfigurationEntry) {
                                return ((RunConfigurationEntry) entry).getPipelineId();
                            }
                            return null;
                        })
                        .filter(Objects::nonNull)
                        .map(pipelineId -> getApiClient().loadPipeline(String.valueOf(pipelineId)))
                        .collect(Collectors.toList()))
                .build();
    }

    @Override
    protected String getOwner(final RunConfigurationDoc entity) {
        return entity.getConfiguration().getOwner();
    }

    @Override
    protected AclClass getAclClass(final RunConfigurationDoc entity) {
        return entity.getConfiguration().getAclClass();
    }

}
