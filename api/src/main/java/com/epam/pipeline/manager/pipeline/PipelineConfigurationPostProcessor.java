/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.pipeline;

import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.entity.configuration.PipeConfValueVO;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.manager.datastorage.providers.ProviderUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;

@Component
@Slf4j
public class PipelineConfigurationPostProcessor {

    private final Map<String, String> dockerRegistryAliases;
    private final Map<String, String> storageAliases;

    public PipelineConfigurationPostProcessor(
            final @Value("${migration.alias.file:}") String aliasFile) {
        if (StringUtils.isBlank(aliasFile) || !Files.exists(Paths.get(aliasFile))) {
            dockerRegistryAliases = Collections.emptyMap();
            storageAliases = Collections.emptyMap();
        } else {
            final Map<String, Map<String, String>> data = readFile(aliasFile);
            dockerRegistryAliases = MapUtils.emptyIfNull(data)
                    .getOrDefault("dockerRegistry", Collections.emptyMap());
            storageAliases = MapUtils.emptyIfNull(data)
                    .getOrDefault("storage", Collections.emptyMap());
        }
        log.debug("PipelineConfigurationPostProcessor initialized with {} docker registries and {} storages.",
                dockerRegistryAliases.size(), storageAliases.size());
    }

    public void postProcessPipelineConfig(final PipelineConfiguration configuration) {
        processDockerImage(configuration);
        processParameters(configuration);
    }

    private void processParameters(final PipelineConfiguration configuration) {
        final Map<String, PipeConfValueVO> parameters = configuration.getParameters();
        if (MapUtils.isEmpty(parameters) || MapUtils.isEmpty(storageAliases)) {
            return;
        }
        parameters.values()
                .stream()
                .filter(value -> value != null
                        && StringUtils.isNotBlank(value.getValue())
                        && value.getValue().startsWith("s3://"))
                .forEach(this::processValue);
    }

    private void processValue(final PipeConfValueVO value) {
        final String initialValue = value.getValue();
        storageAliases
                .entrySet()
                .stream()
                .filter(entry -> initialValue.equals(entry.getKey()) ||
                        initialValue.startsWith(ProviderUtils.withTrailingDelimiter(entry.getKey())))
                .findFirst()
                .map(entry -> initialValue.replace(entry.getKey(), entry.getValue()))
                .ifPresent(newValue -> {
                    value.setValue(newValue);
                    log.debug("Replacing value '{}' with '{}'", initialValue, newValue);
                });
    }

    private void processDockerImage(final PipelineConfiguration configuration) {
        final String dockerImage = configuration.getDockerImage();
        if (StringUtils.isBlank(dockerImage) || MapUtils.isEmpty(dockerRegistryAliases)) {
            return;
        }
        dockerRegistryAliases.entrySet()
                .stream()
                .filter(entry -> dockerImage.startsWith(entry.getKey()))
                .findFirst()
                .map(entry -> dockerImage.replace(entry.getKey(), entry.getValue()))
                .ifPresent(newImage -> {
                    configuration.setDockerImage(newImage);
                    log.debug("Replacing image '{}' with '{}'.", dockerImage, newImage);
                });
    }

    private Map<String, Map<String, String>> readFile(final String aliasFile) {
        try {
            final String content = FileUtils.readFileToString(new File(aliasFile));
            return JsonMapper.parseData(content,
                    new TypeReference<Map<String, Map<String, String>>>() {});
        } catch (IllegalArgumentException | IOException e) {
            log.error(e.getMessage(), e);
            return Collections.emptyMap();
        }
    }
}
