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

import com.epam.pipeline.AbstractSpringTest;
import com.epam.pipeline.app.TestApplicationWithAclSecurity;
import com.epam.pipeline.controller.vo.PipelineRunScheduleVO;
import com.epam.pipeline.controller.vo.cluster.ClusterNodeScheduleVO;
import com.epam.pipeline.entity.cluster.ClusterNodeScale;
import com.epam.pipeline.entity.cluster.ClusterNodeSchedule;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.pipeline.run.RunSchedule;
import com.epam.pipeline.entity.pipeline.run.RunScheduledAction;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.manager.ObjectCreatorUtils;
import com.epam.pipeline.manager.pipeline.RunScheduleManager;
import com.epam.pipeline.manager.region.CloudRegionManager;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@DirtiesContext
@ContextConfiguration(classes = TestApplicationWithAclSecurity.class)
@Transactional
public class NodeScheduleManagerTest extends AbstractSpringTest {

    private static final String TIME_ZONE = "UTC";
    private static final String NODE_TYPE = "c5.xlarge";
    private static final int NODE_DISK = 50;
    private static final long CLOUD_REGION_ID = 1L;
    private static final String CRON_EXPRESSION = "* * * * * ?";
    private static final String USER_OWNER = "OWNER";
    private static final String CRON_EXPRESSION_2 = "*/2 * * * * ?";

    @Autowired
    private NodeScheduleManager nodeScheduleManager;

    @Autowired
    private RunScheduleManager runScheduleManager;

    @MockBean
    private CloudRegionManager cloudRegionManager;

    private ClusterNodeScheduleVO clusterNodeScheduleVO;

    @Before
    public void setup() {
        clusterNodeScheduleVO = new ClusterNodeScheduleVO();
        ClusterNodeScale clusterNodeScale = new ClusterNodeScale();
        clusterNodeScale.setNumberOfInstances(1);
        RunInstance instance = ObjectCreatorUtils.createRunInstance(NODE_TYPE, NODE_DISK, CLOUD_REGION_ID);
        clusterNodeScale.setInstance(instance);
        clusterNodeScheduleVO.setClusterNodeScale(clusterNodeScale);
        clusterNodeScheduleVO.setSchedule(Collections.singletonList(
                getPipelineRunScheduleVO(TIME_ZONE, CRON_EXPRESSION, RunScheduledAction.RUN)));

        Mockito.when(cloudRegionManager.loadOrDefault(Mockito.any())).thenReturn(new AwsRegion());
    }

    @Test
    @WithMockUser(username = USER_OWNER)
    public void createNodeSchedule() {
        nodeScheduleManager.createClusterNodeSchedule(clusterNodeScheduleVO);
        List<ClusterNodeSchedule> clusterNodeSchedules = nodeScheduleManager.loadClusterNodeSchedule();
        Assert.assertEquals(1, clusterNodeSchedules.size());
    }


    @Test
    @WithMockUser(username = USER_OWNER)
    public void loadAllNodeSchedule() {
        nodeScheduleManager.createClusterNodeSchedule(clusterNodeScheduleVO);
        nodeScheduleManager.createClusterNodeSchedule(clusterNodeScheduleVO);
        List<ClusterNodeSchedule> clusterNodeSchedules = nodeScheduleManager.loadClusterNodeSchedule();
        Assert.assertEquals(2, clusterNodeSchedules.size());
    }

