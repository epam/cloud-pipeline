/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.vmmonitor.service.k8s;

import com.epam.pipeline.vmmonitor.model.k8s.TinyproxyThresholdEvent;
import com.epam.pipeline.vmmonitor.service.Monitor;
import com.epam.pipeline.vmmonitor.service.pipeline.TinyproxyStatsClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@ConditionalOnProperty(value = "monitor.tinyproxy.enable", havingValue = "true")
public class TinyproxyMonitor implements Monitor {

    private final KubernetesNotifier kubernetesNotifier;
    private final TinyproxyStatsClient statsClient;
    private final Integer notificationResendDelayMinutes;
    private final Map<String, Long> statsThresholds;
    private final Map<String, LocalDateTime> lastNotification;

    public TinyproxyMonitor(final KubernetesNotifier kubernetesNotifier,
                            final TinyproxyStatsClient statsClient,
                            @Value("#{${monitor.tinyproxy.stats.thresholds:{:}}}")
                            final Map<String, Long> statsThresholds,
                            @Value("${monitor.tinyproxy.notification.resend.delay.mins:120}")
                            final Integer notificationResendDelayMinutes) {
        this.kubernetesNotifier = kubernetesNotifier;
        this.statsClient = statsClient;
        this.notificationResendDelayMinutes = notificationResendDelayMinutes;
        this.statsThresholds = MapUtils.emptyIfNull(statsThresholds);
        this.lastNotification = new HashMap<>();
    }

    @Override
    public void monitor() {
        if (MapUtils.isEmpty(statsThresholds)) {
            log.info("No thresholds configured for tinyproxy monitoring.");
            return;
        }
        log.info("Checking {} configured tinyproxy stats thresholds", statsThresholds.size());
        final LocalDateTime checkTime = LocalDateTime.now();
        final List<TinyproxyThresholdEvent> thresholdEvents = getExceedingThresholdsEvents(checkTime);
        log.info("Tinyproxy threshold events detected: {}", getEventsNames(thresholdEvents));
        thresholdEvents.removeIf(this::beforeNextNotificationTime);
        if (CollectionUtils.isNotEmpty(thresholdEvents)) {
            log.info("Sending notifications on tinyproxy threshold events: {}", getEventsNames(thresholdEvents));
            kubernetesNotifier.notifyTinyproxyThreshold(thresholdEvents);
            thresholdEvents.forEach(event -> lastNotification.put(event.getThresholdKey(), checkTime));
        } else {
            log.info("No threshold events requires notification");
        }
    }

    private List<TinyproxyThresholdEvent> getExceedingThresholdsEvents(final LocalDateTime checkTime) {
        final Map<String, Long> latestStats = loadTinyproxyNumericStats();
        return statsThresholds.entrySet().stream()
            .map(e -> {
                final String thresholdKey = e.getKey();
                final Long thresholdValue = e.getValue();
                final Long latestValue = latestStats.getOrDefault(thresholdKey, -1L);
                return new TinyproxyThresholdEvent(thresholdKey, thresholdValue, latestValue, checkTime);
            })
            .filter(event -> event.getActualValue() > event.getThresholdValue())
            .collect(Collectors.toList());
    }

    private boolean beforeNextNotificationTime(final TinyproxyThresholdEvent event) {
        final LocalDateTime nextNotificationTimestamp =
            lastNotification.getOrDefault(event.getThresholdKey(), LocalDateTime.MIN)
                .plus(notificationResendDelayMinutes, ChronoUnit.MINUTES);
        return event.getTimestamp().isBefore(nextNotificationTimestamp);
    }

    private Map<String, Long> loadTinyproxyNumericStats() {
        log.info("Loading tinyproxy stats");
        return statsClient.load().entrySet().stream()
            .filter(e -> NumberUtils.isDigits(e.getValue()))
            .collect(Collectors.toMap(Map.Entry::getKey, e -> Long.parseLong(e.getValue())));
    }

    private List<String> getEventsNames(final List<TinyproxyThresholdEvent> thresholdEvents) {
        return thresholdEvents.stream()
            .map(TinyproxyThresholdEvent::getThresholdKey)
            .collect(Collectors.toList());
    }
}
