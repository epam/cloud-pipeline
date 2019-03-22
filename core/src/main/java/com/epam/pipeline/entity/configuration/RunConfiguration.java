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

package com.epam.pipeline.entity.configuration;

import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

import static com.epam.pipeline.entity.configuration.RunConfigurationUtils.getNodeCount;
import static com.epam.pipeline.entity.security.acl.AclClass.CONFIGURATION;

/**
 * Represents configuration for running analysis in  {@link ExecutionEnvironment#CLOUD_PLATFORM}.
 */
@Getter
@Setter
@NoArgsConstructor
public class RunConfiguration extends AbstractSecuredEntity {

    private String description;
    private Folder parent;
    private List<AbstractRunConfigurationEntry> entries = new ArrayList<>();

    @Override
    public Folder getParent() {
        return parent;
    }

    @Override
    public AclClass getAclClass() {
        return CONFIGURATION;
    }

    @JsonIgnore
    public int getTotalNodeCount() {
        return getEntries().stream()
                .map(AbstractRunConfigurationEntry::getWorkerCount)
                .mapToInt(nodeCount -> getNodeCount(nodeCount, 1))
                .sum();
    }
}
