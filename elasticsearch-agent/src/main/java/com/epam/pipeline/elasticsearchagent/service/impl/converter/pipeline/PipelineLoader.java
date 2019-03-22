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

import com.epam.pipeline.elasticsearchagent.model.PipelineDoc;
import com.epam.pipeline.elasticsearchagent.service.impl.CloudPipelineAPIClient;
import com.epam.pipeline.elasticsearchagent.service.impl.converter.AbstractCloudPipelineEntityLoader;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.Revision;
import com.epam.pipeline.entity.security.acl.AclClass;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class PipelineLoader extends AbstractCloudPipelineEntityLoader<PipelineDoc> {

    private static final String DRAFT_VERSION = "draft-";

    public PipelineLoader(final CloudPipelineAPIClient apiClient) {
        super(apiClient);
    }

    @Override
    protected PipelineDoc fetchEntity(final Long id) {
        Pipeline pipeline = getApiClient().loadPipeline(String.valueOf(id));
        List<Revision> revisions = getApiClient().loadPipelineVersions(pipeline.getId())
                .stream()
                .filter(revision -> !revision.getName().startsWith(DRAFT_VERSION))
                .collect(Collectors.toList());
        PipelineDoc.PipelineDocBuilder pipelineDocBuilder = PipelineDoc.builder()
                .pipeline(pipeline)
                .revisions(revisions);
        return pipelineDocBuilder.build();
    }

    @Override
    protected String getOwner(PipelineDoc entity) {
        return entity.getPipeline().getOwner();
    }

    @Override
    protected AclClass getAclClass(PipelineDoc entity) {
        return entity.getPipeline().getAclClass();
    }

    @Override
    protected String buildNotFoundErrorMessage(final Long id) {
        return String.format("Pipeline with requested id: '%d' was not found", id);
    }
}
