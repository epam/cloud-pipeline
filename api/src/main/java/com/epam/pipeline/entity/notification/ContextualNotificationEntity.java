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

import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
