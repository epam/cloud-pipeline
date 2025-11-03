/*
 * Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.access;

import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.scheduling.AbstractSchedulingManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.SchedulerLock;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Objects;

/**
 * This scheduler is used to delete obsolete authorization codes.
 */
@Service
@RequiredArgsConstructor
public class AccessCodeCleaner extends AbstractSchedulingManager {

    private final AccessCodeCleanerCore core;

    @PostConstruct
    public void init() {
        scheduleFixedDelaySecured(core::monitor, SystemPreferences.SYSTEM_ACCESS_CODE_MONITOR_DELAY,
                "AccessCodeCleaner");
    }

    @Service
    @Slf4j
    @RequiredArgsConstructor
    private static class AccessCodeCleanerCore {
        private final AccessService accessService;
        private final PreferenceManager preferenceManager;

        @SchedulerLock(name = "AccessCodeCleanerCore_monitor", lockAtMostForString = "PT10M")
        public void monitor() {
            if (monitoringDisabled()) {
                log.debug("Access code cleaner scheduler is not enabled");
                return;
            }

            accessService.deleteExpired();
        }

        private boolean monitoringDisabled() {
            final Boolean monitoringEnabled = preferenceManager.getPreference(
                    SystemPreferences.SYSTEM_ACCESS_CODE_MONITOR_ENABLED);
            return Objects.isNull(monitoringEnabled) || !monitoringEnabled;
        }
    }
}
