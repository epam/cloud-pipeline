package com.epam.pipeline.entity.notification;

import com.epam.pipeline.entity.pipeline.TaskStatus;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Represents types of System Notification.
 * NOTE: defaultThreshold field could have value bigger that 0 or -1
 * to show that threshold value aren't applied here.
 */
public enum NotificationType {

    LONG_RUNNING(1, 3600L, 600L, Collections.emptyList(), true,
            NotificationGroup.LONG_RUNNING),
    LONG_INIT(2, 3600L, 600L, Collections.emptyList(), true,
            NotificationGroup.LONG_RUNNING),
    NEW_ISSUE(3, -1L, -1L, Collections.emptyList(), true,
            NotificationGroup.ISSUE),
    NEW_ISSUE_COMMENT(4, -1L, -1L, Collections.emptyList(), true,
            NotificationGroup.ISSUE),
    PIPELINE_RUN_STATUS(5, -1L, -1L,
            Arrays.asList(TaskStatus.SUCCESS, TaskStatus.FAILURE), true, NotificationGroup.PIPELINE_RUN_STATUS),
    IDLE_RUN(6, -1L, -1L, Collections.emptyList(), true,
            NotificationGroup.IDLE_RUN),
    IDLE_RUN_PAUSED(7, -1L, -1L, Collections.emptyList(), true,
            NotificationGroup.IDLE_RUN),
    IDLE_RUN_STOPPED(8, -1L, -1L, Collections.emptyList(), true,
            NotificationGroup.IDLE_RUN),
    HIGH_CONSUMED_RESOURCES(9, -1L, 600L, Collections.emptyList(), true,
            NotificationGroup.RESOURCE_CONSUMING),
    LONG_STATUS(10, 3600L, 600L, Collections.emptyList(), true,
            NotificationGroup.LONG_STATUS),
    LONG_PAUSED(11, -1L, -1L, Collections.emptyList(), true,
            NotificationGroup.LONG_PAUSED),
    LONG_PAUSED_STOPPED(12, -1L, -1L, Collections.emptyList(), true,
            NotificationGroup.LONG_PAUSED);

    private static final Map<Long, NotificationType> BY_ID;

    static {
        BY_ID = Arrays.stream(values()).collect(Collectors.toMap(NotificationType::getId, t -> t));
    }

    private final long id;
    private final Long defaultThreshold;
    private final Long defaultResendDelay;
    private final List<TaskStatus> defaultStatusesToInform;
    private final boolean enabled;
    private final NotificationGroup group;

    NotificationType(final long id, Long defaultThreshold, final Long defaultResendDelay,
                     final List<TaskStatus> defaultStatusesToInform, final boolean enabled,
                     final NotificationGroup group) {
        this.id = id;
        this.defaultThreshold = defaultThreshold;
        this.defaultResendDelay = defaultResendDelay;
        this.defaultStatusesToInform = defaultStatusesToInform;
        this.enabled = enabled;
        this.group = group;
    }

    public Long getDefaultThreshold() {
        return defaultThreshold;
    }

    public Long getDefaultResendDelay() {
        return defaultResendDelay;
    }

    public List<TaskStatus> getDefaultStatusesToInform() {
        return defaultStatusesToInform;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public static NotificationType getById(final long id) {
        return BY_ID.get(id);
    }

    public long getId() {
        return id;
    }

    public NotificationGroup getGroup() {
        return group;
    }
}
