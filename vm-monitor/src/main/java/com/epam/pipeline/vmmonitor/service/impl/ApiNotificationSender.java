/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.vmmonitor.service.impl;

import com.epam.pipeline.exception.PipelineResponseException;
import com.epam.pipeline.vmmonitor.service.CloudPipelineAPIClient;
import com.epam.pipeline.vmmonitor.service.NotificationSender;
import com.epam.pipeline.vo.notification.NotificationMessageVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class ApiNotificationSender implements NotificationSender {

    private final CloudPipelineAPIClient apiClient;

    @Override
    public void sendMessage(final NotificationMessageVO messageVO) {
        try {
            log.debug("Sending notification using API client");
            apiClient.sendNotification(messageVO);
            log.debug("Successfully sent notification using API client");
        } catch (PipelineResponseException e) {
            log.error("An error occurred during sending notification using API client", e);
        }
    }
}
