/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.monitor.monitoring.user;

import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.monitor.rest.CloudPipelineAPIClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@Slf4j
public class OnlineUsersCleanerServiceCore {
    private final CloudPipelineAPIClient client;
    private final String monitorEnabledPreferenceName;
    private final String usersStorePeriodPreferenceName;

    public OnlineUsersCleanerServiceCore(final CloudPipelineAPIClient client,
                                         @Value("${preference.name.usage.users.clean.enable}")
                                         final String monitorEnabledPreferenceName,
                                         @Value("${preference.name.usage.users.store.period}")
                                         final String usersStorePeriodPreferenceName) {
        this.client = client;
        this.monitorEnabledPreferenceName = monitorEnabledPreferenceName;
        this.usersStorePeriodPreferenceName = usersStorePeriodPreferenceName;
    }

    public void monitor() {
        if (!client.getBooleanPreference(monitorEnabledPreferenceName)) {
            log.debug("Users usage removal is not enabled");
            return;
        }

        final Integer duration = client.getIntPreference(usersStorePeriodPreferenceName);
        if (Objects.isNull(duration)) {
            log.debug("Cannot remove expired online users statistic since period was not specified");
            return;
        }
        client.deleteExpiredOnlineUsers(DateUtils.nowUTC().minusDays(duration).toLocalDate().toString());
        log.debug("Finished online users removal service");
    }
}