    @Test
    @WithMockUser(username = USER_OWNER)
    public void updateBatchNodeSchedule() {
        ClusterNodeSchedule created = nodeScheduleManager.createClusterNodeSchedule(clusterNodeScheduleVO);
        ClusterNodeSchedule loaded = nodeScheduleManager.loadClusterNodeSchedule(
                created.getClusterNodeScale().getId()
        );
        Assert.assertEquals(1, loaded.getSchedule().size());
        Assert.assertEquals(CRON_EXPRESSION, loaded.getSchedule().get(0).getCronExpression());

        PipelineRunScheduleVO scheduleVO = clusterNodeScheduleVO.getSchedule().get(0);
        scheduleVO.setScheduleId(loaded.getSchedule().get(0).getId());
        scheduleVO.setCronExpression(CRON_EXPRESSION_2);
        nodeScheduleManager.updateClusterNodeSchedule(clusterNodeScheduleVO);

        loaded = nodeScheduleManager.loadClusterNodeSchedule(created.getClusterNodeScale().getId());
        Assert.assertEquals(1, loaded.getSchedule().size());
        Assert.assertEquals(CRON_EXPRESSION_2, loaded.getSchedule().get(0).getCronExpression());
    }

    @Test
    @WithMockUser(username = USER_OWNER)
    public void deleteBatchNodeSchedule() {
        ArrayList<PipelineRunScheduleVO> newSchedule = new ArrayList<>(clusterNodeScheduleVO.getSchedule());
        newSchedule.add(getPipelineRunScheduleVO(TIME_ZONE, CRON_EXPRESSION_2, RunScheduledAction.RUN));
        clusterNodeScheduleVO.setSchedule(newSchedule);

        ClusterNodeSchedule created = nodeScheduleManager.createClusterNodeSchedule(clusterNodeScheduleVO);
        ClusterNodeSchedule loaded = nodeScheduleManager.loadClusterNodeSchedule(
                created.getClusterNodeScale().getId()
        );
        Assert.assertEquals(2, loaded.getSchedule().size());
        Assert.assertEquals(2, runScheduleManager.loadAllSchedules().size());


        RunSchedule runScheduleToDelete = loaded.getSchedule().get(0);
        nodeScheduleManager.deleteClusterNodeSchedule(loaded.getClusterNodeScale().getId(), runScheduleToDelete.getId());

        loaded = nodeScheduleManager.loadClusterNodeSchedule(
                created.getClusterNodeScale().getId()
        );
        Assert.assertEquals(1, loaded.getSchedule().size());
        Assert.assertNotEquals(runScheduleToDelete.getId(), loaded.getSchedule().get(0).getId());
        Assert.assertEquals(1, runScheduleManager.loadAllSchedules().size());

    }

    @Test
    @WithMockUser(username = USER_OWNER)
    public void deleteAllBatchNodeSchedule() {
        ArrayList<PipelineRunScheduleVO> newSchedule = new ArrayList<>(clusterNodeScheduleVO.getSchedule());
        newSchedule.add(getPipelineRunScheduleVO("UTC", "*/2 * * * * ?", RunScheduledAction.RUN));
        clusterNodeScheduleVO.setSchedule(newSchedule);
        ClusterNodeSchedule created = nodeScheduleManager.createClusterNodeSchedule(clusterNodeScheduleVO);
        ClusterNodeSchedule loaded = nodeScheduleManager.loadClusterNodeSchedule(
                created.getClusterNodeScale().getId()
        );
        Assert.assertEquals(2, loaded.getSchedule().size());
        Assert.assertEquals(2, runScheduleManager.loadAllSchedules().size());

        nodeScheduleManager.deleteClusterNodeSchedule(loaded.getClusterNodeScale().getId());
        loaded = nodeScheduleManager.loadClusterNodeSchedule(
                created.getClusterNodeScale().getId()
        );
        Assert.assertNull(loaded);
        Assert.assertEquals(0, runScheduleManager.loadAllSchedules().size());
    }

    private PipelineRunScheduleVO getPipelineRunScheduleVO(final String timeZone,
                                                           final String cronExpression,
                                                           final RunScheduledAction action) {
        PipelineRunScheduleVO scheduleVO = new PipelineRunScheduleVO();
        scheduleVO.setTimeZone(timeZone);
        scheduleVO.setCronExpression(cronExpression);
        scheduleVO.setAction(action);
        return scheduleVO;
    }
}