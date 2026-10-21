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

import com.epam.pipeline.entity.cluster.InstanceType;
import com.epam.pipeline.entity.monitoring.IdleMonitoringConfig;
import com.epam.pipeline.entity.monitoring.IdleMonitoringType;
import com.epam.pipeline.entity.monitoring.IdleRunAction;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.utils.DateUtils;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

final class MonitorTestUtils {

    static final int MAX_IDLE_TIMEOUT = 30;
    static final int IDLE_THRESHOLD_PERCENT = 30;
    static final int ACTION_TIMEOUT_MINUTES = 1;
    static final String PLATFORM = "linux";
    static final String TAG_DATE_SUFFIX = "_date";
    static final String TRUE_VALUE_STRING = "true";
    static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private MonitorTestUtils() {
    }

    static InstanceType cpuInstanceType(final String name, final int vcpu) {
        final InstanceType t = new InstanceType();
        t.setName(name);
        t.setVCPU(vcpu);
        return t;
    }

    static InstanceType gpuInstanceType(final String name, final int vcpu, final int gpu) {
        final InstanceType t = cpuInstanceType(name, vcpu);
        t.setGpu(gpu);
        return t;
    }

    static PipelineRun activeRun(final InstanceType type, final String nodeName,
                                  final long runId, final boolean spot) {
        final PipelineRun run = new PipelineRun();
        run.setInstance(new RunInstance(type.getName(), 0, 0, null, null, null, nodeName, PLATFORM, spot));
        run.setPodId(nodeName + "-pod");
        run.setId(runId);
        run.setStartDate(new Date(Instant.now()
                .minus(MAX_IDLE_TIMEOUT + 1, ChronoUnit.MINUTES).toEpochMilli()));
        run.setProlongedAtTime(DateUtils.nowUTC().minus(MAX_IDLE_TIMEOUT + 1, ChronoUnit.MINUTES));
        run.setTags(new HashMap<>());
        return run;
    }

    static List<IdleMonitoringConfig> cpuIdleConfig(final IdleRunAction action) {
        return Collections.singletonList(new IdleMonitoringConfig(
                IdleMonitoringType.CPU, true, (double) IDLE_THRESHOLD_PERCENT,
                MAX_IDLE_TIMEOUT, ACTION_TIMEOUT_MINUTES, action));
    }

    static List<IdleMonitoringConfig> gpuIdleConfig(final IdleRunAction action) {
        return Collections.singletonList(new IdleMonitoringConfig(
                IdleMonitoringType.GPU, true, 0.0, MAX_IDLE_TIMEOUT, ACTION_TIMEOUT_MINUTES, action));
    }

    static List<IdleMonitoringConfig> absoluteIdleConfig(final IdleRunAction action) {
        return Collections.singletonList(new IdleMonitoringConfig(
                IdleMonitoringType.ABSOLUTE, true, null, null, ACTION_TIMEOUT_MINUTES, action));
    }
}
