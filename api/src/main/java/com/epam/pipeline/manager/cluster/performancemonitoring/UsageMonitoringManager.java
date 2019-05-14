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

package com.epam.pipeline.manager.cluster.performancemonitoring;

import com.epam.pipeline.entity.cluster.monitoring.MonitoringStats;

import java.util.List;

/**
 * Node usage monitoring manager.
 */
public interface UsageMonitoringManager {

    /**
     * Retrieves monitoring stats for node.
     *
     * @param nodeName Cluster node name.
     * @return List of monitoring stats.
     */
    List<MonitoringStats> getStatsForNode(String nodeName);

    /**
     * Get available disk space for container.
     *
     * @param nodeName Cluster node name.
     * @param podId Cluster pod name.
     * @param dockerImage Container docker image.
     * @return Available disk space in container.
     */
    long getDiskAvailableForDocker(String nodeName, String podId, String dockerImage);
}
