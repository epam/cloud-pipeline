package com.epam.pipeline.mapper.notification;

import com.epam.pipeline.dto.notification.UserNotification;
import com.epam.pipeline.dto.notification.UserNotificationResource;
import com.epam.pipeline.entity.notification.UserNotificationEntity;
import com.epam.pipeline.entity.notification.UserNotificationResourceEntity;
import org.apache.commons.collections4.ListUtils;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface UserNotificationMapper {

    UserNotificationEntity toEntity(UserNotification dto);

    @Mapping(target = "notification", ignore = true)
    UserNotificationResourceEntity toEntity(UserNotificationResource dto);

    default UserNotification toDto(UserNotificationEntity entity) {
        return new UserNotification(entity.getId(), entity.getType(), entity.getUserId(), entity.getSubject(),
                entity.getText(), entity.getCreatedDate(), entity.getIsRead(), entity.getReadDate(),
                ListUtils.emptyIfNull(entity.getResources()).stream()
                        .map(this::toDto)
                        .collect(Collectors.toList()));
    }

    default UserNotificationResource toDto(UserNotificationResourceEntity entity) {
        return new UserNotificationResource(entity.getId(), entity.getEntityClass(), entity.getEntityId(),
                entity.getStoragePath(), entity.getStorageRuleId());
    }
}
