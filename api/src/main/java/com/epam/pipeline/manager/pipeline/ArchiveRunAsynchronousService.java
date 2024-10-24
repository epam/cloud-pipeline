/*
 * Copyright 2024 EPAM Systems, Inc. (https://www.epam.com/)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.manager.pipeline;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class ArchiveRunAsynchronousService {

    private final ArchiveRunCoreService archiveRunCoreService;

    @Async("backgroundJobsExecutor")
    public void archiveRunsAsynchronous(final Map<String, Date> ownersAndDates, final List<Long> terminalStates,
                                        final Integer runsChunkSize, final Integer ownersChunkSize,
                                        final boolean dryRun) {
        archiveRunCoreService.archiveRuns(ownersAndDates, terminalStates, runsChunkSize, ownersChunkSize, dryRun);
    }
}
