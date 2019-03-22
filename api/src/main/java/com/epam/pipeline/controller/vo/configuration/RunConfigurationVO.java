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

package com.epam.pipeline.controller.vo.configuration;

import java.util.Collections;
import java.util.List;

import com.epam.pipeline.entity.configuration.AbstractRunConfigurationEntry;
import com.epam.pipeline.entity.configuration.RunConfiguration;
import com.epam.pipeline.entity.pipeline.Folder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.collections4.CollectionUtils;

@Getter
@Setter
@NoArgsConstructor
public class RunConfigurationVO {
    private Long parentId;
    private Long id;
    private String name;
    private String description;
    private List<AbstractRunConfigurationEntry> entries;

    public RunConfiguration toEntity() {
        RunConfiguration configuration = new RunConfiguration();
        configuration.setId(getId());
        configuration.setName(getName());
        configuration.setDescription(getDescription());
        if (parentId != null) {
            configuration.setParent(new Folder(getParentId()));
        }
        if (CollectionUtils.isNotEmpty(entries)) {
            configuration.setEntries(entries);
        } else {
            configuration.setEntries(Collections.emptyList());
        }
        return configuration;
    }
}
