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

import com.epam.pipeline.entity.notification.NotificationSettings;
import com.epam.pipeline.manager.notification.NotificationSettingsApiService;
import com.epam.pipeline.test.web.AbstractControllerTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Collections;
import java.util.List;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.BOOLEAN_INSTANCE_TYPE;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.notification.NotificationCreatorUtils.NOTIFICATION_SETTINGS_LIST_TYPE;
import static com.epam.pipeline.test.creator.notification.NotificationCreatorUtils.NOTIFICATION_SETTINGS_TYPE;
import static com.epam.pipeline.test.creator.notification.NotificationCreatorUtils.getNotificationSettings;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

public class NotificationSettingsControllerTest extends AbstractControllerTest {

    private static final String NOTIFICATION_SETTING_URL = SERVLET_PATH + "/notification/settings";
    private static final String BY_ID_URL = NOTIFICATION_SETTING_URL + "/%d";
    private final NotificationSettings notificationSettings = getNotificationSettings();
    private final List<NotificationSettings> notificationSettingsList = Collections.singletonList(notificationSettings);

    @Autowired
    private NotificationSettingsApiService mockNotificationSettingsApiService;

    @Test
    public void shouldFailLoadAllForUnauthorizedUser() {
        performUnauthorizedRequest(get(NOTIFICATION_SETTING_URL));
    }

    @Test
    @WithMockUser
    public void shouldLoadAll() {
        doReturn(notificationSettingsList).when(mockNotificationSettingsApiService).loadAll();

        final MvcResult mvcResult = performRequest(get(NOTIFICATION_SETTING_URL));

        verify(mockNotificationSettingsApiService).loadAll();
        assertResponse(mvcResult, notificationSettingsList, NOTIFICATION_SETTINGS_LIST_TYPE);
    }

    @Test
    public void shouldFailCreateOrUpdateForUnauthorizedUser() {
        performUnauthorizedRequest(post(NOTIFICATION_SETTING_URL));
    }

    @Test
    @WithMockUser
    public void shouldCreateOrUpdate() throws Exception {
        final String content = getObjectMapper().writeValueAsString(notificationSettings);
        doReturn(notificationSettings).when(mockNotificationSettingsApiService).createOrUpdate(notificationSettings);

        final MvcResult mvcResult = performRequest(post(NOTIFICATION_SETTING_URL).content(content));

        verify(mockNotificationSettingsApiService).createOrUpdate(notificationSettings);
        assertResponse(mvcResult, notificationSettings, NOTIFICATION_SETTINGS_TYPE);
    }

    @Test
    public void shouldFailLoadForUnauthorizedUser() {
        performUnauthorizedRequest(get(String.format(BY_ID_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldLoad() {
        doReturn(notificationSettings).when(mockNotificationSettingsApiService).load(ID);

        final MvcResult mvcResult = performRequest(get(String.format(BY_ID_URL, ID)));

        verify(mockNotificationSettingsApiService).load(ID);
        assertResponse(mvcResult, notificationSettings, NOTIFICATION_SETTINGS_TYPE);
    }

    @Test
    public void shouldFailDeleteForUnauthorizedUser() {
        performUnauthorizedRequest(delete(String.format(BY_ID_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldDelete() {
        final MvcResult mvcResult = performRequest(delete(String.format(BY_ID_URL, ID)));

        verify(mockNotificationSettingsApiService).delete(ID);
        assertResponse(mvcResult, true, BOOLEAN_INSTANCE_TYPE);
    }
}
