/*
 * Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.notifier.service.task;

import com.epam.pipeline.entity.notification.NotificationMessage;
import com.epam.pipeline.entity.notification.NotificationMessageDlq;
import com.epam.pipeline.notifier.AbstractSpringTest;
import com.epam.pipeline.notifier.repository.NotificationDlqRepository;
import com.epam.pipeline.notifier.util.MessageToDlqConverter;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class NotificationDLQAwareServiceTest extends AbstractSpringTest {

    private static final Long MESSAGE_ID = 100L;
    private static final Long USER_ID = 43L;
    private static final String MESSAGE_SUBJECT = "Mock Message Subject";
    private static final String MESSAGE_BODY = "Mock Message Body";
    private static final String ERROR_MESSAGE = "Couldn't send the message";
    private static final Map<String, Object> TEMPLATE_PARAMS = Map.of("k1", "v1", "k12", "v12");
    private static final List<Long> COPY_USERS_ID = List.of(2L, 3L);

    @Autowired
    private NotificationDLQAwareService serviceUnderTest;

    @MockBean
    private SMTPNotificationManager smtpNotificationManager;

    @SpyBean
    private NotificationDlqRepository notificationDlqRepository;

    @SpyBean
    private MessageToDlqConverter messageToDlqConverter;

    @Test
    void shouldRemoveNotificationAndSendSuccessfully() {
        NotificationMessage message = prepareNotificationMock();

        serviceUnderTest.notifySubscribers(message);

        verify(smtpNotificationManager).notifySubscribers(message);
        verifyNoInteractions(messageToDlqConverter);
        verifyNoInteractions(notificationDlqRepository);
    }

    @Test
    void shouldMoveNotificationToDlqWhenSendingFails() {
        NotificationMessage message = prepareNotificationMock();
        Exception runtimeException = new RuntimeException(ERROR_MESSAGE);

        doThrow(runtimeException).when(smtpNotificationManager).notifySubscribers(message);

        serviceUnderTest.notifySubscribers(message);

        ArgumentCaptor<NotificationMessageDlq> dlqCaptor =
                ArgumentCaptor.forClass(NotificationMessageDlq.class);
        verify(notificationDlqRepository).save(dlqCaptor.capture());

        NotificationMessageDlq savedDlq = dlqCaptor.getValue();
        assertNotNull(savedDlq.getId());

        Optional<NotificationMessageDlq> dlqMessageFromDb = notificationDlqRepository.findById(savedDlq.getId());
        assertNotNull(dlqMessageFromDb.orElse(null));
        savedDlq = dlqMessageFromDb.get();
        assertEquals(MESSAGE_SUBJECT, savedDlq.getSubject());
        assertEquals(MESSAGE_BODY, savedDlq.getBody());
        assertNotNull(savedDlq.getCreatedDate());
        assertNotNull(savedDlq.getReason());
        assertTrue(savedDlq.getReason().contains(ERROR_MESSAGE));
        assertEquals(TEMPLATE_PARAMS, savedDlq.getTemplateParameters());
        assertEquals(COPY_USERS_ID, savedDlq.getCopyUserIds());
        assertEquals(USER_ID, savedDlq.getToUserId());

        verify(smtpNotificationManager).notifySubscribers(message);
        verify(messageToDlqConverter).convertToDlq(message, runtimeException);
    }

    private static NotificationMessage prepareNotificationMock() {
        NotificationMessage message = mock(NotificationMessage.class);
        when(message.getId()).thenReturn(MESSAGE_ID);
        when(message.getSubject()).thenReturn(MESSAGE_SUBJECT);
        when(message.getBody()).thenReturn(MESSAGE_BODY);
        when(message.getCopyUserIds()).thenReturn(COPY_USERS_ID);
        when(message.getToUserId()).thenReturn(USER_ID);
        when(message.getTemplateParameters()).thenReturn(TEMPLATE_PARAMS);
        return message;
    }
}

