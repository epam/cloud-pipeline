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

package com.epam.pipeline.manager.cluster.alive.policy;

import com.epam.pipeline.entity.cluster.ClusterKeepAlivePolicy;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

@Component
@RequiredArgsConstructor
@Slf4j
public class MinutesTillHourNodeExpirationService implements NodeExpirationService {

    private static final int TIME_DELIMITER = 60;
    private static final int TIME_TO_SHUT_DOWN_NODE = 1;

    private final PreferenceManager preferenceManager;

    @Override
    public boolean isNodeExpired(final Long runId, final LocalDateTime nodeLaunchTime) {
        if (nodeLaunchTime == null) {
            return true;
        }
        final Integer keepAliveMinutes = preferenceManager.getPreference(
                SystemPreferences.CLUSTER_KEEP_ALIVE_MINUTES);
        if (keepAliveMinutes == null) {
            return true;
        }
        try {
            log.debug("Node {} launch time {}.", runId, nodeLaunchTime);
            LocalDateTime now = LocalDateTime.now(Clock.systemUTC());
            long aliveTime = Duration.between(nodeLaunchTime, now).getSeconds() / TIME_DELIMITER;
            log.debug("Node {} is alive for {} minutes.", runId, aliveTime);
            long minutesToWholeHour = aliveTime % TIME_DELIMITER;
            long minutesLeft = TIME_DELIMITER - minutesToWholeHour;
            log.debug("Node {} has {} minutes left until next hour.", runId, minutesLeft);
            return minutesLeft <= keepAliveMinutes && minutesLeft > TIME_TO_SHUT_DOWN_NODE;
        } catch (DateTimeParseException e) {
            log.error(e.getMessage(), e);
            return true;
        }
    }

    @Override
    public ClusterKeepAlivePolicy policy() {
        return ClusterKeepAlivePolicy.MINUTES_TILL_HOUR;
    }
}
