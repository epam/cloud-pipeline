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

package com.epam.pipeline.entity.notification;

<<<<<<< ours
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
||||||| base
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
=======
import com.fasterxml.jackson.annotation.JsonProperty;
>>>>>>> theirs

import java.util.Date;

<<<<<<< ours
@Getter
@Setter
@RequiredArgsConstructor
public class SystemNotificationConfirmation {
    private final Long notificationId;
    private final String title;
    private final String body;
    private final String user;
    private final Date date;
||||||| base
@Getter
@Setter
@RequiredArgsConstructor
@EqualsAndHashCode
public class SystemNotificationConfirmation {
    private final Long notificationId;
    private final String title;
    private final String body;
    private final String user;
    private final Date date;
=======
public record SystemNotificationConfirmation(
        @JsonProperty("notificationId") Long notificationId,
        @JsonProperty("title") String title,
        @JsonProperty("body") String body,
        @JsonProperty("user") String user,
        @JsonProperty("date") Date date) {
>>>>>>> theirs
}
