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

import com.epam.pipeline.controller.vo.notification.NotificationMessageVO;
import com.epam.pipeline.entity.notification.NotificationMessage;
import com.epam.pipeline.manager.notification.NotificationApiService;
import com.epam.pipeline.test.web.AbstractControllerTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;

import static com.epam.pipeline.test.creator.notification.NotificationCreatorUtils.NOTIFICATION_MESSAGE_TYPE;
import static com.epam.pipeline.test.creator.notification.NotificationCreatorUtils.getNotificationMessage;
import static com.epam.pipeline.test.creator.notification.NotificationCreatorUtils.getNotificationMessageVO;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

public class NotificationControllerTest extends AbstractControllerTest {

    private static final String NOTIFICATION_URL = SERVLET_PATH + "/notification";
    private static final String NOTIFICATION_MESSAGE_URL = NOTIFICATION_URL + "/message";
    private final NotificationMessage notificationMessage = getNotificationMessage();
    private final NotificationMessageVO notificationMessageVO = getNotificationMessageVO();

    @Autowired
    private NotificationApiService mockNotificationApiService;

    @Test
    public void shouldFailCreateForUnauthorizedUser() {
        performUnauthorizedRequest(post(NOTIFICATION_MESSAGE_URL));
    }

    @Test
    @WithMockUser
    public void shouldCreate() throws Exception {
        final String content = getObjectMapper().writeValueAsString(notificationMessageVO);
        doReturn(notificationMessage).when(mockNotificationApiService).createNotification(notificationMessageVO);

        final MvcResult mvcResult = performRequest(post(NOTIFICATION_MESSAGE_URL).content(content));

        verify(mockNotificationApiService).createNotification(notificationMessageVO);
        assertResponse(mvcResult, notificationMessage, NOTIFICATION_MESSAGE_TYPE);
    }
}
