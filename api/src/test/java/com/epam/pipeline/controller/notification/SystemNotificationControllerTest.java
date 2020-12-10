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

package com.epam.pipeline.controller.notification;

import com.epam.pipeline.controller.vo.SystemNotificationFilterVO;
import com.epam.pipeline.entity.notification.SystemNotification;
import com.epam.pipeline.entity.notification.SystemNotificationConfirmation;
import com.epam.pipeline.entity.notification.SystemNotificationConfirmationRequest;
import com.epam.pipeline.acl.notification.SystemNotificationApiService;
import com.epam.pipeline.test.web.AbstractControllerTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.notification.NotificationCreatorUtils.CONFIRMATION_TYPE;
import static com.epam.pipeline.test.creator.notification.NotificationCreatorUtils.SYSTEM_NOTIFICATION_LIST_TYPE;
import static com.epam.pipeline.test.creator.notification.NotificationCreatorUtils.SYSTEM_NOTIFICATION_TYPE;
import static com.epam.pipeline.test.creator.notification.NotificationCreatorUtils.getSystemNotification;
import static com.epam.pipeline.test.creator.notification.NotificationCreatorUtils.getSystemNotificationConfirmation;
import static com.epam.pipeline.test.creator.notification.NotificationCreatorUtils.getSystemNotificationConfirmationRequest;
import static com.epam.pipeline.test.creator.notification.NotificationCreatorUtils.getSystemNotificationFilterVO;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

public class SystemNotificationControllerTest extends AbstractControllerTest {

    private static final long ONE_SECOND = 1000L;
    private static final String NOTIFICATION_URL = SERVLET_PATH + "/notification";
    private static final String LIST_NOTIFICATIONS_URL = NOTIFICATION_URL + "/list";
    private static final String ACTIVE_NOTIFICATIONS_URL = NOTIFICATION_URL + "/active";
    private static final String FILTER_URL = NOTIFICATION_URL + "/filter";
    private static final String BY_ID_URL = NOTIFICATION_URL + "/%d";
    private static final String CONFIRM_URL = NOTIFICATION_URL + "/confirm";
    private static final String AFTER = "after";
    private static final String DATE_AS_STRING = "1970-01-01 03:00:01";
    private static final Date DATE = new Date(ONE_SECOND);
    private final SystemNotification systemNotification = getSystemNotification();
    private final List<SystemNotification> systemNotificationList = Collections.singletonList(systemNotification);
    private final SystemNotificationFilterVO systemNotificationFilterVO = getSystemNotificationFilterVO();
    private final SystemNotificationConfirmation systemNotificationConfirmation = getSystemNotificationConfirmation();
    private final SystemNotificationConfirmationRequest systemNotificationConfirmationRequest =
            getSystemNotificationConfirmationRequest();

    @Autowired
    private SystemNotificationApiService mockSystemNotificationApiService;

    @Test
    public void shouldFailCreateOrUpdateNotificationForUnauthorizedUser() {
        performUnauthorizedRequest(post(NOTIFICATION_URL));
    }

    @Test
    @WithMockUser
    public void shouldCreateOrUpdateNotification() throws Exception {
        final String content = getObjectMapper().writeValueAsString(systemNotification);
        doReturn(systemNotification).when(mockSystemNotificationApiService)
                .createOrUpdateNotification(systemNotification);

        final MvcResult mvcResult = performRequest(post(NOTIFICATION_URL).content(content));

        verify(mockSystemNotificationApiService).createOrUpdateNotification(systemNotification);
        assertResponse(mvcResult, systemNotification, SYSTEM_NOTIFICATION_TYPE);
    }

    @Test
    public void shouldFailLoadNotificationsForUnauthorizedUser() {
        performUnauthorizedRequest(get(LIST_NOTIFICATIONS_URL));
    }

    @Test
    @WithMockUser
    public void shouldLoadNotifications() {
        doReturn(systemNotificationList).when(mockSystemNotificationApiService).loadAllNotifications();

        final MvcResult mvcResult = performRequest(get(LIST_NOTIFICATIONS_URL));

        verify(mockSystemNotificationApiService).loadAllNotifications();
        assertResponse(mvcResult, systemNotificationList, SYSTEM_NOTIFICATION_LIST_TYPE);
    }

    @Test
    public void shouldFailLoadActiveNotificationsForUnauthorizedUser() {
        performUnauthorizedRequest(get(ACTIVE_NOTIFICATIONS_URL));
    }

    @Test
    @WithMockUser
    public void shouldLoadActiveNotifications() {
        doReturn(systemNotificationList).when(mockSystemNotificationApiService).loadActiveNotifications(DATE);

        final MvcResult mvcResult = performRequest(get(ACTIVE_NOTIFICATIONS_URL)
                .param(AFTER, DATE_AS_STRING));

        verify(mockSystemNotificationApiService).loadActiveNotifications(DATE);
        assertResponse(mvcResult, systemNotificationList, SYSTEM_NOTIFICATION_LIST_TYPE);
    }

    @Test
    public void shouldFailFilterNotificationsForUnauthorizedUser() {
        performUnauthorizedRequest(post(FILTER_URL));
    }

    @Test
    @WithMockUser
    public void shouldFilterNotifications() throws Exception {
        final String content = getObjectMapper().writeValueAsString(systemNotificationFilterVO);
        doReturn(systemNotificationList).when(mockSystemNotificationApiService)
                .filterNotifications(systemNotificationFilterVO);

        final MvcResult mvcResult = performRequest(post(FILTER_URL).content(content));

        verify(mockSystemNotificationApiService).filterNotifications(systemNotificationFilterVO);
        assertResponse(mvcResult, systemNotificationList, SYSTEM_NOTIFICATION_LIST_TYPE);
    }

    @Test
    public void shouldFailLoadNotificationForUnauthorizedUser() {
        performUnauthorizedRequest(get(String.format(BY_ID_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldLoadNotification() {
        doReturn(systemNotification).when(mockSystemNotificationApiService).loadNotification(ID);

        final MvcResult mvcResult = performRequest(get(String.format(BY_ID_URL, ID)));

        verify(mockSystemNotificationApiService).loadNotification(ID);
        assertResponse(mvcResult, systemNotification, SYSTEM_NOTIFICATION_TYPE);
    }

    @Test
    public void shouldFailDeleteNotificationForUnauthorizedUser() {
        performUnauthorizedRequest(delete(String.format(BY_ID_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldDeleteNotification() {
        doReturn(systemNotification).when(mockSystemNotificationApiService).deleteNotification(ID);

        final MvcResult mvcResult = performRequest(delete(String.format(BY_ID_URL, ID)));

        verify(mockSystemNotificationApiService).deleteNotification(ID);
        assertResponse(mvcResult, systemNotification, SYSTEM_NOTIFICATION_TYPE);
    }

    @Test
    public void shouldFailConfirmNotificationForUnauthorizedUser() {
        performUnauthorizedRequest(post(CONFIRM_URL));
    }

    @Test
    @WithMockUser
    public void shouldConfirmNotification() throws Exception{
        final String content = getObjectMapper().writeValueAsString(systemNotificationConfirmationRequest);
        doReturn(systemNotificationConfirmation).when(mockSystemNotificationApiService)
                .confirmNotification(systemNotificationConfirmationRequest);

        final MvcResult mvcResult = performRequest(post(CONFIRM_URL).content(content));

        verify(mockSystemNotificationApiService).confirmNotification(systemNotificationConfirmationRequest);
        assertResponse(mvcResult, systemNotificationConfirmation, CONFIRMATION_TYPE);
    }
}
