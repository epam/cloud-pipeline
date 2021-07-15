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
import com.epam.pipeline.dts.sync.model.AutonomousSyncRule;
import com.epam.pipeline.entity.dts.submission.DtsRegistry;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
@EnableScheduling
@Slf4j
public class CloudPipelineApiPreferenceService implements PreferenceService {

    private final CloudPipelineAPIClient apiClient;
    private final ConcurrentMap<String, String> preferences;
    private final String dtsName;
    private final String dtsLocalShutdownKey;
    private final String dtsLocalSyncRulesKey;

    @Autowired
    public CloudPipelineApiPreferenceService(final @Value("${dts.name}") String dtsName,
                                             final @Value("${dts.preference.shutdown.key:dts.restart.force}")
                                                 String dtsLocalShutdownKey,
                                             final @Value("${dts.local.preference.sync.rules.key:dts.local.sync.rules}")
                                                     String dtsLocalSyncRulesKey,
                                             final CloudPipelineAPIClient apiClient) {
        this.apiClient = apiClient;
        this.preferences = new ConcurrentHashMap<>();
        this.dtsName = tryBuildDtsName(dtsName);
        this.dtsLocalShutdownKey = dtsLocalShutdownKey;
        this.dtsLocalSyncRulesKey = dtsLocalSyncRulesKey;
        log.info("Synchronizing preferences for current host: `{}`", this.dtsName);
    }

    @Scheduled(fixedDelayString = "${dts.sync.poll:60000}")
    public void synchronizePreferences() {
        final Map<String, String> updatedPreferences = apiClient.findDtsRegistryByNameOrId(dtsName)
            .map(DtsRegistry::getPreferences)
            .orElse(Collections.emptyMap());
        log.warn("Following preferences received during sync iteration: {}", updatedPreferences.toString());
        preferences.clear();
        preferences.putAll(updatedPreferences);
    }

    @Override
    public Optional<List<AutonomousSyncRule>> getSyncRules() {
        final String rulesAsString = preferences.getOrDefault(dtsLocalSyncRulesKey, "[]");
        try {
            return Optional.of(new ObjectMapper()
                                   .readValue(rulesAsString, new TypeReference<List<AutonomousSyncRule>>() {}));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    @Override
    public boolean isShutdownRequired() {
        return Boolean.TRUE.toString().equalsIgnoreCase(preferences.get(dtsLocalShutdownKey));
    }

    @Override
    public void clearShutdownFlag() {
        log.info("Clear flag of remote shutdown `{}` for DTS registry `{}`", dtsLocalShutdownKey, dtsName);
        apiClient.deleteDtsRegistryPreferences(dtsName, Collections.singletonList(dtsLocalShutdownKey));
    }

    private String tryBuildDtsName(final String preconfiguredDtsName) {
        final String dtsName = Optional.ofNullable(preconfiguredDtsName)
            .filter(StringUtils::isNotBlank)
            .orElseGet(this::tryExtractHostnameFromEnvironment);
        if (StringUtils.isBlank(dtsName)) {
            throw new IllegalStateException("Unable to build DTS name!");
        }
        return dtsName;
    }

    private String tryExtractHostnameFromEnvironment() {
        try {
            return Optional.ofNullable(InetAddress.getLocalHost())
                .map(InetAddress::getCanonicalHostName)
                .filter(StringUtils::isNotEmpty)
                .map(StringUtils::strip)
                .map(StringUtils::lowerCase)
                .orElse(StringUtils.EMPTY);
        } catch (UnknownHostException e) {
            return StringUtils.EMPTY;
        }
    }
}
