/*
 * Copyright 2017-2025 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.cluster.performancemonitoring.monitor;

import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.StopServerlessRun;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.pipeline.StopServerlessRunManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import org.apache.commons.collections4.ListUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Component
public class ServerlessRunMonitor implements RunMonitor {

    private final PipelineRunManager pipelineRunManager;
    private final StopServerlessRunManager stopServerlessRunManager;
    private final PreferenceManager preferenceManager;

    @Autowired
    public ServerlessRunMonitor(final PipelineRunManager pipelineRunManager,
                                 final StopServerlessRunManager stopServerlessRunManager,
                                 final PreferenceManager preferenceManager) {
        this.pipelineRunManager = pipelineRunManager;
        this.stopServerlessRunManager = stopServerlessRunManager;
        this.preferenceManager = preferenceManager;
    }

    @Override
    public void monitor(final List<PipelineRun> runs) {
        ListUtils.emptyIfNull(stopServerlessRunManager.loadActiveServerlessRuns())
                .stream()
                .filter(this::serverlessRunIsExpired)
                .forEach(run -> pipelineRunManager.stopServerlessRun(run.getRunId()));
    }

    private boolean serverlessRunIsExpired(final StopServerlessRun run) {
        final Long timeout = getTimeoutMinutes(run);
        return Objects.nonNull(timeout) && run.getLastUpdate().isBefore(LocalDateTime.now().minusMinutes(timeout));
    }

    private Long getTimeoutMinutes(final StopServerlessRun run) {
        return Objects.nonNull(run.getStopAfter())
                ? run.getStopAfter()
                : preferenceManager.getPreference(SystemPreferences.LAUNCH_SERVERLESS_STOP_TIMEOUT).longValue();
    }
}
