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

import com.epam.pipeline.controller.vo.PipelineRunScheduleVO;
import com.epam.pipeline.controller.vo.cluster.ClusterNodeScheduleVO;
import com.epam.pipeline.dao.cluster.ClusterNodeScaleDao;
import com.epam.pipeline.entity.cluster.ClusterNodeScale;
import com.epam.pipeline.entity.cluster.ClusterNodeSchedule;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.pipeline.run.RunSchedule;
import com.epam.pipeline.entity.pipeline.run.ScheduleType;
import com.epam.pipeline.manager.pipeline.RunScheduleManager;
import com.epam.pipeline.manager.region.CloudRegionManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class NodeScheduleManager {

    @Autowired
    private ClusterNodeScaleDao clusterNodeScaleDao;

    @Autowired
    private RunScheduleManager runScheduleManager;

    @Autowired
    private CloudRegionManager cloudRegionManager;

    @Transactional(propagation = Propagation.REQUIRED)
    public ClusterNodeSchedule createClusterNodeSchedule(final ClusterNodeScheduleVO clusterNodeScheduleVO) {
        final ClusterNodeScale clusterNodeScale = clusterNodeScheduleVO.getClusterNodeScale();
        Assert.notNull(clusterNodeScale,
                "Node specification isn't provided, when trying to schedule Node action");
        Assert.isTrue(!CollectionUtils.isEmpty(clusterNodeScheduleVO.getSchedule()),
                "Schedule isn't provided, when trying to schedule Node action");

        if (clusterNodeScale.getInstance().getSpot() == null) {
            clusterNodeScale.getInstance().setSpot(false);
        }

        // try to load region
        cloudRegionManager.loadOrDefault(clusterNodeScale.getInstance().getCloudRegionId());

        clusterNodeScaleDao.createClusterNodeScale(clusterNodeScale);
        List<RunSchedule> schedules = runScheduleManager.createSchedules(clusterNodeScale.getId(),
                ScheduleType.CLUSTER_NODE_SCALE, clusterNodeScheduleVO.getSchedule());
        return new ClusterNodeSchedule(clusterNodeScale, schedules);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public ClusterNodeSchedule loadClusterNodeSchedule(final Long id) {
        final ClusterNodeScale clusterNodeScale = clusterNodeScaleDao.loadClusterNodeScale(id);
        List<RunSchedule> schedules = ListUtils.emptyIfNull(
                runScheduleManager.loadAllSchedulesBySchedulableId(id, ScheduleType.CLUSTER_NODE_SCALE)
        );
        if (clusterNodeScale == null || schedules.isEmpty()) {
            return null;
        }
        return new ClusterNodeSchedule(clusterNodeScale, schedules);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public List<ClusterNodeSchedule> loadClusterNodeSchedule() {
        final List<ClusterNodeScale> clusterNodeScales = clusterNodeScaleDao.loadClusterNodeScale();
        return clusterNodeScales.stream()
                .map(batchNodeSpec -> {
                    List<RunSchedule> schedule = runScheduleManager.loadAllSchedulesBySchedulableId(
                            batchNodeSpec.getId(), ScheduleType.CLUSTER_NODE_SCALE);
                    return new ClusterNodeSchedule(batchNodeSpec, schedule);
                })
                .collect(Collectors.toList());
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public ClusterNodeSchedule updateClusterNodeSchedule(final ClusterNodeScheduleVO clusterNodeScheduleVO) {
        ClusterNodeScale clusterNodeScale = clusterNodeScheduleVO.getClusterNodeScale();
        ClusterNodeSchedule loaded = loadClusterNodeSchedule(clusterNodeScheduleVO.getClusterNodeScale().getId());
        Assert.notNull(loaded, "Can't find schedule for batch of node with id: " + clusterNodeScale.getId());
        clusterNodeScaleDao.updateClusterNodeScale(mergeClusterNodeSchedule(clusterNodeScale, loaded.getClusterNodeScale()));
        List<PipelineRunScheduleVO> schedules = clusterNodeScheduleVO.getSchedule();
        if (!CollectionUtils.emptyIfNull(schedules).isEmpty()) {
            runScheduleManager.updateSchedules(clusterNodeScale.getId(), ScheduleType.CLUSTER_NODE_SCALE, schedules);
        }
        return loadClusterNodeSchedule(clusterNodeScheduleVO.getClusterNodeScale().getId());
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void deleteClusterNodeSchedule(final Long id) {
        Assert.notNull(loadClusterNodeSchedule(id), "Scale cluster task not found, id: " + id);
        runScheduleManager.deleteSchedules(id, ScheduleType.CLUSTER_NODE_SCALE);
        clusterNodeScaleDao.deleteClusterNodeScale(id);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void deleteClusterNodeSchedule(final Long id, final Long scheduleId) {
        log.debug("Delete schedule with id: " + scheduleId + " from scale cluster task with id: " + id);
        Assert.notNull(loadClusterNodeSchedule(id), "Scale cluster task not found, id: " + id);
        runScheduleManager.deleteSchedules(id, ScheduleType.CLUSTER_NODE_SCALE, Collections.singletonList(scheduleId));
    }

    private ClusterNodeScale mergeClusterNodeSchedule(final ClusterNodeScale clusterNodeScale, final ClusterNodeScale loaded) {
        if (clusterNodeScale.getNumberOfInstances() != null && clusterNodeScale.getNumberOfInstances() > 0) {
            loaded.setNumberOfInstances(clusterNodeScale.getNumberOfInstances());
        }
        RunInstance instance = clusterNodeScale.getInstance();
        if (instance != null) {
            RunInstance loadedInstance = loaded.getInstance();
            loadedInstance.setNodeDisk(instance.getNodeDisk() != null ? instance.getNodeDisk() : loadedInstance.getNodeDisk());
            loadedInstance.setNodeType(instance.getNodeType() != null ? instance.getNodeType() : loadedInstance.getNodeType());
            loadedInstance.setCloudRegionId(instance.getCloudRegionId() != null ? instance.getCloudRegionId() : loadedInstance.getCloudRegionId());
        }
        return loaded;
    }
}
