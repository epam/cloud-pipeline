/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.entity.docker;

import com.epam.pipeline.entity.AbstractHierarchicalEntity;
import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.pipeline.DockerRegistry;
import com.epam.pipeline.entity.security.acl.AclClass;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@link DockerRegistryList} represents a pseudo root entity for returning list of
 * {@link DockerRegistry} entities in one tree structure
 */
@Getter
@Setter
public class DockerRegistryList extends AbstractHierarchicalEntity {
    private List<DockerRegistry> registries;

    public DockerRegistryList(List<DockerRegistry> registries) {
        super();
        this.registries = new ArrayList<>(registries);
    }

    @Override
    public List<AbstractSecuredEntity> getLeaves() {
        return Collections.emptyList();
    }

    @Override
    public List<AbstractHierarchicalEntity> getChildren() {
        return new ArrayList<>(registries);
    }

    @Override
    public void filterLeaves(Map<AclClass, Set<Long>> idToRemove) {
        //no op
    }

    @Override
    public void filterChildren(Map<AclClass, Set<Long>> idToRemove) {
        registries = filterCollection(registries, AclClass.DOCKER_REGISTRY, idToRemove);
    }

    @Override
    public AbstractSecuredEntity getParent() {
        return null;
    }

    @Override
    public AclClass getAclClass() {
        return AclClass.DOCKER_REGISTRY;
    }

    @SuppressWarnings("unchecked")
    @Override
    public DockerRegistryList copyView() {
       return new DockerRegistryList(
                this.getRegistries().stream().map(DockerRegistry::copyView).collect(Collectors.toList())
        );
    }
}
