/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Represents settings for System Notifications. Notifications are submitted from Pipeline API via NotificationManger
 * for various actions: long running pipelines, new issues, etc. They are being stored to the database and later are
 * picked up by NotificationService (see notifier module).
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class NotificationSettings {

    private static final long MISSING_TIME_THRESHOLD = -1L;

    private Long id;
    private long templateId;

    /**
     * Relevant only for LONG_RUNNING and LONG_INIT notification type. Represents time delay (in ms), starting from
     * which a PipelineRun is considered "long running" and therefore a notification should be sent.
     */
    private Long threshold;
    /**
     * Relevant only for LONG_RUNNING and LONG_INIT notification type. Represents time delay between first notification
     * on a long running or long initializing PipelineRun and additional notifications sent.
     */
    private Long resendDelay;

    /**
     * Contains IDs of pipeline user's, that should be explicitly notified by a notification with this settings
     */
    private List<Long> informedUserIds;
    private boolean keepInformedAdmins;
    private boolean keepInformedOwner;
    private NotificationType type;

    public static NotificationSettings getDefault(NotificationType type) {
        NotificationSettings settings = new NotificationSettings();
        settings.setId(type.getId());
        settings.setTemplateId(type.getId());
        settings.setType(type);
        settings.setThreshold(type.getDefaultThreshold());
        settings.setResendDelay(type.getDefaultResendDelay());
        return settings;
    }

    public enum NotificationType {

        LONG_RUNNING(1, 3600L, 600L, NotificationGroup.LONG_RUNNING),
        LONG_INIT(2, 3600L, 600L, NotificationGroup.LONG_RUNNING),
        NEW_ISSUE(3, MISSING_TIME_THRESHOLD, MISSING_TIME_THRESHOLD, NotificationGroup.ISSUE),
        NEW_ISSUE_COMMENT(4, MISSING_TIME_THRESHOLD, MISSING_TIME_THRESHOLD, NotificationGroup.ISSUE),
        PIPELINE_RUN_STATUS(5, MISSING_TIME_THRESHOLD, MISSING_TIME_THRESHOLD, NotificationGroup.PIPELINE_RUN_STATUS),
        IDLE_RUN(6, MISSING_TIME_THRESHOLD, MISSING_TIME_THRESHOLD, NotificationGroup.IDLE_RUN),
        IDLE_RUN_PAUSED(7, MISSING_TIME_THRESHOLD, MISSING_TIME_THRESHOLD, NotificationGroup.IDLE_RUN),
        IDLE_RUN_STOPPED(8, MISSING_TIME_THRESHOLD, MISSING_TIME_THRESHOLD, NotificationGroup.IDLE_RUN),
        HIGH_CONSUMED_RESOURCES(9, MISSING_TIME_THRESHOLD, 600L,  NotificationGroup.RESOURCE_CONSUMING),
        LONG_STATUS(10, 3600L, 600L, NotificationGroup.LONG_STATUS);


        private static final Map<Long, NotificationType> BY_ID;

        static {
            BY_ID = Arrays.stream(values()).collect(Collectors.toMap(NotificationType::getId, t -> t));
        }

        final long id;
        final Long defaultThreshold;
        final Long defaultResendDelay;
        final NotificationGroup group;

        NotificationType(long id, Long defaultThreshold, Long defaultResendDelay, NotificationGroup group) {
            this.id = id;
            this.defaultThreshold = defaultThreshold;
            this.defaultResendDelay = defaultResendDelay;
            this.group = group;
        }
        public Long getDefaultThreshold() {
            return defaultThreshold;
        }

        public Long getDefaultResendDelay() {
            return defaultResendDelay;
        }

        public static NotificationType getById(long id) {
            return BY_ID.get(id);
        }

        public long getId() {
            return id;
        }

        public NotificationGroup getGroup() {
            return group;
        }
    }

    public enum NotificationGroup {
        LONG_RUNNING,
        ISSUE,
        PIPELINE_RUN_STATUS,
        IDLE_RUN,
        RESOURCE_CONSUMING,
        LONG_STATUS
    }
}
