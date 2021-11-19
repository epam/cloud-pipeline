package com.epam.pipeline.entity.utils;

import com.epam.pipeline.entity.notification.NotificationType;

import javax.persistence.AttributeConverter;

public class NotificationTypeConverter implements AttributeConverter<NotificationType, Long>  {

    @Override
    public Long convertToDatabaseColumn(final NotificationType attribute) {
        return attribute.getId();
    }

    @Override
    public NotificationType convertToEntityAttribute(final Long dbData) {
        return NotificationType.getById(dbData);
    }
}
