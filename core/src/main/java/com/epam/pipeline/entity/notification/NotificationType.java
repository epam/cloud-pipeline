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
            NotificationGroup.LONG_PAUSED),
    STORAGE_QUOTA_EXCEEDING(13, -1L, -1L, Collections.emptyList(), true,
                NotificationGroup.RESOURCE_CONSUMING),
    INACTIVE_USERS(14, -1L, -1L, Collections.emptyList(), true,
            NotificationGroup.USER),
    BILLING_QUOTA_EXCEEDING(15, -1L, -1L, Collections.emptyList(), true,
            NotificationGroup.RESOURCE_CONSUMING),
    FULL_NODE_POOL(16, -1L, -1L, Collections.emptyList(), true,
            NotificationGroup.NODE_POOL),
    DATASTORAGE_LIFECYCLE_ACTION(17, -1L, -1L, Collections.emptyList(), true,
            NotificationGroup.DATASTORAGE_LIFECYCLE),
    DATASTORAGE_LIFECYCLE_RESTORE_ACTION(18, -1L, -1L, Collections.emptyList(), true,
            NotificationGroup.DATASTORAGE_LIFECYCLE),
    LDAP_BLOCKED_USERS(19, -1L, -1L, Collections.emptyList(), true,
            NotificationGroup.USER),
    LDAP_BLOCKED_POSTPONED_USERS(20, -1L, -1L, Collections.emptyList(), true,
            NotificationGroup.USER);

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
