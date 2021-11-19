package com.epam.pipeline.entity.notification;

import com.epam.pipeline.entity.utils.LongsListConverter;
import com.epam.pipeline.entity.utils.NotificationTypeConverter;
import com.epam.pipeline.entity.utils.RunStatusesListConverter;
import com.epam.pipeline.entity.utils.TimestampConverter;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Getter
@Setter
@Builder
@Table(name = "contextual_notification", schema = "pipeline")
@NoArgsConstructor
@AllArgsConstructor
public class ContextualNotificationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Convert(converter = NotificationTypeConverter.class)
    private NotificationType type;

    @Convert(converter = LongsListConverter.class)
    private List<Long> recipients;

    private Long triggerId;

    @Convert(converter = RunStatusesListConverter.class)
    private List<TaskStatus> triggerStatuses;

    private String subject;

    private String body;

    @Convert(converter = TimestampConverter.class)
    private LocalDateTime created;

}
