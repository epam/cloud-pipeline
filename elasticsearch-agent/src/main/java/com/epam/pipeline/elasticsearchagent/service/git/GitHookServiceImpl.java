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
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class GitHookServiceImpl implements GitHookService {

    private final Map<GitEventType, GitEventProcessor> eventProcessors;

    public GitHookServiceImpl(final List<GitEventProcessor> processors) {
        this.eventProcessors = ListUtils
                .emptyIfNull(processors)
                .stream()
                .collect(Collectors.toMap(GitEventProcessor::supportedEventType, Function.identity()));
    }

    @Override
    public void processGitEvent(final GitEvent event) {
        GitEventType eventType = event.getEventType();
        if (eventType == null) {
            log.error("Unspecified git event type");
            return;
        }
        if (!eventProcessors.containsKey(eventType)) {
            log.error("Unsupported event type: {}", eventType);
            return;
        }

        log.debug("Processing git hook with type: {}", eventType);
        eventProcessors.get(eventType).processEvent(event);
    }
}
