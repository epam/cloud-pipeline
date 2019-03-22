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

import com.epam.pipeline.elasticsearchagent.ObjectCreationUtils;
import com.epam.pipeline.elasticsearchagent.model.git.GitEvent;
import com.epam.pipeline.elasticsearchagent.model.git.GitEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GitHookServiceImplTest {

    private static final String URL = "https://ec2-52-28-80-53.eu-central-1.compute.amazonaws.com/root/bash_test.git";
    private static final String COMMIT_FILE_PATH = "src/new_version";
    private static final String BRANCH = "refs/heads/master";

    @Mock
    private GitPushEventProcessor gitPushEventProcessor;

    private GitHookServiceImpl gitHookService;
    private GitEvent gitEvent;

    @BeforeEach
    void setup() {
        gitEvent = ObjectCreationUtils.buildGitEvent(
                URL, Collections.singletonList(COMMIT_FILE_PATH), GitEventType.push, BRANCH);

        when(gitPushEventProcessor.supportedEventType()).thenReturn(GitEventType.push);
        List<GitEventProcessor> processors = new ArrayList<>();
        processors.add(gitPushEventProcessor);
        gitHookService = new GitHookServiceImpl(processors);

    }

    @Test
    void shouldProcessGitEventTest() {
        gitHookService.processGitEvent(gitEvent);

        verify(gitPushEventProcessor, times(1)).processEvent(any());
    }

    @Test
    void shouldNotProcessGitEventDueToUnsupportedTypeTest() {
        gitEvent.setEventType(GitEventType.tag_push);
        gitHookService.processGitEvent(gitEvent);

        verify(gitPushEventProcessor, times(0)).processEvent(any());
    }
}