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

package com.epam.pipeline.controller.vo;

import com.epam.pipeline.entity.notification.SystemNotificationSeverity;
import com.epam.pipeline.entity.notification.SystemNotificationState;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.collections.CollectionUtils;

import java.util.Date;
import java.util.List;

@Getter
@Setter
@EqualsAndHashCode
public class SystemNotificationFilterVO {
    private List<SystemNotificationSeverity> severityList;
    private List<SystemNotificationState> stateList;
    private Date createdDateAfter;

    @JsonIgnore
    public boolean isEmpty() {
        return CollectionUtils.isEmpty(this.severityList) &&
                CollectionUtils.isEmpty(this.stateList) &&
                this.createdDateAfter == null;
    }
}
