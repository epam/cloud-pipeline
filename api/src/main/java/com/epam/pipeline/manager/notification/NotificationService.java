package com.epam.pipeline.manager.notification;

import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.cluster.monitoring.ELKUsageMetric;
import com.epam.pipeline.entity.datastorage.NFSStorageMountStatus;
import com.epam.pipeline.entity.datastorage.nfs.NFSDataStorage;
import com.epam.pipeline.entity.datastorage.nfs.NFSQuotaNotificationEntry;
import com.epam.pipeline.entity.datastorage.nfs.NFSQuotaNotificationRecipient;
import com.epam.pipeline.entity.issue.Issue;
import com.epam.pipeline.entity.issue.IssueComment;
import com.epam.pipeline.entity.notification.NotificationSettings;
import com.epam.pipeline.entity.notification.NotificationType;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public interface NotificationService {

    default void notifyLongRunningTask(PipelineRun run, Long duration, NotificationSettings settings) {

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
                                               final List<NFSQuotaNotificationRecipient> recipients) {

    }
}
