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

package com.epam.pipeline.controller.vo.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Custom notification view object.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationMessageVO {

    /**
     * Notification subject.
     */
    private String subject;

    /**
     * Notification body.
     */
    private String body;

    /**
     * Template parameters that will be used to fill the subject and the body of the notification.
     */
    private Map<String, Object> parameters;

    /**
     * User name of the notification receiver.
     */
    private String toUser;

    /**
     * User names of ones who are in the notification CC list.
     */
    private List<String> copyUsers;
}
