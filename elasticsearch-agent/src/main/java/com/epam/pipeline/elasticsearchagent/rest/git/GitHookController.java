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
package com.epam.pipeline.elasticsearchagent.rest.git;

import com.epam.pipeline.elasticsearchagent.model.git.GitEvent;
import com.epam.pipeline.elasticsearchagent.rest.AbstractRestController;
import com.epam.pipeline.elasticsearchagent.service.git.GitHookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@ConditionalOnProperty(value = "git.hook.endpoint.disable", matchIfMissing = true, havingValue = "false")
@RequiredArgsConstructor
@Slf4j
@RestController
public class GitHookController extends AbstractRestController {

    private final GitHookService gitHookService;

    @PostMapping(value = "/githook/event")
    public ResponseEntity processGitEvent(@RequestBody final GitEvent gitEvent) {
        log.debug("Event: {}", gitEvent);
        gitHookService.processGitEvent(gitEvent);
        return new ResponseEntity(HttpStatus.OK);
    }
}
