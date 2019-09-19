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

package com.epam.pipeline.manager.notification;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.controller.vo.EntityVO;
import com.epam.pipeline.controller.vo.MetadataVO;
import com.epam.pipeline.controller.vo.SystemNotificationFilterVO;
import com.epam.pipeline.dao.notification.NotificationDao;
import com.epam.pipeline.entity.metadata.MetadataEntry;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import com.epam.pipeline.entity.metadata.PipeConfValueType;
import com.epam.pipeline.entity.notification.SystemNotification;
import com.epam.pipeline.entity.notification.SystemNotificationConfirmation;
import com.epam.pipeline.entity.notification.SystemNotificationConfirmationRequest;
import com.epam.pipeline.entity.notification.SystemNotificationSeverity;
import com.epam.pipeline.entity.notification.SystemNotificationState;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.manager.metadata.MetadataManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.security.AuthManager;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SystemNotificationManager {

    private static final String DEFAULT_SYSTEM_EVENTS_METADATA_KEY = "confirmed_notifications";

    private final NotificationDao notificationDao;
    private final MessageHelper messageHelper;
    private final MetadataManager metadataManager;
    private final AuthManager authManager;
    private final PreferenceManager preferenceManager;

    @Transactional(propagation = Propagation.REQUIRED)
    public SystemNotification createOrUpdateNotification(SystemNotification notification) {
        if (notification.getNotificationId() == null) {
            return this.createNotification(notification);
        } else {
            return this.updateNotification(notification);
        }
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public SystemNotification createNotification(SystemNotification notification) {
        Assert.notNull(
                notification.getTitle(),
                messageHelper.getMessage(MessageConstants.ERROR_NOTIFICATION_TITLE_REQUIRED)
        );
        if (notification.getSeverity() == null) {
            notification.setSeverity(SystemNotificationSeverity.INFO);
        }
        if (notification.getState() == null) {
            notification.setState(SystemNotificationState.INACTIVE);
        }
        notification.setCreatedDate(new Date());
        return notificationDao.createNotification(notification);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public SystemNotification updateNotification(SystemNotification notification) {
        SystemNotification dbNotification = notificationDao.loadNotification(notification.getNotificationId());
        Assert.notNull(
                dbNotification,
                messageHelper.getMessage(
                        MessageConstants.ERROR_NOTIFICATION_NOT_FOUND,
                        notification.getNotificationId()
                )
        );
        Assert.notNull(
                notification.getTitle(),
                messageHelper.getMessage(MessageConstants.ERROR_NOTIFICATION_TITLE_REQUIRED)
        );
        dbNotification.setTitle(notification.getTitle());
        dbNotification.setBody(notification.getBody());
        dbNotification.setCreatedDate(new Date());
        dbNotification.setBlocking(notification.getBlocking());
        if (notification.getState() != null) {
            dbNotification.setState(notification.getState());
        }
        if (notification.getSeverity() != null) {
            dbNotification.setSeverity(notification.getSeverity());
        }
        return notificationDao.updateNotification(dbNotification);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public SystemNotification deleteNotification(Long id) {
        SystemNotification dbNotification = notificationDao.loadNotification(id);
        Assert.notNull(
                dbNotification,
                messageHelper.getMessage(
                        MessageConstants.ERROR_NOTIFICATION_NOT_FOUND,
                        dbNotification.getNotificationId()
                )
        );
        notificationDao.deleteNotification(id);
        return dbNotification;
    }

    public SystemNotification loadNotification(Long id) {
        SystemNotification dbNotification = notificationDao.loadNotification(id);
        Assert.notNull(
                dbNotification,
                messageHelper.getMessage(
                        MessageConstants.ERROR_NOTIFICATION_NOT_FOUND,
                        dbNotification.getNotificationId()
                )
        );
        return dbNotification;
    }

    public List<SystemNotification> loadAllNotifications() {
        return notificationDao.loadAllNotifications();
    }

    public List<SystemNotification> filterNotifications(SystemNotificationFilterVO filterVO) {
        if (filterVO == null) {
            return this.loadAllNotifications();
        }
        return notificationDao.filterNotifications(filterVO);
    }

    public List<SystemNotification> loadActiveNotifications(Date after) {
        SystemNotificationFilterVO filterVO = new SystemNotificationFilterVO();
        filterVO.setStateList(Collections.singletonList(SystemNotificationState.ACTIVE));
        filterVO.setCreatedDateAfter(after);
        return this.filterNotifications(filterVO);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public SystemNotificationConfirmation confirmNotification(final SystemNotificationConfirmationRequest request) {
        Assert.notNull(
                request.getNotificationId(),
                messageHelper.getMessage(MessageConstants.ERROR_NOTIFICATION_ID_REQUIRED)
        );
        Assert.notNull(
                request.getTitle(),
                messageHelper.getMessage(MessageConstants.ERROR_NOTIFICATION_TITLE_REQUIRED)
        );
        final SystemNotification dbNotification = notificationDao.loadNotification(request.getNotificationId());
        Assert.notNull(
                dbNotification,
                messageHelper.getMessage(
                        MessageConstants.ERROR_NOTIFICATION_NOT_FOUND,
                        dbNotification.getNotificationId()
                )
        );
        final PipelineUser user = authManager.getCurrentUser();
        Assert.notNull(user, messageHelper.getMessage(MessageConstants.ERROR_USER_NOT_AUTHORIZED));
        final SystemNotificationConfirmation confirmation = request.toConfirmation(user.getUserName());
        final MetadataEntry metadataEntry = Optional
                .ofNullable(metadataManager.loadMetadataItem(user.getId(), AclClass.PIPELINE_USER))
                .orElseGet(() -> toMetadataEntity(user.getId()));
        return storeConfirmation(metadataEntry, confirmation);
    }

    private MetadataEntry toMetadataEntity(final Long userId) {
        final MetadataEntry entry = new MetadataEntry();
        entry.setEntity(new EntityVO(userId, AclClass.PIPELINE_USER));
        return entry;
    }

    private SystemNotificationConfirmation storeConfirmation(final MetadataEntry metadataEntry,
                                                             final SystemNotificationConfirmation confirmation) {
        final String confirmationsKey = Optional.ofNullable(preferenceManager.getStringPreference(
                SystemPreferences.MISC_SYSTEM_EVENTS_CONFIRMATION_METADATA_KEY.getKey()))
                .orElse(DEFAULT_SYSTEM_EVENTS_METADATA_KEY);
        final PipeConfValue updatedValue = Optional.ofNullable(metadataEntry.getData())
                .map(data -> data.get(confirmationsKey))
                .map(value -> appendMetadataValue(value, confirmation))
                .orElseGet(() -> toMetadataValue(confirmation));
        Optional.ofNullable(metadataEntry.getEntity())
                .map(entityVO -> toMetadataVO(entityVO, confirmationsKey, updatedValue))
                .ifPresent(metadataManager::updateMetadataItemKey);
        return confirmation;
    }

    private MetadataVO toMetadataVO(final EntityVO entityVO, final String key, final PipeConfValue value) {
        final MetadataVO metadataVO = new MetadataVO();
        metadataVO.setEntity(entityVO);
        metadataVO.setData(Collections.singletonMap(key, value));
        return metadataVO;
    }

    private PipeConfValue appendMetadataValue(final PipeConfValue existingValue,
                                              final SystemNotificationConfirmation confirmation) {
        try {
            final List<SystemNotificationConfirmation> confirmations = new ArrayList<>(toConfirmations(existingValue));
            confirmations.add(confirmation);
            return toMetadataValue(confirmations);
        } catch (IllegalArgumentException e) {
            log.error(String.format("System notification confirmations parsing has failed. " +
                    "All existing confirmations will be cleaned for the user %s.", confirmation.getUser()), e);
            return toMetadataValue(confirmation);
        }
    }

    private List<SystemNotificationConfirmation> toConfirmations(final PipeConfValue existingValue) {
        return Optional.of(existingValue)
                .map(PipeConfValue::getValue)
                .map(this::toConfirmations)
                .orElseGet(Collections::emptyList);
    }

    private List<SystemNotificationConfirmation> toConfirmations(final String confirmationsJson) {
        return JsonMapper.parseData(confirmationsJson, new TypeReference<List<SystemNotificationConfirmation>>() {});
    }

    private PipeConfValue toMetadataValue(final SystemNotificationConfirmation confirmation) {
        return toMetadataValue(Collections.singletonList(confirmation));
    }

    private PipeConfValue toMetadataValue(final List<SystemNotificationConfirmation> confirmations) {
        return new PipeConfValue(PipeConfValueType.JSON.toString(),
                JsonMapper.convertDataToJsonStringForQuery(confirmations));
    }
}
