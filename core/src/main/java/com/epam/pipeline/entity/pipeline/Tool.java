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

import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class Tool extends AbstractSecuredEntity {

    private Long id;
    private String image;
    private String cpu;
    private String ram;
    private String instanceType;
    private Integer disk;

    private Long registryId;
    private String registry;
    private Long toolGroupId;
    private String toolGroup;
    private Long link;

    @JsonIgnore
    private String secretName;
    private String description;
    private String shortDescription;
    private List<String> labels;
    private List<String> endpoints;
    private String defaultCommand;
    private boolean hasIcon;
    private Long iconId;

    public void setIconId(Long iconId) {
        this.iconId = iconId;
        hasIcon = iconId != null;
    }

    private ToolGroup parent;
    private AclClass aclClass = AclClass.TOOL;

    public Tool(String image) {
        this.image = image;
    }

    public ToolGroup getParent() {
        if (parent != null) {
            return parent;
        }
        return toolGroupId == null ? null : new ToolGroup(toolGroupId);
    }

    @JsonIgnore
    public boolean isSymlink() {
        return link != null;
    }
}
