package com.epam.pipeline.manager.notification;

import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.dto.quota.AppliedQuota;
import com.epam.pipeline.dto.quota.Quota;
import com.epam.pipeline.dto.quota.QuotaAction;
import com.epam.pipeline.entity.cluster.monitoring.ELKUsageMetric;
import com.epam.pipeline.entity.cluster.pool.NodePool;
import com.epam.pipeline.entity.datastorage.NFSStorageMountStatus;
import com.epam.pipeline.entity.datastorage.nfs.NFSDataStorage;
import com.epam.pipeline.entity.datastorage.nfs.NFSQuotaNotificationEntry;
import com.epam.pipeline.entity.issue.Issue;
import com.epam.pipeline.entity.issue.IssueComment;
import com.epam.pipeline.entity.notification.NotificationEntityClass;
import com.epam.pipeline.entity.notification.NotificationParameter;
import com.epam.pipeline.entity.notification.NotificationType;
import com.epam.pipeline.entity.notification.UserNotificationResourceEntity;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.mapper.IssueMapper;
import com.epam.pipeline.mapper.PipelineRunMapper;
import com.epam.pipeline.mapper.cluster.pool.NodePoolMapper;
import com.epam.pipeline.mapper.user.UserMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.MapUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationParameterManager {

    private static final double PERCENT = 100.0;

    private final JsonMapper jsonMapper;

    public Map<String, Object> build(final NotificationType type, final PipelineRun run) {
        final Map<String, Object> parameters = build(type);
        parameters.putAll(buildEntities(NotificationEntityClass.RUN, run.getId()));
        parameters.putAll(PipelineRunMapper.map(run));
        return parameters;
    }

    public Map<String, Object> build(final NotificationType type, final PipelineRun run, final Long duration,
                                     final Long threshold) {
        final Map<String, Object> parameters = build(type);
        parameters.putAll(buildEntities(NotificationEntityClass.RUN, run.getId()));
        parameters.putAll(PipelineRunMapper.map(run, threshold, duration));
        return parameters;
    }

    public Map<String, Object> build(final NotificationType type, final PipelineRun run,
                                     final Long threshold) {
        final Map<String, Object> parameters = build(type);
        parameters.putAll(buildEntities(NotificationEntityClass.RUN, run.getId()));
        parameters.putAll(PipelineRunMapper.map(run, threshold));
        return parameters;
    }

    public Map<String, Object> build(final NotificationType type, final Issue issue) {
        final Map<String, Object> parameters = build(type);
        parameters.putAll(buildEntities(NotificationEntityClass.ISSUE, issue.getId()));
        parameters.putAll(IssueMapper.map(issue, jsonMapper));
        return parameters;
    }

    public Map<String, Object> build(final NotificationType type, final Issue issue,
                                     final IssueComment comment) {
        final Map<String, Object> parameters = build(type);
        parameters.putAll(buildEntities(NotificationEntityClass.ISSUE, issue.getId()));
        parameters.putAll(IssueMapper.map(comment, jsonMapper));
        parameters.put("issue", IssueMapper.map(issue, jsonMapper));
        return parameters;
    }

    public Map<String, Object> build(final NotificationType type, final PipelineRun run,
                                     final double cpuRate, final double idleCpuLevel) {
        final Map<String, Object> parameters = build(type);
        parameters.putAll(buildEntities(NotificationEntityClass.RUN, run.getId()));
        parameters.putAll(PipelineRunMapper.map(run));
        parameters.put("idleCpuLevel", idleCpuLevel);
        parameters.put("cpuRate", cpuRate * PERCENT);
        return parameters;
    }

    public Map<String, Object> build(final NotificationType type,
                                     final PipelineRun run,
                                     final Map<ELKUsageMetric, Double> metrics,
                                     final double memThreshold,
                                     final double diskThreshold) {
        final Map<String, Object> parameters = build(type);
        parameters.putAll(buildEntities(NotificationEntityClass.RUN, run.getId()));
        parameters.putAll(PipelineRunMapper.map(run));
        parameters.put("memoryThreshold", memThreshold);
        parameters.put("memoryRate", metrics.getOrDefault(ELKUsageMetric.MEM, 0.0) * PERCENT);
        parameters.put("diskThreshold", diskThreshold);
        parameters.put("diskRate", metrics.getOrDefault(ELKUsageMetric.FS, 0.0) * PERCENT);
        return parameters;
    }

    public Map<String, Object> build(final NotificationType type, final NFSDataStorage storage,
                                     final NFSQuotaNotificationEntry quota,
                                     final NFSStorageMountStatus newStatus,
                                     final LocalDateTime activationTime) {
        final Map<String, Object> parameters = build(type);
        parameters.putAll(buildEntities(NotificationEntityClass.STORAGE, storage.getId()));
        parameters.put("storageId", storage.getId());
        parameters.put("storageName", storage.getName());
        parameters.put("threshold", NFSQuotaNotificationEntry.NO_ACTIVE_QUOTAS_NOTIFICATION.equals(quota)
                ? "no_active_quotas"
                : quota.toThreshold());
        parameters.put("previousMountStatus", storage.getMountStatus());
        parameters.put("newMountStatus", newStatus);
        parameters.put("activationTime", activationTime);
        return parameters;
    }

    public Map<String, Object> build(final NotificationType type, final AppliedQuota appliedQuota) {
        final Quota quota = appliedQuota.getQuota();
        final QuotaAction action = appliedQuota.getAction();
        final Map<String, Object> parameters = build(type);
        parameters.putAll(buildEntities(NotificationEntityClass.QUOTA, appliedQuota.getId()));
        parameters.put("expense", appliedQuota.getExpense());
        parameters.put("from", appliedQuota.getFrom());
        parameters.put("to", appliedQuota.getTo());
        parameters.put("group", quota.getQuotaGroup());
        parameters.put("quota", quota.getValue());
        parameters.put("type", quota.getType());
        parameters.put("subject", quota.getSubject());
        parameters.put("actions", action.getActions().stream().map(Enum::name).collect(Collectors.joining(",")));
        parameters.put("threshold", action.getThreshold());
        return parameters;
    }

    public Map<String, Object> build(final NotificationType type, final List<PipelineUser> users,
                                     final Map<Long, String> userStorages) {
        final Map<String, Object> parameters = build(type);
        parameters.putAll(buildEntities(NotificationEntityClass.USER, users.stream()
                .map(PipelineUser::getId)
                .collect(Collectors.toList())));
        parameters.put("pipelineUsers", users.stream()
                .map(user -> UserMapper.map(user, userStorages))
                .collect(Collectors.toList()));
        return parameters;
    }

    public Map<String, Object> build(final NotificationType type, final List<NodePool> pools) {
        final Map<String, Object> parameters = build(type);
        parameters.putAll(buildEntities(NotificationEntityClass.NODE_POOL, pools.stream()
                .map(NodePool::getId)
                .collect(Collectors.toList())));
        parameters.put("pools", pools.stream()
                .map(pool -> NodePoolMapper.map(pool, jsonMapper))
                .collect(Collectors.toList()));
        return parameters;
    }

    private Map<String, Object> build(final NotificationType type) {
        final Map<String, Object> parameters = new HashMap<>();
        parameters.put(NotificationParameter.TYPE.getKey(), type);
        return parameters;
    }

    private Map<String, Object> buildEntities(final NotificationEntityClass entityClass,
                                              final Long entityId) {
        return buildEntities(entityClass, Collections.singletonList(entityId));
    }

    private Map<String, Object> buildEntities(final NotificationEntityClass entityClass,
                                              final List<Long> entityIds) {
        final Map<String, Object> parameters = new HashMap<>();
        parameters.put(NotificationParameter.RESOURCES.getKey(), entityIds.stream()
                .map(entityId -> toResource(entityClass, entityId))
                .map(this::toString)
                .collect(Collectors.toList()));
        return parameters;
    }

    private UserNotificationResourceEntity toResource(final NotificationEntityClass entityClass,
                                                      final Long entityId) {
        return new UserNotificationResourceEntity(null, null, entityClass, entityId, null, null);
    }

    private Map<String, Object> toString(final UserNotificationResourceEntity entity) {
        return MapUtils.emptyIfNull(jsonMapper.convertValue(entity, new TypeReference<Map<String, Object>>() {}));
    }
}
