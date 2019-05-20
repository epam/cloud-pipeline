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

package com.epam.pipeline.dao.monitoring.metricrequester;

import com.epam.pipeline.entity.cluster.monitoring.MonitoringStats;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Node usage monitoring requester.
 *
 * It collects stats for a single usage metric: CPU, RAM and etc.
 */
public interface MonitoringRequester {

    /**
     * Collects node usage stats.
     *
     * @param nodeName Node name to collect stats for.
     * @param from Minimal date for collecting stats.
     * @param to Maximal date for collecting stats.
     * @param interval Duration of a single monitoring.
     * @return Monitoring stats for the given period.
     */
    List<MonitoringStats> requestStats(String nodeName, LocalDateTime from, LocalDateTime to, Duration interval);
}
