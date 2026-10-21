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

import com.epam.pipeline.entity.monitoring.IdleMonitoringType;
import com.epam.pipeline.entity.pipeline.PipelineRun;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public interface RunMonitor {

    long ONE = 1L;
    int MILLIS = 1000;
    double PERCENT = 100.0;
    double ONE_THOUSANDTH = 0.001;
    double ZERO_USAGE_RATE = 0.0;
    String TRUE_VALUE_STRING = "true";

    List<String> IDLE_TAGS = Arrays.stream(IdleMonitoringType.values())
            .map(IdleMonitoringType::getTag)
            .collect(Collectors.toList());

    void monitor(List<PipelineRun> runs);

    default int order() {
        return Integer.MAX_VALUE;
    }
}
