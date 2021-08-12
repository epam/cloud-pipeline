package com.epam.pipeline.mapper.notification;

import com.epam.pipeline.dto.notification.ContextualNotification;
import com.epam.pipeline.entity.notification.ContextualNotificationEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ContextualNotificationMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "created", ignore = true)
    ContextualNotificationEntity toEntity(ContextualNotification notification);

    default ContextualNotification toDto(ContextualNotificationEntity entity) {
        return new ContextualNotification(entity.getId(), entity.getType(), entity.getTriggerId(),
                entity.getTriggerStatuses(), entity.getRecipients(), entity.getSubject(), entity.getBody(),
                entity.getCreated());
    }
}
