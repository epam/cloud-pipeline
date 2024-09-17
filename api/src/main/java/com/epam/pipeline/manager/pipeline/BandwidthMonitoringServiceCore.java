/*
 * Copyright 2024 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.pipeline;

import com.epam.pipeline.controller.vo.TagsVO;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.docker.DockerContainerOperationManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.SchedulerLock;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

import static com.epam.pipeline.manager.pipeline.PipelineRunManager.NETWORK_LIMIT;

@Service
@Slf4j
@RequiredArgsConstructor
public class BandwidthMonitoringServiceCore {
    private final PipelineRunManager pipelineRunManager;
    private final DockerContainerOperationManager dockerContainerOperationManager;
    private final PreferenceManager preferenceManager;

    @SchedulerLock(name = "BandwidthMonitoringService_monitor", lockAtMostForString = "PT10M")
    public void monitor() {
        final List<PipelineRun> runs = pipelineRunManager.loadRunningPipelineRuns();
        final String networkLimitDateTag = getNetworkLimitDateTag();
        for (PipelineRun run: runs) {
            final Map<String, String> tags = run.getTags();
            if (shouldSetLimit(tags)) {
                final int boundary = Integer.parseInt(tags.get(NETWORK_LIMIT));
                dockerContainerOperationManager.limitNetworkBandwidth(run, boundary, true);
                run.addTag(networkLimitDateTag, DateUtils.nowUTCStr());
            } else if (shouldCleanLimit(tags)) {
                dockerContainerOperationManager.limitNetworkBandwidth(run, 0, false);
                run.removeTag(networkLimitDateTag);
            }
            pipelineRunManager.updateTags(run.getId(), new TagsVO(run.getTags()), true);
        }
    }

    private boolean shouldSetLimit(final Map<String, String> tags) {
        return tags.containsKey(NETWORK_LIMIT) && !tags.containsKey(getNetworkLimitDateTag());
    }

    private boolean shouldCleanLimit(final Map<String, String> tags) {
        return !tags.containsKey(NETWORK_LIMIT) && tags.containsKey(getNetworkLimitDateTag());
    }

    private String getNetworkLimitDateTag() {
        final String suffix = preferenceManager.getPreference(SystemPreferences.SYSTEM_RUN_TAG_DATE_SUFFIX);
        return String.format("%s%s", NETWORK_LIMIT, suffix);
    }
}
