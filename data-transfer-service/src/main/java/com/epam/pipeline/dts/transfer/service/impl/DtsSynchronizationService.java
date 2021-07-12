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

package com.epam.pipeline.dts.transfer.service.impl;

import com.epam.pipeline.dts.common.service.CloudPipelineAPIClient;
import com.epam.pipeline.entity.dts.submission.DtsRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
@Slf4j
@EnableScheduling
public class DtsSynchronizationService {

    private final ConcurrentMap<String, String> preferences = new ConcurrentHashMap<>();
    private final CloudPipelineAPIClient apiClient;
    private final ConfigurableApplicationContext context;
    private final String dtsName;
    private final String dtsLocalShutdownKey;

    @Autowired
    public DtsSynchronizationService(final @Value("${dts.local.name}") String dtsName,
                                     final @Value("${dts.local.preference.shutdown.key:dts.local.restart}")
                                             String dtsLocalShutdownKey,
                                     final CloudPipelineAPIClient apiClient,
                                     final ConfigurableApplicationContext context) {
        this.apiClient = apiClient;
        this.dtsLocalShutdownKey = dtsLocalShutdownKey;
        this.context = context;
        this.dtsName = tryBuildDtsName(dtsName);
        log.info("DTS sync is enabled for current host: `{}`.", this.dtsName);
    }

    @Scheduled(fixedDelayString = "${dts.local.preferences.poll:60000}")
    public void synchronizePreferences() {
        final Map<String, String> updatedPreferences = apiClient.findDtsRegistryByNameOrId(dtsName)
            .map(DtsRegistry::getPreferences)
            .orElse(Collections.emptyMap());
        if (shutdownRequired(updatedPreferences)) {
            performShutdown();
        }
        preferences.clear();
        preferences.putAll(updatedPreferences);
    }

    private String tryBuildDtsName(final String dtsNameFromProperties) {
        final String dtsName = Optional.ofNullable(dtsNameFromProperties)
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

    private boolean shutdownRequired(final Map<String, String> newPreferences) {
        return Boolean.TRUE.toString()
            .equalsIgnoreCase(newPreferences.getOrDefault(dtsLocalShutdownKey, Boolean.FALSE.toString()));
    }

    private void performShutdown() {
        log.info("Shutdown will be preformed as preference flag `{}` is `true`.", dtsLocalShutdownKey);
        apiClient.deleteDtsRegistryPreferences(dtsName, Collections.singletonList(dtsLocalShutdownKey));
        context.close();
    }
}
