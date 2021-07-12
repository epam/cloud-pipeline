/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.dts.sync.service.impl;

import com.epam.pipeline.dts.common.service.CloudPipelineAPIClient;
import com.epam.pipeline.dts.sync.service.PreferenceService;
import com.epam.pipeline.dts.sync.service.ShutdownService;
import com.epam.pipeline.dts.sync.model.AutonomousDtsDetails;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
@Slf4j
@EnableScheduling
public class DtsSynchronizationService implements ShutdownService {

    private final CloudPipelineAPIClient apiClient;
    private final ConfigurableApplicationContext context;
    private final PreferenceService preferenceService;
    private final String dtsLocalShutdownKey;
    private final String dtsName;

    @Autowired
    public DtsSynchronizationService(final @Value("${dts.preference.shutdown.key:dts.restart.force}")
                                             String dtsLocalShutdownKey,
                                     final ConfigurableApplicationContext context,
                                     final CloudPipelineApiPreferenceService preferenceService,
                                     final AutonomousDtsDetails autonomousDtsDetails) {
        this.apiClient = autonomousDtsDetails.getApiClient();
        this.dtsName = autonomousDtsDetails.getDtsName();
        this.preferenceService = preferenceService;
        this.context = context;
        this.dtsLocalShutdownKey = dtsLocalShutdownKey;
    }

    @Scheduled(fixedDelayString = "${dts.sync.poll:60000}")
    public void synchronizePreferences() {
        if (shutdownRequired(preferenceService.get(dtsLocalShutdownKey))) {
            shutdown();
        }
    }

    private boolean shutdownRequired(final String shutDownPrefValue) {
        return Boolean.TRUE.toString().equalsIgnoreCase(shutDownPrefValue);
    }

    @Override
    public void shutdown() {
        log.info("Shutdown will be preformed as preference flag `{}` is `true`.", dtsLocalShutdownKey);
        apiClient.deleteDtsRegistryPreferences(dtsName, Collections.singletonList(dtsLocalShutdownKey));
        context.close();
    }
}
