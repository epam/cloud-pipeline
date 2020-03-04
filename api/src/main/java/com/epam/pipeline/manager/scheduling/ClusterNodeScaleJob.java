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

package com.epam.pipeline.manager.scheduling;

import com.epam.pipeline.entity.cluster.ClusterNodeScaleAction;
import com.epam.pipeline.entity.cluster.ClusterNodeSchedule;
import com.epam.pipeline.entity.pipeline.run.RunScheduledAction;
import com.epam.pipeline.manager.cluster.NodeScheduleManager;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.Queue;

@Component
@Slf4j
public class ClusterNodeScaleJob implements Job {

    @Autowired
    private Queue<ClusterNodeScaleAction> clusterNodeScaleQueue;

    @Autowired
    private NodeScheduleManager nodeScheduleManager;

    @Override
    public void execute(final JobExecutionContext context) {
        log.debug("Job " + context.getJobDetail().getKey().getName() + " fired " + context.getFireTime());

        final String action = context.getMergedJobDataMap().getString("Action");
        final Long clusterNodeScaleId = context.getMergedJobDataMap().getLongValue("SchedulableId");
        Assert.isTrue(action.equals(RunScheduledAction.RUN.name()) || action.equals(RunScheduledAction.STOP.name()),
                "Action for ClusterNodeScaleJob could be RUN or STOP, provided: " + action);
        ClusterNodeSchedule clusterNodeSchedule = nodeScheduleManager.loadClusterNodeSchedule(clusterNodeScaleId);
        Assert.notNull(clusterNodeSchedule, "Cluster node scale with id: "
                + clusterNodeScaleId + " not found!");

        final String user = context.getMergedJobDataMap().getString("User");
        Assert.notNull(user, "User is not provided for job: " + context.getJobDetail().getKey().getName());
        final ClusterNodeScaleAction clusterNodeScaleAction = new ClusterNodeScaleAction(
                clusterNodeSchedule.getClusterNodeScale(),
                RunScheduledAction.valueOf(action),
                user
        );

        log.debug("Adding new task to queue for AutoscalerManager, with action: " + clusterNodeScaleAction.getAction());
        clusterNodeScaleQueue.add(clusterNodeScaleAction);
        log.debug("Next job scheduled " + context.getNextFireTime());
    }
}
