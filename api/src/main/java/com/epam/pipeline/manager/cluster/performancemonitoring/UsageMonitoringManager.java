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

package com.epam.pipeline.manager.cluster.performancemonitoring;

import com.epam.pipeline.entity.cluster.monitoring.MonitoringStats;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDateTime;
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
    default List<MonitoringStats> getStatsForNode(String nodeName) {
        return getStatsForNode(nodeName, null, null);
    }

    /**
     * Retrieves monitoring stats for node.
     *
     * @param nodeName Cluster node name.
     * @param from Minimal date for collecting stats.
     * @param to Maximal date for collecting stats.
     * @return List of monitoring stats.
     */
    List<MonitoringStats> getStatsForNode(String nodeName,
                                          @Nullable LocalDateTime from,
                                          @Nullable LocalDateTime to);

    /**
     * Retrieves monitoring stats for node as input stream.
     *
     * @param nodeName Cluster node name.
     * @param from Minimal date for collecting stats.
     * @param to Maximal date for collecting stats.
     * @param interval period of stats collecting
     * @return stream, containing required information in .csv format
     */
    InputStream getStatsForNodeAsInputStream(String nodeName,
                                             @Nullable LocalDateTime from,
                                             @Nullable LocalDateTime to,
                                             Duration interval);

    /**
     * Retrieves number of bytes that available on a pod disk .
     *
     * @param nodeName Cluster node name.
     * @param podId
     * @param dockerImage of the container of the pod.
     * @return available bytes amount.
     */
    long getPodDiskSpaceAvailable(String nodeName,
                                  String podId,
                                  String dockerImage);

}
