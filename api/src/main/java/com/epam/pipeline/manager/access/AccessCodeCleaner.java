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

import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.scheduling.AbstractSchedulingManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

/**
 * This scheduler is used to delete obsolete authorization code.
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
}
