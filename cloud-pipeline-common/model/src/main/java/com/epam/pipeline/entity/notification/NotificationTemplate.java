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

import com.epam.pipeline.entity.notification.NotificationSettings.NotificationType;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Getter
@Setter
@Entity
@Table(name="notification_template", schema = "pipeline")
@NoArgsConstructor
public class NotificationTemplate {

    private static final String EMPTY = "";
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    private String name;

    private String subject;
    private String body;

    public NotificationTemplate(Long id) {
        this.setId(id);
    }

    public static NotificationTemplate getDefault(NotificationType type) {
        NotificationTemplate template = new NotificationTemplate(type.getId());
        template.setName(type.name());
        template.setBody(EMPTY);
        template.setSubject(EMPTY);
        return template;
    }
}
