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

    public static boolean isTimeOutEnds(NotificationTimestamp nt, int shift) {
        return nt != null && nt.getTimestamp() != null
                && DateUtils.nowUTC().isAfter(nt.getTimestamp().plus(shift, ChronoUnit.MINUTES));
    }

}
