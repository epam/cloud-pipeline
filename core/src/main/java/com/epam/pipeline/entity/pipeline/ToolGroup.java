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

package com.epam.pipeline.entity.pipeline;

import com.epam.pipeline.entity.AbstractHierarchicalEntity;
import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.collections4.ListUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Represents a group of tools. This entity is mainly used to simplify process of rights management on set of tools.
 * A ToolGroup can belong to only one registry and can contain multiple tools.
 */
@Getter
@Setter
@NoArgsConstructor
public class ToolGroup extends AbstractHierarchicalEntity {
    private Long id;
    private Long registryId;
    private String name;
    private String description;

    private List<Tool> tools;

    /**
     * A flag, that determines if a ToolGroup belongs to the currently logged it user. Should be derived
     * from SecurityContext, not from the database
     */
    private boolean privateGroup;

    private final AclClass aclClass = AclClass.TOOL_GROUP;

    @JsonIgnore
    private DockerRegistry parent;

    public ToolGroup(Long id) {
        super(id);
    }

    @Override
    @JsonIgnore
    public List<? extends AbstractSecuredEntity> getLeaves() {
        return new ArrayList<>(ListUtils.emptyIfNull(tools));
    }

    @Override
    @JsonIgnore
    public List<? extends AbstractHierarchicalEntity> getChildren() {
        return Collections.emptyList();
    }

    @Override
    public void filterLeaves(Map<AclClass, Set<Long>> idToRemove) {
        tools = filterCollection(tools, AclClass.TOOL, idToRemove);
    }

    @Override
    public void filterChildren(Map<AclClass, Set<Long>> idToRemove) {
        // no leaves, so no op
    }

    public DockerRegistry getParent() {
        if (parent != null) {
            return parent;
        }
        return registryId == null ? null : new DockerRegistry(registryId);
    }

    @Override
    public AbstractHierarchicalEntity copyView() {
        final ToolGroup result = new ToolGroup(this.getId());
        result.setRegistryId(this.getRegistryId());
        result.setName(this.getName());
        result.setDescription(this.getDescription());
        result.setPrivateGroup(this.isPrivateGroup());
        result.setParent(this.getParent());
        result.setOwner(this.getOwner());
        return result;
    }
}
