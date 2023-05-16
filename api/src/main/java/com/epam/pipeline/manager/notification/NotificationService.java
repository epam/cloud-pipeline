/*
 * Copyright 2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.notification;

import com.epam.pipeline.dto.quota.AppliedQuota;
import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.cluster.monitoring.ELKUsageMetric;
import com.epam.pipeline.entity.cluster.pool.NodePool;
import com.epam.pipeline.entity.datastorage.NFSStorageMountStatus;
import com.epam.pipeline.entity.datastorage.nfs.NFSDataStorage;
import com.epam.pipeline.entity.datastorage.nfs.NFSQuotaNotificationEntry;
import com.epam.pipeline.entity.datastorage.nfs.NFSQuotaNotificationRecipient;
import com.epam.pipeline.entity.issue.Issue;
import com.epam.pipeline.entity.issue.IssueComment;
import com.epam.pipeline.entity.notification.NotificationSettings;
import com.epam.pipeline.entity.notification.NotificationType;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.user.PipelineUser;
import org.apache.commons.lang3.tuple.Pair;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public interface NotificationService {

    default void notifyLongRunningTask(PipelineRun run, Long duration, final NotificationType type,
                                       NotificationSettings settings) {

    }

    default void notifyIssue(Issue issue, AbstractSecuredEntity entity, String htmlText) {

    }

    default void notifyIssueComment(IssueComment comment, Issue issue, String htmlText) {

    }

    default void notifyRunStatusChanged(PipelineRun pipelineRun) {

    }

    default void notifyIdleRuns(List<Pair<PipelineRun, Double>> pipelineCpuRatePairs,
                                NotificationType notificationType) {

    }

    default void notifyHighResourceConsumingRuns(
            List<Pair<PipelineRun, Map<ELKUsageMetric, Double>>> pipelinesMetrics,
            NotificationType notificationType) {

    }

    default void notifyStuckInStatusRuns(List<PipelineRun> runs) {

    }

    default void notifyLongPausedRuns(List<PipelineRun> pausedRuns) {

    }

    default List<PipelineRun> notifyLongPausedRunsBeforeStop(List<PipelineRun> pausedRuns) {
        return Collections.emptyList();
    }

    default void notifyOnStorageQuotaExceeding(final NFSDataStorage storage,
                                               final NFSStorageMountStatus newStatus,
                                               final NFSQuotaNotificationEntry exceededQuota,
                                               final List<NFSQuotaNotificationRecipient> recipients,
                                               final LocalDateTime activationTime) {

    }

    default void notifyPipelineUsers(final List<PipelineUser> inactiveUsers, final NotificationType type) {
    }

    default void notifyFullNodePools(List<NodePool> nodePools) {
    }

    default void notifyOnBillingQuotaExceeding(final AppliedQuota appliedQuota) {

    }
}
