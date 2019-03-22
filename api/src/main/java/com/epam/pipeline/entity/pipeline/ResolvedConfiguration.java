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

package com.epam.pipeline.entity.pipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.metadata.MetadataEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.collections4.MapUtils;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ResolvedConfiguration {

    private MetadataEntity entity;
    private Map<String, PipelineConfiguration> configurations;
    private List<Long> associatedEntityIds = new ArrayList<>();

    public ResolvedConfiguration(MetadataEntity entity,
            Map<String, PipelineConfiguration> configurations) {
        this.entity = entity;
        this.configurations = configurations;
    }

    public Long getEntityId() {
        return entity == null ? null : entity.getId();
    }

    public PipelineConfiguration getConfiguration(String name) {
        return MapUtils.isEmpty(configurations) ? null : configurations.get(name);
    }

    public List<Long> getAllAssociatedIds() {
        List<Long> result = new ArrayList<>();
        if (getEntityId() != null) {
            result.add(getEntityId());
        }
        result.addAll(associatedEntityIds);
        return result;
    }
}
