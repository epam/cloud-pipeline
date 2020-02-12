/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.cluster.alive.policy;

import com.epam.pipeline.entity.cluster.ClusterKeepAlivePolicy;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Component
@RequiredArgsConstructor
@Slf4j
public class IdleMinutesNodeExpirationService implements NodeExpirationService {

    private final PipelineRunManager runManager;
    private final PreferenceManager preferenceManager;

    @Override
    public boolean isNodeExpired(final Long runId, final LocalDateTime nodeLaunchTime) {
        try {
            final Integer keepAliveMinutes = preferenceManager.getPreference(
                    SystemPreferences.CLUSTER_KEEP_ALIVE_MINUTES);
            if (keepAliveMinutes == null) {
                return true;
            }
            final PipelineRun pipelineRun = runManager.loadPipelineRun(runId);
            final Date endDate = pipelineRun.getEndDate();
            if (endDate == null) {
                return true;
            }
            return Instant.ofEpochMilli(endDate.getTime())
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                    .plus(keepAliveMinutes, ChronoUnit.MINUTES)
                    .isAfter(LocalDate.now());
        } catch (IllegalArgumentException e) {
            log.trace(e.getMessage(), e);
            return true;
        }
    }

    @Override
    public ClusterKeepAlivePolicy policy() {
        return ClusterKeepAlivePolicy.IDLE_MINUTES;
    }
}
