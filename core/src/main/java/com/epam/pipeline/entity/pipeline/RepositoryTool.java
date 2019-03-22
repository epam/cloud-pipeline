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

@Getter
@Setter
@NoArgsConstructor
public class RepositoryTool extends AbstractSecuredEntity {
    private Tool tool;
    private String image;
    private Boolean registered;
    private Boolean invalid;

    public RepositoryTool(String image, Tool tool) {
        this.image = image;
        this.tool = tool;
        this.registered = true;
        this.invalid = false;
    }

    public RepositoryTool(Tool tool) {
        this.image = tool.getImage();
        this.tool = tool;
        this.registered = true;
        this.invalid = true;
    }

    public RepositoryTool(String image) {
        this.image = image;
        this.registered = false;
        this.invalid = false;
    }

    @Override
    @JsonIgnore
    public Long getId() {
        return tool.getId();
    }

    @Override
    @JsonIgnore
    public AbstractSecuredEntity getParent() {
        return tool.getParent();
    }

    @Override
    @JsonIgnore
    public AclClass getAclClass() {
        return AclClass.TOOL;
    }
}
