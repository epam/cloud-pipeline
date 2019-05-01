package com.epam.pipeline.entity.notification;

import com.epam.pipeline.entity.notification.NotificationSettings.NotificationType;
import com.epam.pipeline.entity.utils.DateUtils;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Data
public class NotificationTimestamp {

    private Long runId;
    private NotificationType type;
    private LocalDateTime timestamp;

    public static boolean isTimeoutEnds(final NotificationTimestamp nt, final long timeout, ChronoUnit unit) {
        // if it already was sent once and resendDelay <= 0 we won't send it again
        return timeout > 0
                && nt.getTimestamp() != null
                && DateUtils.nowUTC().isAfter(nt.getTimestamp().plus(timeout, unit));
    }

}
