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

package com.epam.pipeline.entity.notification;

import com.epam.pipeline.entity.pipeline.TaskStatus;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Represents settings for System Notifications. Notifications are submitted from Pipeline API via NotificationManger
 * for various actions: long running pipelines, new issues, etc. They are being stored to the database and later are
 * picked up by NotificationService (see notifier module).
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
public class NotificationSettings {

    private Long id;
    private long templateId;

    /**
     * Relevant only for LONG_RUNNING and LONG_INIT notification type. Represents time delay (in ms), starting from
     * which a PipelineRun is considered "long running" and therefore a notification should be sent.
     * Could have value bigger that 0 or -1 to show that threshold value aren't applied here.
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

    private List<TaskStatus> statusesToInform;

    private boolean keepInformedAdmins;
    private boolean keepInformedOwner;
    private NotificationType type;
    private boolean enabled;

    public static NotificationSettings getDefault(final NotificationType type) {
        final NotificationSettings settings = new NotificationSettings();
        settings.setId(type.getId());
        settings.setTemplateId(type.getId());
        settings.setType(type);
        settings.setStatusesToInform(type.getDefaultStatusesToInform());
        settings.setEnabled(type.isEnabled());
        settings.setThreshold(type.getDefaultThreshold());
        settings.setResendDelay(type.getDefaultResendDelay());
        return settings;
    }

}
