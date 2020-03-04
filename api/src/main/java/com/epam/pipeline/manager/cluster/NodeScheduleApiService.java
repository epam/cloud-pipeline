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

package com.epam.pipeline.manager.cluster;

import com.epam.pipeline.controller.vo.cluster.ClusterNodeScheduleVO;
import com.epam.pipeline.entity.cluster.ClusterNodeSchedule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
@Slf4j
public class NodeScheduleApiService {

    public static final String ADMIN = "hasRole('ADMIN')";

    @Autowired
    private NodeScheduleManager nodeScheduleManager;

    @PreAuthorize(ADMIN)
    public ClusterNodeSchedule createClusterNodeSchedule(final ClusterNodeScheduleVO clusterNodeScheduleVO) {
        return nodeScheduleManager.createClusterNodeSchedule(clusterNodeScheduleVO);
    }

    @PreAuthorize(ADMIN)
    public ClusterNodeSchedule loadClusterNodeSchedule(final Long id) {
        return nodeScheduleManager.loadClusterNodeSchedule(id);
    }


    @PreAuthorize(ADMIN)
    public List<ClusterNodeSchedule> loadClusterNodeSchedule() {
        return nodeScheduleManager.loadClusterNodeSchedule();
    }

    @PreAuthorize(ADMIN)
    public ClusterNodeSchedule updateClusterNodeSchedule(final ClusterNodeScheduleVO clusterNodeScheduleVO) {
        return nodeScheduleManager.updateClusterNodeSchedule(clusterNodeScheduleVO);
    }

    @PreAuthorize(ADMIN)
    public void deleteClusterNodeSchedule(final Long id, final Long scheduleId) {
        if (Objects.nonNull(scheduleId)) {
            nodeScheduleManager.deleteClusterNodeSchedule(id, scheduleId);
        } else {
            nodeScheduleManager.deleteClusterNodeSchedule(id);
        }
    }
}
