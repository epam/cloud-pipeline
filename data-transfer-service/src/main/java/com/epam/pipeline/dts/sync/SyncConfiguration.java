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

package com.epam.pipeline.dts.sync;

import com.epam.pipeline.dts.common.service.CloudPipelineAPIClient;
import com.epam.pipeline.dts.configuration.CommonConfiguration;
import com.epam.pipeline.dts.sync.model.AutonomousDtsDetails;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;

@SpringBootConfiguration
@ComponentScan(basePackages = "com.epam.pipeline.dts.sync.impl")
@Import({CommonConfiguration.class})
public class SyncConfiguration {

    @Bean
    public AutonomousDtsDetails autonomousDtsDetails(final @Value("${dts.local.name}") String dtsName,
                                                     final CloudPipelineAPIClient apiClient) {
        final String resolvedDtsName = tryBuildDtsName(dtsName);
        return new AutonomousDtsDetails(resolvedDtsName, apiClient);
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
