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
package com.epam.pipeline.elasticsearchagent.service.git;

import com.epam.pipeline.elasticsearchagent.model.git.GitEvent;
import com.epam.pipeline.elasticsearchagent.model.git.GitEventType;
import com.epam.pipeline.elasticsearchagent.service.impl.CloudPipelineAPIClient;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.exception.PipelineResponseException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public interface GitEventProcessor {

    Logger LOG = LoggerFactory.getLogger(GitEventProcessor.class);

    GitEventType supportedEventType();
    void processEvent(GitEvent event);

    default boolean validateEvent(GitEvent event) {
        if (supportedEventType() != event.getEventType()) {
            LOG.error("Not supported event type: {}", event.getEventType());
            return false;
        }
        if (event.getProject() == null || StringUtils.isBlank(
                event.getProject().getRepositoryUrl())) {
            LOG.error("Project is not specified for event");
            return false;
        }
        return true;
    }

    default Optional<Pipeline> fetchPipeline(final GitEvent event,
                                             final CloudPipelineAPIClient apiClient) {
        try {
            return Optional.of(
                    apiClient.loadPipelineByRepositoryUrl(event.getProject().getRepositoryUrl()));
        } catch (PipelineResponseException e) {
            LOG.error("An error during pipeline fetch: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
