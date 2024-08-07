/*
 * Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.monitor.service;

import com.epam.pipeline.entity.cluster.InstanceType;
import com.epam.pipeline.monitor.rest.CloudPipelineAPIClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Refreshes GPU instance types
 */
@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("PMD.AvoidCatchingGenericException")
public class InstanceTypesLoader {
    private final CloudPipelineAPIClient client;
    private final Set<String> gpuInstanceTypes = ConcurrentHashMap.newKeySet();

    @PostConstruct
    public void init() {
        refreshInstances();
    }

    @Scheduled(fixedDelayString = "${refresh.instances.timeout:86400000}")
    public void refreshInstances() {
        try {
            final Set<String> loadedTypes = client.loadAllInstanceTypes().stream()
                    .filter(it -> it.getGpu() > 0)
                    .map(InstanceType::getName)
                    .filter(StringUtils::isNotBlank)
                    .collect(Collectors.toSet());
            gpuInstanceTypes.clear();
            gpuInstanceTypes.addAll(loadedTypes);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    public Set<String> loadGpuInstanceTypes() {
        return gpuInstanceTypes;
    }
}
