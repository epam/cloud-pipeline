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

package com.epam.pipeline.manager.metadata.processor;

import com.epam.pipeline.entity.metadata.PipeConfValue;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Provides common interface for post processing of metadata values,
 * e.g. name normalization for bucket names
 */
@Service
public class MetadataPostProcessorService {

    private Map<String, MetadataPostProcessor> postProcessors;

    public MetadataPostProcessorService(List<MetadataPostProcessor> availableProcessors) {
        if (CollectionUtils.isEmpty(availableProcessors)) {
            this.postProcessors = new HashMap<>();
        } else {
            this.postProcessors = availableProcessors.stream().collect(
                    Collectors.toMap(MetadataPostProcessor::supportedType, Function.identity()));
        }
    }

    public void postProcessParameter(String name, PipeConfValue parameter) {
        if (parameter == null || !StringUtils.hasText(parameter.getType())) {
            return;
        }
        MetadataPostProcessor metadataPostProcessor = postProcessors.get(parameter.getType());
        if (metadataPostProcessor != null) {
            metadataPostProcessor.process(name, parameter);
        }
    }
}
