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

package com.epam.pipeline.dts.sync.impl;

import com.epam.pipeline.dts.common.service.CloudPipelineAPIClient;
import com.epam.pipeline.dts.sync.PreferenceService;
import com.epam.pipeline.dts.sync.model.AutonomousDtsDetails;
import com.epam.pipeline.entity.dts.submission.DtsRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
@EnableScheduling
public class CloudPipelineApiPreferenceService implements PreferenceService {

    private final CloudPipelineAPIClient apiClient;
    private final ConcurrentMap<String, String> preferences;
    private final String dtsName;

    @Autowired
    public CloudPipelineApiPreferenceService(final AutonomousDtsDetails autonomousDtsDetails) {
        this.apiClient = autonomousDtsDetails.getApiClient();
        this.preferences = new ConcurrentHashMap<>();
        this.dtsName = autonomousDtsDetails.getDtsName();
    }

    @Scheduled(fixedDelayString = "${dts.sync.poll:60000}")
    public void synchronizePreferences() {
        final Map<String, String> updatedPreferences = apiClient.findDtsRegistryByNameOrId(dtsName)
            .map(DtsRegistry::getPreferences)
            .orElse(Collections.emptyMap());
        preferences.clear();
        preferences.putAll(updatedPreferences);
    }

    @Override
    public String get(final String preferenceKey) {
        return preferences.get(preferenceKey);
    }
}
