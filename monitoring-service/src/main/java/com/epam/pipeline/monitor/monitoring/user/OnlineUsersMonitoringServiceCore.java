/*
 * Copyright 2021 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.monitor.rest.CloudPipelineAPIClient;
import com.epam.pipeline.monitor.service.user.OnlineUsersService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class OnlineUsersMonitoringServiceCore {
    private final CloudPipelineAPIClient client;
    private final String monitorEnabledPreferenceName;
    private final OnlineUsersService onlineUsersService;

    public OnlineUsersMonitoringServiceCore(final CloudPipelineAPIClient client,
                                            @Value("${preference.name.usage.users.monitor.enable}")
                                            final String monitorEnabledPreferenceName,
                                            final OnlineUsersService onlineUsersService) {
        this.client = client;
        this.monitorEnabledPreferenceName = monitorEnabledPreferenceName;
        this.onlineUsersService = onlineUsersService;
    }

    public void monitor() {
        if (!client.getBooleanPreference(monitorEnabledPreferenceName)) {
            log.debug("Users usage monitor is not enabled");
            return;
        }

        final List<Long> activeUserIds = client.loadOnlineUsers().stream()
                .map(PipelineUser::getId)
                .collect(Collectors.toList());
        onlineUsersService.save(activeUserIds);
    }
}
