/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.cluster.cleaner;

import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.manager.pipeline.RunLogManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("PMD.AvoidCatchingGenericException")
public class RunLogMigrationCleaner implements RunCleaner {

    private final RunLogManager runLogManager;

    @Override
    public void cleanResources(final PipelineRun run) {
        if (Objects.isNull(run)) {
            return;
        }
        cleanResources(run.getId());
    }

    @Override
    public void cleanResources(final Long runId) {
        try {
            runLogManager.migrateRunLogsToStorage(runId);
        } catch (Exception e) {
            log.error("Failed to migrate run logs to storage for run {}: {}", runId, e.getMessage(), e);
        }
    }
}
