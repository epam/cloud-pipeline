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

package com.epam.pipeline.manager.scheduling.provider;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dao.cluster.ClusterNodeScaleDao;
import com.epam.pipeline.entity.cluster.ClusterNodeScale;
import com.epam.pipeline.entity.pipeline.run.RunScheduledAction;
import com.epam.pipeline.entity.pipeline.run.ScheduleType;
import com.epam.pipeline.manager.scheduling.ClusterNodeScaleJob;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

@Service
@RequiredArgsConstructor
public class NodeScheduleProvider implements ScheduleProvider {

    @Autowired
    private final MessageHelper messageHelper;

    @Autowired
    private final ClusterNodeScaleDao clusterNodeScaleDao;


    @Override
    public ScheduleType getScheduleType() {
        return ScheduleType.CLUSTER_NODE_SCALE;
    }

    @Override
    public void verifyScheduleAction(final Long schedulableId, final RunScheduledAction action) {
        Assert.isTrue(action == RunScheduledAction.RUN || action == RunScheduledAction.STOP,
                messageHelper.getMessage(MessageConstants.SCHEDULE_ACTION_IS_NOT_ALLOWED,
                        RunScheduledAction.RUN.name() + ", " + RunScheduledAction.STOP.name(), action));
    }

    @Override
    public void verifySchedulable(final Long schedulableId) {
        final ClusterNodeScale configuration = clusterNodeScaleDao.loadClusterNodeScale(schedulableId);
        Assert.notNull(configuration,
                messageHelper.getMessage(MessageConstants.ERROR_RUN_CONFIG_NOT_FOUND, schedulableId));
    }

    @Override
    public Class<?> getScheduleJobClass() {
        return ClusterNodeScaleJob.class;
    }

    @Override
    public MessageHelper getMessageHelper() {
        return messageHelper;
    }
}
