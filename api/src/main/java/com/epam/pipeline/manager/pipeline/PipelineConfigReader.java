/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.pipeline;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import com.epam.pipeline.entity.configuration.ConfigurationEntry;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.exception.ConfigurationReadingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PipelineConfigReader {

    private static final Logger LOGGER = LoggerFactory.getLogger(PipelineConfigReader.class);

    public ConfigurationEntry readConfiguration(String config, ObjectMapper mapper) {
        try {
            PipelineConfiguration configuration =
                    mapper.readValue(config, PipelineConfiguration.class);
            return new ConfigurationEntry(configuration);
        } catch (IOException e) {
            LOGGER.debug(e.getMessage(), e);
            throw new ConfigurationReadingException(config, e);
        }
    }

    public List<ConfigurationEntry> readConfigurations(String config, ObjectMapper mapper) {
        try {
            return mapper.readValue(config, new TypeReference<List<ConfigurationEntry>>() { });
        } catch (JsonMappingException e) {
            // support for older single config jsons
            return Collections.singletonList(readConfiguration(config, mapper));
        } catch (IOException e) {
            LOGGER.debug(e.getMessage(), e);
            throw new ConfigurationReadingException(config, e);
        }
    }
}
